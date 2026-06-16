package org.jellyfin.androidtv.ui.jellyseerr

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Models for the Jellyseerr proxy endpoints exposed by the Jellyfin-Enhanced server plugin
 * (https://github.com/n00bcodr/Jellyfin-Enhanced). All endpoints live under
 * `/JellyfinEnhanced/jellyseerr/...` on the Jellyfin server itself and are authenticated with the
 * regular Jellyfin access token, so no Jellyseerr credentials are needed in the app.
 *
 * These classes intentionally only model the fields the app uses; the underlying responses are
 * standard Jellyseerr API responses with many more fields (deserialized with ignoreUnknownKeys).
 */

const val JELLYSEERR_TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w300"

/** Response of `GET /JellyfinEnhanced/jellyseerr/user-status`. */
@Serializable
data class JellyseerrUserStatus(
	val active: Boolean = false,
	val userFound: Boolean = false,
	val jellyseerrUserId: String? = null,
	val reason: String? = null,
	val message: String? = null,
) {
	/** Jellyseerr is reachable AND the current Jellyfin user is linked to a Jellyseerr account. */
	val available: Boolean get() = active && userFound
}

/** Response of `GET /JellyfinEnhanced/jellyseerr/search`. */
@Serializable
data class JellyseerrSearchResponse(
	val page: Int = 1,
	val totalPages: Int = 0,
	val totalResults: Int = 0,
	val results: List<JellyseerrSearchResult> = emptyList(),
)

/** A single media result. `id` is the TMDB id. */
@Serializable
data class JellyseerrSearchResult(
	val id: Int = 0,
	val mediaType: String = "",
	val title: String? = null,
	val name: String? = null,
	val originalTitle: String? = null,
	val originalName: String? = null,
	val overview: String? = null,
	val posterPath: String? = null,
	val releaseDate: String? = null,
	val firstAirDate: String? = null,
	val mediaInfo: JellyseerrMediaInfo? = null,
) {
	val isMovie: Boolean get() = mediaType == "movie"
	val isTv: Boolean get() = mediaType == "tv"

	val displayTitle: String
		get() = title ?: name ?: originalTitle ?: originalName ?: ""

	val year: String?
		get() = (releaseDate ?: firstAirDate)?.takeIf { it.length >= 4 }?.substring(0, 4)

	val posterUrl: String?
		get() = posterPath?.let { JELLYSEERR_TMDB_IMAGE_BASE + it }

	val status: JellyseerrMediaStatus
		get() = JellyseerrMediaStatus.fromCode(mediaInfo?.status)
}

@Serializable
data class JellyseerrMediaInfo(
	val status: Int? = null,
	val status4k: Int? = null,
)

/**
 * Jellyseerr media availability status. Determines whether an item can still be requested or is
 * already pending/available.
 */
enum class JellyseerrMediaStatus {
	/** Not known to Jellyseerr / no request yet — can be requested. */
	UNKNOWN,

	/** A request exists but is not yet processed/available. */
	PENDING,

	/** Already (partially) available on the server. */
	AVAILABLE;

	companion object {
		fun fromCode(code: Int?): JellyseerrMediaStatus = when (code) {
			// Jellyseerr MediaStatus: 1=Unknown, 2=Pending, 3=Processing, 4=Partially Available, 5=Available
			2, 3 -> PENDING
			4, 5 -> AVAILABLE
			else -> UNKNOWN
		}
	}
}

/** Body of `POST /JellyfinEnhanced/jellyseerr/request`, forwarded verbatim to Jellyseerr. */
@Serializable
data class JellyseerrRequestBody(
	val mediaType: String,
	val mediaId: Int,
	/** "all" for a whole series, or an array of season numbers. Omitted for movies. */
	val seasons: JsonElement? = null,
)
