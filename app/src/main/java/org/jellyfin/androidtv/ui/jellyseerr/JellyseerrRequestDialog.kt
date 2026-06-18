package org.jellyfin.androidtv.ui.jellyseerr

import android.widget.ImageView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.dialog.DialogBase
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.koin.compose.koinInject

private enum class PickerField { SERVER, PROFILE, FOLDER }

private enum class RequestKind { MOVIE, SERIES_ALL, SERIES_SELECTED }

private data class PrimaryAction(val kind: RequestKind, val labelRes: Int, val enabled: Boolean)

/**
 * Detail + request dialog for a Jellyseerr search result. Movies request directly; series load
 * their season list and (when the server allows partial requests) let the user pick seasons.
 * When Radarr/Sonarr instances are exposed, an advanced-options section lets the user pick the
 * target server, quality profile and root folder.
 */
@Composable
fun JellyseerrRequestDialog(
	result: JellyseerrSearchResult,
	onDismiss: () -> Unit,
) {
	val repository = koinInject<JellyseerrRepository>()
	val scope = rememberCoroutineScope()
	val context = LocalContext.current

	var loading by remember(result.id) { mutableStateOf(result.isTv) }
	var tvDetails by remember(result.id) { mutableStateOf<JellyseerrTvDetails?>(null) }
	var partialRequests by remember(result.id) { mutableStateOf(false) }
	var includeSpecials by remember(result.id) { mutableStateOf(false) }
	var submitting by remember(result.id) { mutableStateOf(false) }
	val selectedSeasons = remember(result.id) { mutableStateListOf<Int>() }

	// Advanced options
	var servers by remember(result.id) { mutableStateOf<List<JellyseerrServer>>(emptyList()) }
	var selectedServer by remember(result.id) { mutableStateOf<JellyseerrServer?>(null) }
	var serverDetails by remember(result.id) { mutableStateOf<JellyseerrServerDetails?>(null) }
	var selectedProfileId by remember(result.id) { mutableStateOf<Int?>(null) }
	var selectedFolder by remember(result.id) { mutableStateOf<String?>(null) }
	var activePicker by remember(result.id) { mutableStateOf<PickerField?>(null) }

	suspend fun applyServer(server: JellyseerrServer) {
		selectedServer = server
		serverDetails = null
		selectedProfileId = null
		selectedFolder = null
		val details = repository.getServerDetails(result.isMovie, server.id)
		serverDetails = details
		selectedProfileId = server.activeProfileId ?: details?.profiles?.firstOrNull()?.id
		selectedFolder = server.activeDirectory ?: details?.rootFolders?.firstOrNull()?.path
	}

	LaunchedEffect(result.id) {
		if (result.isTv) {
			val settings = repository.getRequestSettings()
			partialRequests = settings.partialRequestsEnabled
			includeSpecials = settings.enableSpecialEpisodes
			tvDetails = repository.getTvDetails(result.id)
			loading = false

			// Pre-select all requestable seasons so the request button is usable immediately.
			if (partialRequests) {
				tvDetails?.let { details ->
					val requestable = details.seasons
						.filter { !it.isSpecial || includeSpecials }
						.filter { details.statusForSeason(it.seasonNumber) == JellyseerrMediaStatus.UNKNOWN }
						.map { it.seasonNumber }
					selectedSeasons.clear()
					selectedSeasons.addAll(requestable)
				}
			}
		}

		val list = repository.getServers(result.isMovie)
		servers = list
		(list.firstOrNull { it.isDefault } ?: list.firstOrNull())?.let { applyServer(it) }
	}

	val advancedOptions = selectedServer?.let {
		JellyseerrAdvancedOptions(serverId = it.id, profileId = selectedProfileId, rootFolder = selectedFolder)
	}

	fun submit(seasons: List<Int>?) {
		if (submitting) return
		submitting = true
		scope.launch {
			val outcome = if (result.isMovie) {
				repository.requestMovie(result.id, advancedOptions)
			} else {
				repository.requestSeries(result.id, seasons, advancedOptions)
			}
			submitting = false
			val message = if (outcome.isSuccess) {
				context.getString(R.string.jellyseerr_request_success, result.displayTitle)
			} else {
				context.getString(R.string.jellyseerr_request_failed, result.displayTitle)
			}
			Toast.makeText(context, message, Toast.LENGTH_LONG).show()
			if (outcome.isSuccess) onDismiss()
		}
	}

	// Seasons the user may still request (skip specials unless enabled, and skip ones already there).
	val requestableSeasons = tvDetails?.seasons.orEmpty()
		.filter { !it.isSpecial || includeSpecials }
		.filter { tvDetails?.statusForSeason(it.seasonNumber) == JellyseerrMediaStatus.UNKNOWN }
		.map { it.seasonNumber }

	DialogBase(
		visible = true,
		onDismissRequest = {
			when {
				submitting -> Unit
				activePicker != null -> activePicker = null
				else -> onDismiss()
			}
		},
	) {
		Column(
			modifier = Modifier
				.width(620.dp)
				.clip(RoundedCornerShape(12.dp))
				.background(JellyfinTheme.colorScheme.background)
				.padding(28.dp),
		) {
			when (val picker = activePicker) {
				null -> RequestForm(
					result = result,
					tvDetails = tvDetails,
					loading = loading,
					partialRequests = partialRequests,
					includeSpecials = includeSpecials,
					selectedSeasons = selectedSeasons,
					requestableSeasons = requestableSeasons,
					submitting = submitting,
					servers = servers,
					selectedServer = selectedServer,
					serverDetails = serverDetails,
					selectedProfileId = selectedProfileId,
					selectedFolder = selectedFolder,
					onOpenPicker = { activePicker = it },
					onClose = { if (!submitting) onDismiss() },
					onSubmit = ::submit,
				)

				else -> PickerScreen(
					field = picker,
					servers = servers,
					serverDetails = serverDetails,
					selectedServer = selectedServer,
					selectedProfileId = selectedProfileId,
					selectedFolder = selectedFolder,
					onPickServer = { server -> scope.launch { applyServer(server) }; activePicker = null },
					onPickProfile = { id -> selectedProfileId = id; activePicker = null },
					onPickFolder = { path -> selectedFolder = path; activePicker = null },
				)
			}
		}
	}
}

@Composable
private fun RequestForm(
	result: JellyseerrSearchResult,
	tvDetails: JellyseerrTvDetails?,
	loading: Boolean,
	partialRequests: Boolean,
	includeSpecials: Boolean,
	selectedSeasons: MutableList<Int>,
	requestableSeasons: List<Int>,
	submitting: Boolean,
	servers: List<JellyseerrServer>,
	selectedServer: JellyseerrServer?,
	serverDetails: JellyseerrServerDetails?,
	selectedProfileId: Int?,
	selectedFolder: String?,
	onOpenPicker: (PickerField) -> Unit,
	onClose: () -> Unit,
	onSubmit: (List<Int>?) -> Unit,
) {
	// Header: poster + title/year/overview
	Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
		val posterUrl = (tvDetails?.posterPath?.let { JELLYSEERR_TMDB_IMAGE_BASE + it }) ?: result.posterUrl
		if (posterUrl != null) {
			AsyncImage(
				url = posterUrl,
				modifier = Modifier
					.width(100.dp)
					.height(150.dp)
					.clip(RoundedCornerShape(8.dp)),
				scaleType = ImageView.ScaleType.CENTER_CROP,
			)
		}

		Column(modifier = Modifier.weight(1f)) {
			Text(
				text = result.displayTitle,
				color = JellyfinTheme.colorScheme.onBackground,
				fontSize = 22.sp,
				fontWeight = FontWeight.Bold,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis,
			)
			result.year?.let { year ->
				Text(
					text = year,
					color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.6f),
					fontSize = 14.sp,
					modifier = Modifier.padding(top = 2.dp),
				)
			}
			val overview = tvDetails?.overview ?: result.overview
			if (!overview.isNullOrBlank()) {
				Text(
					text = overview,
					color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.8f),
					fontSize = 13.sp,
					maxLines = 4,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.padding(top = 10.dp),
				)
			}
		}
	}

	Spacer(modifier = Modifier.height(16.dp))

	// Scrollable body: advanced options + season selection / status
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.heightIn(max = 280.dp)
			.verticalScroll(rememberScrollState()),
	) {
		if (servers.isNotEmpty()) {
			SectionLabel(stringResource(R.string.jellyseerr_dialog_advanced))
			OptionRow(
				label = stringResource(R.string.jellyseerr_dialog_server),
				value = selectedServer?.displayName ?: stringResource(R.string.jellyseerr_dialog_loading),
				enabled = !submitting,
				onClick = { onOpenPicker(PickerField.SERVER) },
			)
			OptionRow(
				label = stringResource(R.string.jellyseerr_dialog_quality),
				value = serverDetails?.profiles?.firstOrNull { it.id == selectedProfileId }?.name
					?: stringResource(R.string.jellyseerr_dialog_select_quality),
				enabled = !submitting && serverDetails != null,
				onClick = { onOpenPicker(PickerField.PROFILE) },
			)
			OptionRow(
				label = stringResource(R.string.jellyseerr_dialog_folder),
				value = selectedFolder ?: stringResource(R.string.jellyseerr_dialog_select_folder),
				enabled = !submitting && serverDetails != null,
				onClick = { onOpenPicker(PickerField.FOLDER) },
			)
			Spacer(modifier = Modifier.height(8.dp))
		}

		when {
			result.isMovie -> StatusLine(result.status)

			loading -> HintText(stringResource(R.string.jellyseerr_dialog_loading))

			tvDetails == null -> HintText(stringResource(R.string.jellyseerr_dialog_no_seasons))

			partialRequests -> SeasonSelection(
				details = tvDetails,
				includeSpecials = includeSpecials,
				selected = selectedSeasons,
				requestable = requestableSeasons,
				enabled = !submitting,
			)

			else -> HintText(stringResource(R.string.jellyseerr_dialog_request_series_hint))
		}
	}

	Spacer(modifier = Modifier.height(20.dp))

	// Footer actions
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
	) {
		Button(onClick = onClose, enabled = !submitting) {
			Text(stringResource(R.string.jellyseerr_dialog_close))
		}

		val primary = primaryAction(result, loading, tvDetails, partialRequests, requestableSeasons, selectedSeasons)
		if (primary != null) {
			Button(
				onClick = {
					when (primary.kind) {
						RequestKind.MOVIE, RequestKind.SERIES_ALL -> onSubmit(null)
						RequestKind.SERIES_SELECTED -> onSubmit(selectedSeasons.toList())
					}
				},
				enabled = primary.enabled && !submitting,
			) {
				Text(
					if (submitting) stringResource(R.string.jellyseerr_dialog_requesting)
					else stringResource(primary.labelRes),
				)
			}
		}
	}
}

@Composable
private fun PickerScreen(
	field: PickerField,
	servers: List<JellyseerrServer>,
	serverDetails: JellyseerrServerDetails?,
	selectedServer: JellyseerrServer?,
	selectedProfileId: Int?,
	selectedFolder: String?,
	onPickServer: (JellyseerrServer) -> Unit,
	onPickProfile: (Int) -> Unit,
	onPickFolder: (String) -> Unit,
) {
	val titleRes = when (field) {
		PickerField.SERVER -> R.string.jellyseerr_dialog_server
		PickerField.PROFILE -> R.string.jellyseerr_dialog_quality
		PickerField.FOLDER -> R.string.jellyseerr_dialog_folder
	}

	// (label, selected, onPick) options for the active field
	data class Option(val label: String, val selected: Boolean, val onPick: () -> Unit)

	val options: List<Option> = when (field) {
		PickerField.SERVER -> servers.map { server ->
			Option(server.displayName, server.id == selectedServer?.id) { onPickServer(server) }
		}

		PickerField.PROFILE -> serverDetails?.profiles.orEmpty().map { profile ->
			Option(profile.name, profile.id == selectedProfileId) { onPickProfile(profile.id) }
		}

		PickerField.FOLDER -> serverDetails?.rootFolders.orEmpty().map { folder ->
			Option(folder.path, folder.path == selectedFolder) { onPickFolder(folder.path) }
		}
	}

	val firstFocus = remember { FocusRequester() }

	Text(
		text = stringResource(titleRes),
		color = JellyfinTheme.colorScheme.onBackground,
		fontSize = 18.sp,
		fontWeight = FontWeight.Bold,
		modifier = Modifier.padding(bottom = 12.dp),
	)

	if (options.isEmpty()) {
		HintText(stringResource(R.string.jellyseerr_dialog_loading))
		return
	}

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.heightIn(max = 320.dp)
			.verticalScroll(rememberScrollState()),
	) {
		options.forEachIndexed { index, option ->
			ChoiceRow(
				label = option.label,
				selected = option.selected,
				onClick = option.onPick,
				modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
			)
		}
	}

	LaunchedEffect(field) { runCatching { firstFocus.requestFocus() } }
}

@Composable
private fun SectionLabel(text: String) {
	Text(
		text = text,
		color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.7f),
		fontSize = 12.sp,
		fontWeight = FontWeight.Bold,
		modifier = Modifier.padding(bottom = 6.dp),
	)
}

@Composable
private fun HintText(text: String) {
	Text(
		text = text,
		color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.7f),
		fontSize = 14.sp,
	)
}

@Composable
private fun StatusLine(status: JellyseerrMediaStatus) {
	HintText(
		when (status) {
			JellyseerrMediaStatus.AVAILABLE -> stringResource(R.string.jellyseerr_dialog_already_available)
			JellyseerrMediaStatus.PENDING -> stringResource(R.string.jellyseerr_dialog_already_requested)
			JellyseerrMediaStatus.UNKNOWN -> stringResource(R.string.jellyseerr_dialog_request_movie_hint)
		}
	)
}

/** A form row that shows a label + the current value and opens a picker when clicked. */
@Composable
private fun OptionRow(
	label: String,
	value: String,
	enabled: Boolean,
	onClick: () -> Unit,
) {
	var focused by remember { mutableStateOf(false) }
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(6.dp))
			.onFocusChanged { focused = it.isFocused }
			.background(if (focused) JellyfinTheme.colorScheme.listButtonFocused else Color.Transparent)
			.clickable(enabled = enabled, onClick = onClick)
			.padding(horizontal = 12.dp, vertical = 10.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp),
	) {
		Text(
			text = label,
			color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.7f),
			fontSize = 13.sp,
			modifier = Modifier.width(150.dp),
		)
		Text(
			text = value,
			color = JellyfinTheme.colorScheme.onBackground,
			fontSize = 14.sp,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.weight(1f),
		)
		Text(text = "›", color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 16.sp)
	}
}

/** A single-choice row used in the picker screens. */
@Composable
private fun ChoiceRow(
	label: String,
	selected: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	var focused by remember { mutableStateOf(false) }
	Row(
		modifier = modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(6.dp))
			.onFocusChanged { focused = it.isFocused }
			.background(if (focused) JellyfinTheme.colorScheme.listButtonFocused else Color.Transparent)
			.clickable(onClick = onClick)
			.padding(horizontal = 12.dp, vertical = 12.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp),
	) {
		Text(
			text = if (selected) "●" else "○",
			color = JellyfinTheme.colorScheme.onBackground.copy(alpha = if (selected) 1f else 0.5f),
			fontSize = 12.sp,
		)
		Text(
			text = label,
			color = JellyfinTheme.colorScheme.onBackground,
			fontSize = 14.sp,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)
	}
}

@Composable
private fun SeasonSelection(
	details: JellyseerrTvDetails,
	includeSpecials: Boolean,
	selected: MutableList<Int>,
	requestable: List<Int>,
	enabled: Boolean,
) {
	val seasons = details.seasons
		.filter { !it.isSpecial || includeSpecials }
		.sortedBy { it.seasonNumber }

	// Select-all toggle
	val allSelected = requestable.isNotEmpty() && selected.containsAll(requestable)
	SelectableRow(
		checked = allSelected,
		enabled = enabled && requestable.isNotEmpty(),
		title = stringResource(R.string.jellyseerr_dialog_select_all),
		subtitle = null,
		trailing = null,
		onToggle = {
			if (allSelected) {
				selected.clear()
			} else {
				selected.clear()
				selected.addAll(requestable)
			}
		},
	)

	for (season in seasons) {
		val status = details.statusForSeason(season.seasonNumber)
		val isRequestable = season.seasonNumber in requestable
		val title = season.name?.takeIf { it.isNotBlank() }
			?: stringResource(R.string.jellyseerr_dialog_season, season.seasonNumber)
		SelectableRow(
			checked = season.seasonNumber in selected,
			enabled = enabled && isRequestable,
			title = title,
			subtitle = stringResource(R.string.jellyseerr_dialog_episodes, season.episodeCount),
			trailing = when (status) {
				JellyseerrMediaStatus.AVAILABLE -> stringResource(R.string.jellyseerr_status_available)
				JellyseerrMediaStatus.PENDING -> stringResource(R.string.jellyseerr_status_requested)
				JellyseerrMediaStatus.UNKNOWN -> null
			},
			onToggle = {
				if (season.seasonNumber in selected) selected.remove(season.seasonNumber)
				else selected.add(season.seasonNumber)
			},
		)
	}
}

@Composable
private fun SelectableRow(
	checked: Boolean,
	enabled: Boolean,
	title: String,
	subtitle: String?,
	trailing: String?,
	onToggle: () -> Unit,
) {
	var focused by remember { mutableStateOf(false) }
	val alpha = if (enabled) 1f else 0.4f

	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(6.dp))
			.onFocusChanged { focused = it.isFocused }
			.background(if (focused) JellyfinTheme.colorScheme.listButtonFocused else Color.Transparent)
			.clickable(enabled = enabled, onClick = onToggle)
			.padding(horizontal = 12.dp, vertical = 10.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp),
	) {
		Box(
			modifier = Modifier
				.size(20.dp)
				.clip(RoundedCornerShape(4.dp))
				.border(
					width = 2.dp,
					color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.5f * alpha),
					shape = RoundedCornerShape(4.dp),
				)
				.background(if (checked) JellyfinTheme.colorScheme.buttonFocused.copy(alpha = alpha) else Color.Transparent),
			contentAlignment = Alignment.Center,
		) {
			if (checked) {
				Text(text = "✓", color = JellyfinTheme.colorScheme.onButtonFocused, fontSize = 12.sp)
			}
		}

		Column(modifier = Modifier.weight(1f)) {
			Text(
				text = title,
				color = JellyfinTheme.colorScheme.onBackground.copy(alpha = alpha),
				fontSize = 14.sp,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			if (subtitle != null) {
				Text(
					text = subtitle,
					color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.6f * alpha),
					fontSize = 11.sp,
				)
			}
		}

		if (trailing != null) {
			Text(
				text = trailing,
				color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.7f),
				fontSize = 11.sp,
			)
		}
	}
}

/** Computes the primary (request) button for the current state, or null when nothing is requestable. */
private fun primaryAction(
	result: JellyseerrSearchResult,
	loading: Boolean,
	tvDetails: JellyseerrTvDetails?,
	partialRequests: Boolean,
	requestableSeasons: List<Int>,
	selectedSeasons: List<Int>,
): PrimaryAction? = when {
	result.isMovie -> when (result.status) {
		JellyseerrMediaStatus.UNKNOWN -> PrimaryAction(RequestKind.MOVIE, R.string.jellyseerr_dialog_request, enabled = true)
		else -> null
	}

	loading -> null

	// Details failed to load — still allow requesting the whole series so the button works.
	tvDetails == null -> PrimaryAction(RequestKind.SERIES_ALL, R.string.jellyseerr_dialog_request_series, enabled = true)

	partialRequests -> when {
		requestableSeasons.isEmpty() -> null
		else -> PrimaryAction(
			RequestKind.SERIES_SELECTED,
			R.string.jellyseerr_dialog_request_selected,
			enabled = selectedSeasons.isNotEmpty(),
		)
	}

	else -> PrimaryAction(RequestKind.SERIES_ALL, R.string.jellyseerr_dialog_request_series, enabled = true)
}
