package org.jellyfin.androidtv.ui.calendar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpMethod
import timber.log.Timber

/**
 * Loads upcoming releases from the Jellyfin-Enhanced plugin's arr calendar endpoint, using the
 * authenticated [ApiClient] (same plugin and auth as the Jellyseerr integration).
 */
interface CalendarRepository {
	/** Upcoming releases for the default window (today .. +90 days). Empty on failure. */
	suspend fun getUpcoming(): List<CalendarItem>
}

class CalendarRepositoryImpl(
	private val apiClient: ApiClient,
) : CalendarRepository {
	private val json = Json {
		ignoreUnknownKeys = true
		isLenient = true
		coerceInputValues = true
	}

	override suspend fun getUpcoming(): List<CalendarItem> = withContext(Dispatchers.IO) {
		try {
			val response = apiClient.request(
				method = HttpMethod.GET,
				pathTemplate = "/JellyfinEnhanced/arr/calendar",
			)
			json.decodeFromString<List<CalendarItem>>(response.body.decodeToString())
		} catch (error: Exception) {
			Timber.w(error, "Jellyfin-Enhanced calendar fetch failed")
			emptyList()
		}
	}
}
