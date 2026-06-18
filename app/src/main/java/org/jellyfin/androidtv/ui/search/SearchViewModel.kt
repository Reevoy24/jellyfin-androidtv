package org.jellyfin.androidtv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.jellyseerr.JellyseerrRepository
import org.jellyfin.androidtv.ui.jellyseerr.JellyseerrSearchResult
import org.jellyfin.sdk.model.api.BaseItemKind
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class SearchViewModel(
	private val searchRepository: SearchRepository,
	private val jellyseerrRepository: JellyseerrRepository,
) : ViewModel() {
	companion object {
		private val debounceDuration = 600.milliseconds

		private val groups = mapOf(
			R.string.lbl_movies to setOf(BaseItemKind.MOVIE),
			R.string.lbl_series to setOf(BaseItemKind.SERIES),
			R.string.lbl_episodes to setOf(BaseItemKind.EPISODE),
			R.string.lbl_videos to setOf(BaseItemKind.VIDEO),
			R.string.lbl_programs to setOf(BaseItemKind.LIVE_TV_PROGRAM),
			R.string.channels to setOf(BaseItemKind.LIVE_TV_CHANNEL),
			R.string.lbl_playlists to setOf(BaseItemKind.PLAYLIST),
			R.string.lbl_artists to setOf(BaseItemKind.MUSIC_ARTIST),
			R.string.lbl_albums to setOf(BaseItemKind.MUSIC_ALBUM),
			R.string.lbl_songs to setOf(BaseItemKind.AUDIO),
			R.string.photo_albums to setOf(BaseItemKind.PHOTO_ALBUM),
			R.string.photos to setOf(BaseItemKind.PHOTO),
			R.string.lbl_collections to setOf(BaseItemKind.BOX_SET),
			R.string.lbl_people to setOf(BaseItemKind.PERSON),
		)
	}

	private var searchJob: Job? = null
	private var jellyseerrJob: Job? = null

	private var previousQuery: String? = null

	// Cached availability of the Jellyseerr integration for this session (null = not checked yet).
	private var jellyseerrAvailable: Boolean? = null

	private val _searchResultsFlow = MutableStateFlow<Collection<SearchResultGroup>>(emptyList())
	val searchResultsFlow = _searchResultsFlow.asStateFlow()

	private val _jellyseerrResultsFlow = MutableStateFlow<List<JellyseerrSearchResult>>(emptyList())
	val jellyseerrResultsFlow = _jellyseerrResultsFlow.asStateFlow()

	/** Submit (keyboard "search"): run the library search now AND query Jellyseerr. */
	fun searchImmediately(query: String) {
		val trimmed = query.trim()
		runLibrarySearch(trimmed, 0.milliseconds)
		runJellyseerrSearch(trimmed)
	}

	/**
	 * Typing: run the (debounced) library search only. Jellyseerr is an expensive external call and
	 * typing on a TV remote is slow (every keystroke would exceed the debounce), so it is queried
	 * only on submit. Stale Jellyseerr results are cleared while the query keeps changing.
	 */
	fun searchDebounced(query: String) {
		val trimmed = query.trim()
		runLibrarySearch(trimmed, debounceDuration)

		if (_jellyseerrResultsFlow.value.isNotEmpty()) {
			jellyseerrJob?.cancel()
			_jellyseerrResultsFlow.value = emptyList()
		}
	}

	private fun runLibrarySearch(trimmed: String, debounce: Duration) {
		if (trimmed == previousQuery) return
		previousQuery = trimmed

		searchJob?.cancel()

		if (trimmed.isBlank()) {
			_searchResultsFlow.value = emptyList()
			return
		}

		searchJob = viewModelScope.launch {
			delay(debounce)

			_searchResultsFlow.value = groups.map { (stringRes, itemKinds) ->
				async {
					val result = searchRepository.search(trimmed, itemKinds)
					SearchResultGroup(stringRes, result.getOrNull().orEmpty())
				}
			}.awaitAll()
		}
	}

	private fun runJellyseerrSearch(trimmed: String) {
		jellyseerrJob?.cancel()

		if (trimmed.isBlank()) {
			_jellyseerrResultsFlow.value = emptyList()
			return
		}

		jellyseerrJob = viewModelScope.launch {
			_jellyseerrResultsFlow.value = searchJellyseerr(trimmed)
		}
	}

	private suspend fun searchJellyseerr(query: String): List<JellyseerrSearchResult> {
		if (jellyseerrAvailable == null) {
			jellyseerrAvailable = jellyseerrRepository.getUserStatus()?.available == true
		}
		if (jellyseerrAvailable != true) return emptyList()

		return jellyseerrRepository.search(query)
	}
}
