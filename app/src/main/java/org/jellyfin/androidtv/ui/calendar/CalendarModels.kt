package org.jellyfin.androidtv.ui.calendar

import kotlinx.serialization.Serializable
import org.jellyfin.androidtv.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * An upcoming release from the Jellyfin-Enhanced plugin's `/JellyfinEnhanced/arr/calendar` endpoint
 * (a Sonarr/Radarr calendar item). Only the fields the app renders are modelled.
 */
@Serializable
data class CalendarItem(
	val id: String? = null,
	val source: String = "",
	val type: String = "",
	val title: String = "",
	val subtitle: String? = null,
	val releaseDate: String? = null,
	val releaseType: String = "",
	val hasFile: Boolean = false,
	val monitored: Boolean = false,
	val posterUrl: String? = null,
	val backdropUrl: String? = null,
	val overview: String? = null,
	val seasonNumber: Int? = null,
	val episodeNumber: Int? = null,
	val episodeTitle: String? = null,
	val instanceName: String? = null,
) {
	val release: CalendarReleaseType get() = CalendarReleaseType.from(releaseType)

	val sourceLabel: String get() = instanceName?.takeIf { it.isNotBlank() } ?: source

	val posterOrBackdrop: String? get() = posterUrl ?: backdropUrl

	private val instant: Instant?
		get() = releaseDate?.let { raw ->
			runCatching { Instant.parse(raw) }.getOrNull()
				?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
				?: runCatching { LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC) }.getOrNull()
		}

	/** Local date of the release, used to group items into days. */
	val localDate: LocalDate?
		get() = instant?.atZone(ZoneId.systemDefault())?.toLocalDate()

	/** Local time of the release, or null when it has no meaningful time (midnight). */
	val localTime: LocalTime?
		get() = instant?.atZone(ZoneId.systemDefault())?.toLocalTime()?.takeIf { it != LocalTime.MIDNIGHT }
}

/** Release type → accent color + label, mirroring the plugin's calendar colours. */
enum class CalendarReleaseType(val color: Long, val labelRes: Int) {
	CINEMA(0xFF2196F3, R.string.calendar_cinema_release),
	DIGITAL(0xFF9C27B0, R.string.calendar_digital_release),
	PHYSICAL(0xFFFF5722, R.string.calendar_physical_release),
	EPISODE(0xFF4CAF50, R.string.calendar_episode),
	OTHER(0xFF00A4DC, R.string.calendar_release);

	companion object {
		fun from(releaseType: String) = when (releaseType) {
			"CinemaRelease" -> CINEMA
			"DigitalRelease" -> DIGITAL
			"PhysicalRelease" -> PHYSICAL
			"Episode" -> EPISODE
			else -> OTHER
		}
	}
}

/** Items that release on a given day, with a display label ("Today", "Tomorrow", or a date). */
data class CalendarDay(
	val date: LocalDate,
	val items: List<CalendarItem>,
)
