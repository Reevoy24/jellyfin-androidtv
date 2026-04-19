package org.jellyfin.androidtv.ui.browsing

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import java.util.UUID
import java.util.function.Consumer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.model.api.ItemSortBy

fun loadGenreNames(
	lifecycle: Lifecycle,
	apiClient: ApiClient,
	parentId: UUID?,
	onLoaded: Consumer<List<String>>,
	onError: Consumer<Throwable>,
) {
	lifecycle.coroutineScope.launch {
		runCatching {
			withContext(Dispatchers.IO) {
				apiClient.genresApi.getGenres(
					parentId = parentId,
					sortBy = setOf(ItemSortBy.SORT_NAME),
				).content.items
					.mapNotNull { it.name?.takeIf(String::isNotBlank) }
					.distinct()
			}
		}.onSuccess(onLoaded::accept)
			.onFailure(onError::accept)
	}
}
