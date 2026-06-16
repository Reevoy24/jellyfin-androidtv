package org.jellyfin.androidtv.ui.jellyseerr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpMethod
import timber.log.Timber

/**
 * Talks to the Jellyseerr proxy endpoints of the Jellyfin-Enhanced server plugin. All calls reuse
 * the authenticated [ApiClient] (base url + access token), so requests are attributed to the
 * current Jellyfin user without any extra credentials.
 */
interface JellyseerrRepository {
	/** Returns the user/availability status, or `null` when the request failed entirely. */
	suspend fun getUserStatus(): JellyseerrUserStatus?

	/** Searches Jellyseerr for movies and series. Returns an empty list on failure. */
	suspend fun search(query: String): List<JellyseerrSearchResult>

	/** Requests a movie or whole series. */
	suspend fun request(result: JellyseerrSearchResult): Result<Unit>
}

class JellyseerrRepositoryImpl(
	private val apiClient: ApiClient,
) : JellyseerrRepository {
	companion object {
		private const val BASE = "/JellyfinEnhanced/jellyseerr"
	}

	// The proxied responses are standard Jellyseerr payloads with many more fields than we model.
	private val json = Json {
		ignoreUnknownKeys = true
		isLenient = true
		coerceInputValues = true
	}

	override suspend fun getUserStatus(): JellyseerrUserStatus? = withContext(Dispatchers.IO) {
		try {
			val response = apiClient.request(
				method = HttpMethod.GET,
				pathTemplate = "$BASE/user-status",
			)
			json.decodeFromString<JellyseerrUserStatus>(response.body.decodeToString())
		} catch (error: Exception) {
			Timber.d(error, "Jellyseerr user-status check failed")
			null
		}
	}

	override suspend fun search(query: String): List<JellyseerrSearchResult> = withContext(Dispatchers.IO) {
		try {
			val response = apiClient.request(
				method = HttpMethod.GET,
				pathTemplate = "$BASE/search",
				queryParameters = mapOf("query" to query, "page" to 1),
			)
			json.decodeFromString<JellyseerrSearchResponse>(response.body.decodeToString())
				.results
				// Only movies and series can be requested; the plugin also filters out "person".
				.filter { it.isMovie || it.isTv }
		} catch (error: Exception) {
			Timber.w(error, "Jellyseerr search failed for query \"%s\"", query)
			emptyList()
		}
	}

	override suspend fun request(result: JellyseerrSearchResult): Result<Unit> = withContext(Dispatchers.IO) {
		try {
			val body = JellyseerrRequestBody(
				mediaType = result.mediaType,
				mediaId = result.id,
				// Whole series; movies don't take a seasons field.
				seasons = if (result.isTv) JsonPrimitive("all") else null,
			)
			apiClient.request(
				method = HttpMethod.POST,
				pathTemplate = "$BASE/request",
				requestBody = body,
			)
			Result.success(Unit)
		} catch (error: Exception) {
			Timber.w(error, "Jellyseerr request failed for \"%s\"", result.displayTitle)
			Result.failure(error)
		}
	}
}
