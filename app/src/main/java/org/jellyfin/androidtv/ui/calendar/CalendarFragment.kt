package org.jellyfin.androidtv.ui.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.CircularProgressIndicator
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.jellyfin.androidtv.ui.shared.toolbar.rememberMainToolbarFocusRequesters
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class CalendarFragment : Fragment() {
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	) = content {
		val viewModel = koinViewModel<CalendarViewModel>()
		val state by viewModel.state.collectAsState()
		val toolbarFocusRequesters = rememberMainToolbarFocusRequesters()
		val contentFocusRequester = remember { FocusRequester() }

		LaunchedEffect(state) {
			val target = if (state is CalendarViewModel.State.Content) contentFocusRequester
			else toolbarFocusRequesters.calendar
			runCatching { target.requestFocus() }
		}

		Column {
			MainToolbar(
				activeButton = MainToolbarActiveButton.Calendar,
				focusRequesters = toolbarFocusRequesters,
				downFocusRequester = contentFocusRequester,
			)

			when (val current = state) {
				is CalendarViewModel.State.Loading -> CenteredBox {
					CircularProgressIndicator(modifier = Modifier.size(48.dp))
				}

				is CalendarViewModel.State.Empty -> CenteredBox {
					Text(
						text = stringResource(R.string.calendar_empty),
						color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.7f),
						fontSize = 16.sp,
					)
				}

				is CalendarViewModel.State.Content -> Agenda(
					days = current.days,
					firstFocusRequester = contentFocusRequester,
				)
			}
		}
	}
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
	Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun Agenda(days: List<CalendarDay>, firstFocusRequester: FocusRequester) {
	Column(
		modifier = Modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 48.dp, vertical = 12.dp),
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		var isFirst = true
		for (day in days) {
			DayHeader(day.date)
			for (item in day.items) {
				CalendarEventCard(
					item = item,
					modifier = if (isFirst) Modifier.focusRequester(firstFocusRequester) else Modifier,
				)
				isFirst = false
			}
		}
	}
}

@Composable
private fun DayHeader(date: LocalDate) {
	val today = LocalDate.now()
	val relative = when (date) {
		today -> stringResource(R.string.calendar_today)
		today.plusDays(1) -> stringResource(R.string.calendar_tomorrow)
		else -> null
	}
	val formatted = remember(date) { date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }

	Text(
		text = if (relative != null) "$relative · $formatted" else formatted,
		color = JellyfinTheme.colorScheme.onBackground,
		fontSize = 14.sp,
		fontWeight = FontWeight.Bold,
		modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
	)
}

@Composable
private fun CalendarEventCard(item: CalendarItem, modifier: Modifier = Modifier) {
	var focused by remember { mutableStateOf(false) }
	val accent = Color(item.release.color)
	val shape = RoundedCornerShape(10.dp)

	Box(
		modifier = modifier
			.fillMaxWidth()
			.height(100.dp)
			.clip(shape)
			.onFocusChanged { focused = it.isFocused }
			.background(JellyfinTheme.colorScheme.surface)
			.border(
				width = if (focused) 2.dp else 0.dp,
				color = if (focused) JellyfinTheme.colorScheme.buttonFocused else Color.Transparent,
				shape = shape,
			)
			.focusable(),
	) {
		val backdrop = item.backdropUrl ?: item.posterUrl
		if (backdrop != null) {
			AsyncImage(
				url = backdrop,
				modifier = Modifier.fillMaxSize(),
				scaleType = ImageView.ScaleType.CENTER_CROP,
			)
		}

		// Dark gradient (heavier on the left) keeps the title readable over the backdrop.
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(
					Brush.horizontalGradient(
						listOf(Color.Black.copy(alpha = 0.88f), Color.Black.copy(alpha = 0.35f))
					)
				)
		)

		// Release-type accent bar.
		Box(
			modifier = Modifier
				.width(5.dp)
				.fillMaxHeight()
				.background(accent)
				.align(Alignment.CenterStart)
		)

		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(start = 18.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
			verticalArrangement = Arrangement.Center,
		) {
			Text(
				text = item.title,
				color = Color.White,
				fontSize = 16.sp,
				fontWeight = FontWeight.Bold,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			item.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
				Text(
					text = subtitle,
					color = Color.White.copy(alpha = 0.85f),
					fontSize = 12.sp,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.padding(top = 2.dp),
				)
			}

			Row(
				modifier = Modifier.padding(top = 6.dp),
				horizontalArrangement = Arrangement.spacedBy(6.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Text(
					text = stringResource(item.release.labelRes),
					color = accent,
					fontSize = 11.sp,
					fontWeight = FontWeight.Bold,
				)
				Text(
					text = "· ${item.sourceLabel}",
					color = Color.White.copy(alpha = 0.7f),
					fontSize = 11.sp,
				)
				item.localTime?.let { time ->
					Text(
						text = "· ${time.format(TIME_FORMATTER)}",
						color = Color.White.copy(alpha = 0.7f),
						fontSize = 11.sp,
					)
				}
			}
		}

		if (item.hasFile) {
			Text(
				text = stringResource(R.string.calendar_available),
				color = JellyfinTheme.colorScheme.onBadge,
				fontSize = 10.sp,
				fontWeight = FontWeight.Bold,
				modifier = Modifier
					.align(Alignment.TopEnd)
					.padding(8.dp)
					.clip(RoundedCornerShape(4.dp))
					.background(JellyfinTheme.colorScheme.badge)
					.padding(horizontal = 6.dp, vertical = 3.dp),
			)
		}
	}
}

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
