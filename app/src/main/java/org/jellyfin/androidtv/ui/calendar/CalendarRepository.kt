package org.jellyfin.androidtv.ui.calendar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpMethod
import timber.log.Timber
import java.time.Duration
import java.time.Instant

/**
 * Loads upcoming releases from the Jellyfin-Enhanced plugin's arr calendar endpoint, using the
 * authenticated [ApiClient] (same plugin and auth as the Jellyseerr integration).
 */
interface CalendarRepository {
	/** Releases for a window around now (−14 .. +180 days) covering the calendar views. Empty on failure. */
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
			val now = Instant.now()
			val start = now.minus(Duration.ofDays(14))
			val end = now.plus(Duration.ofDays(180))
			val response = apiClient.request(
				method = HttpMethod.GET,
				pathTemplate = "/JellyfinEnhanced/arr/calendar",
				queryParameters = mapOf("start" to start.toString(), "end" to end.toString()),
			)
			// The endpoint returns an envelope: { events: [...], errors: [...] }.
			json.decodeFromString<CalendarResponse>(response.body.decodeToString())
				.events
				.filter { it.releaseDate != null }
		} catch (error: Exception) {
			Timber.w(error, "Jellyfin-Enhanced calendar fetch failed")
			emptyList()
		}
	}
}
