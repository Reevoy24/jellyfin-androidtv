package org.jellyfin.androidtv.ui.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.CircularProgressIndicator
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.jellyfin.androidtv.ui.shared.toolbar.rememberMainToolbarFocusRequesters
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DAY_MONTH_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("d. MMMM")
private val MONTH_YEAR_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
private val FULL_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM")

/** Monday of the week containing [date]. */
private fun weekStart(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

class CalendarFragment : Fragment() {
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	) = content {
		val viewModel = koinViewModel<CalendarViewModel>()
		val items by viewModel.items.collectAsState()
		val view by viewModel.view.collectAsState()
		val anchor by viewModel.anchor.collectAsState()

		val toolbarFocusRequesters = rememberMainToolbarFocusRequesters()
		val headerFocusRequester = remember { FocusRequester() }

		LaunchedEffect(items != null) {
			runCatching { (if (items != null) headerFocusRequester else toolbarFocusRequesters.calendar).requestFocus() }
		}

		val byDate = remember(items) {
			items.orEmpty()
				.filter { it.localDate != null }
				.groupBy { it.localDate!! }
				.mapValues { (_, list) -> list.sortedWith(compareBy({ it.localTime ?: java.time.LocalTime.MIN }, { it.title })) }
		}

		Column(modifier = Modifier.fillMaxSize()) {
			MainToolbar(
				activeButton = MainToolbarActiveButton.Calendar,
				focusRequesters = toolbarFocusRequesters,
				downFocusRequester = headerFocusRequester,
			)

			when {
				items == null -> CenteredBox { CircularProgressIndicator(modifier = Modifier.height(48.dp)) }

				else -> {
					CalendarHeader(
						view = view,
						anchor = anchor,
						todayFocusRequester = headerFocusRequester,
						onPrevious = viewModel::goPrevious,
						onToday = viewModel::goToday,
						onNext = viewModel::goNext,
						onSetView = viewModel::setView,
					)

					Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
						when (view) {
							CalendarView.AGENDA -> AgendaView(byDate)
							CalendarView.DAY -> DayView(byDate, anchor)
							CalendarView.WEEK -> WeekView(byDate, anchor)
							CalendarView.MONTH -> MonthView(byDate, anchor)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
	Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun CalendarHeader(
	view: CalendarView,
	anchor: LocalDate,
	todayFocusRequester: FocusRequester,
	onPrevious: () -> Unit,
	onToday: () -> Unit,
	onNext: () -> Unit,
	onSetView: (CalendarView) -> Unit,
) {
	val periodLabel = when (view) {
		CalendarView.AGENDA -> stringResource(R.string.calendar_view_agenda)
		CalendarView.DAY -> anchor.format(FULL_DATE_FORMATTER)
		CalendarView.WEEK -> {
			val monday = weekStart(anchor)
			"${monday.format(DAY_MONTH_FORMATTER)} – ${monday.plusDays(6).format(DAY_MONTH_FORMATTER)}"
		}

		CalendarView.MONTH -> anchor.format(MONTH_YEAR_FORMATTER)
	}

	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 32.dp, vertical = 8.dp),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			text = periodLabel,
			color = JellyfinTheme.colorScheme.onBackground,
			fontSize = 18.sp,
			fontWeight = FontWeight.Bold,
		)

		if (view != CalendarView.AGENDA) {
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
				Button(onClick = onPrevious) { Text("‹") }
				Button(onClick = onToday, modifier = Modifier.focusRequester(todayFocusRequester)) {
					Text(stringResource(R.string.calendar_today))
				}
				Button(onClick = onNext) { Text("›") }
			}
		} else {
			// Keep the today focus requester attached so it always has a target.
			Box(modifier = Modifier.focusRequester(todayFocusRequester).focusable())
		}

		Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
			ViewButton(stringResource(R.string.calendar_view_day), view == CalendarView.DAY) { onSetView(CalendarView.DAY) }
			ViewButton(stringResource(R.string.calendar_view_week), view == CalendarView.WEEK) { onSetView(CalendarView.WEEK) }
			ViewButton(stringResource(R.string.calendar_view_month), view == CalendarView.MONTH) { onSetView(CalendarView.MONTH) }
			ViewButton(stringResource(R.string.calendar_view_agenda), view == CalendarView.AGENDA) { onSetView(CalendarView.AGENDA) }
		}
	}
}

@Composable
private fun ViewButton(label: String, active: Boolean, onClick: () -> Unit) {
	val colors = if (active) {
		ButtonDefaults.colors(
			containerColor = JellyfinTheme.colorScheme.buttonActive,
			contentColor = JellyfinTheme.colorScheme.onButtonActive,
		)
	} else {
		ButtonDefaults.colors()
	}
	Button(onClick = onClick, colors = colors) { Text(label) }
}

// ---- Views ----------------------------------------------------------------

@Composable
private fun AgendaView(byDate: Map<LocalDate, List<CalendarItem>>) {
	val today = LocalDate.now()
	val days = byDate.keys.filter { it >= today }.sorted()

	if (days.isEmpty()) {
		CenteredBox {
			Text(
				text = stringResource(R.string.calendar_empty),
				color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.7f),
				fontSize = 16.sp,
			)
		}
		return
	}

	Column(
		modifier = Modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 48.dp, vertical = 8.dp),
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		for (day in days) {
			DayHeader(day)
			for (item in byDate[day].orEmpty()) EventCard(item)
		}
	}
}

@Composable
private fun DayView(byDate: Map<LocalDate, List<CalendarItem>>, anchor: LocalDate) {
	val dayItems = byDate[anchor].orEmpty()
	if (dayItems.isEmpty()) {
		CenteredBox {
			Text(
				text = stringResource(R.string.calendar_empty),
				color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.7f),
				fontSize = 16.sp,
			)
		}
		return
	}
	Column(
		modifier = Modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 48.dp, vertical = 8.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		for (item in dayItems) EventCard(item)
	}
}

@Composable
private fun WeekView(byDate: Map<LocalDate, List<CalendarItem>>, anchor: LocalDate) {
	val monday = weekStart(anchor)
	val days = (0..6).map { monday.plusDays(it.toLong()) }

	Column(
		modifier = Modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 24.dp, vertical = 8.dp),
	) {
		Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			for (day in days) {
				Column(
					modifier = Modifier.weight(1f),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					ColumnDayHeader(day)
					for (item in byDate[day].orEmpty()) EventCard(item)
				}
			}
		}
	}
}

@Composable
private fun MonthView(byDate: Map<LocalDate, List<CalendarItem>>, anchor: LocalDate) {
	val gridStart = weekStart(anchor.withDayOfMonth(1))
	val weeks = (0..5).map { w -> (0..6).map { d -> gridStart.plusDays((w * 7 + d).toLong()) } }

	Column(
		modifier = Modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 24.dp, vertical = 8.dp),
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		// Weekday header row
		Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
			for (d in 0..6) {
				Text(
					text = gridStart.plusDays(d.toLong()).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
					color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.6f),
					fontSize = 11.sp,
					fontWeight = FontWeight.Bold,
					textAlign = TextAlign.Center,
					modifier = Modifier.weight(1f),
				)
			}
		}
		for (week in weeks) {
			Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
				for (day in week) {
					MonthCell(
						date = day,
						items = byDate[day].orEmpty(),
						inMonth = day.month == anchor.month,
						modifier = Modifier.weight(1f),
					)
				}
			}
		}
	}
}

// ---- Cells & cards --------------------------------------------------------

@Composable
private fun DayHeader(date: LocalDate) {
	val today = LocalDate.now()
	val relative = when (date) {
		today -> stringResource(R.string.calendar_today)
		today.plusDays(1) -> stringResource(R.string.calendar_tomorrow)
		else -> null
	}
	val formatted = remember(date) { date.format(DAY_MONTH_FORMATTER) }
	Text(
		text = if (relative != null) "$relative · $formatted" else formatted,
		color = JellyfinTheme.colorScheme.onBackground,
		fontSize = 14.sp,
		fontWeight = FontWeight.Bold,
		modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
	)
}

@Composable
private fun ColumnDayHeader(date: LocalDate) {
	val isToday = date == LocalDate.now()
	val color = if (isToday) JellyfinTheme.colorScheme.buttonFocused else JellyfinTheme.colorScheme.onBackground
	Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
		Text(
			text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
			color = color.copy(alpha = 0.7f),
			fontSize = 11.sp,
		)
		Text(
			text = date.dayOfMonth.toString(),
			color = color,
			fontSize = 16.sp,
			fontWeight = FontWeight.Bold,
		)
	}
}

@Composable
private fun MonthCell(date: LocalDate, items: List<CalendarItem>, inMonth: Boolean, modifier: Modifier = Modifier) {
	var focused by remember { mutableStateOf(false) }
	val isToday = date == LocalDate.now()
	val baseAlpha = if (inMonth) 1f else 0.4f

	Column(
		modifier = modifier
			.heightIn(min = 92.dp)
			.clip(RoundedCornerShape(6.dp))
			.onFocusChanged { focused = it.isFocused }
			.background(
				if (focused) JellyfinTheme.colorScheme.listButtonFocused else JellyfinTheme.colorScheme.surface.copy(alpha = baseAlpha)
			)
			.focusable()
			.padding(6.dp),
	) {
		Text(
			text = date.dayOfMonth.toString(),
			color = (if (isToday) JellyfinTheme.colorScheme.buttonFocused else JellyfinTheme.colorScheme.onBackground).copy(alpha = baseAlpha),
			fontSize = 12.sp,
			fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
		)
		val shown = items.take(3)
		for (item in shown) {
			Text(
				text = item.title,
				color = Color(item.release.color).copy(alpha = baseAlpha),
				fontSize = 9.sp,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.padding(top = 2.dp),
			)
		}
		if (items.size > shown.size) {
			Text(
				text = "+${items.size - shown.size}",
				color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.6f * baseAlpha),
				fontSize = 9.sp,
				modifier = Modifier.padding(top = 1.dp),
			)
		}
	}
}

@Composable
private fun EventCard(item: CalendarItem, modifier: Modifier = Modifier) {
	val navigationRepository = koinInject<NavigationRepository>()
	val targetId = item.jellyfinItemId
	var focused by remember { mutableStateOf(false) }
	val accent = Color(item.release.color)
	val shape = RoundedCornerShape(10.dp)

	Box(
		modifier = modifier
			.fillMaxWidth()
			.height(92.dp)
			.clip(shape)
			.onFocusChanged { focused = it.isFocused }
			.background(JellyfinTheme.colorScheme.surface)
			.border(
				width = if (focused) 2.dp else 0.dp,
				color = if (focused) JellyfinTheme.colorScheme.buttonFocused else Color.Transparent,
				shape = shape,
			)
			// Clickable (and focusable) when there is a Jellyfin item to open — series, or movies
			// already in the library. Otherwise just focusable so D-pad can still scroll past it.
			.then(
				if (targetId != null) {
					Modifier.clickable { navigationRepository.navigate(Destinations.itemDetails(targetId)) }
				} else {
					Modifier.focusable()
				}
			),
	) {
		val backdrop = item.backdropUrl ?: item.posterUrl
		if (backdrop != null) {
			AsyncImage(
				url = backdrop,
				modifier = Modifier.fillMaxSize(),
				scaleType = ImageView.ScaleType.CENTER_CROP,
			)
		}

		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(
					Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.85f)))
				)
		)

		Box(
			modifier = Modifier
				.fillMaxWidth()
				.height(4.dp)
				.background(accent)
				.align(Alignment.BottomStart)
		)

		if (item.localTime != null || item.hasFile) {
			Row(
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(6.dp),
				horizontalArrangement = Arrangement.spacedBy(4.dp),
			) {
				item.localTime?.let { time ->
					Text(
						text = time.format(TIME_FORMATTER),
						color = JellyfinTheme.colorScheme.onBadge,
						fontSize = 9.sp,
						fontWeight = FontWeight.Bold,
						modifier = Modifier
							.clip(RoundedCornerShape(4.dp))
							.background(if (item.hasFile) JellyfinTheme.colorScheme.badge else Color.Black.copy(alpha = 0.6f))
							.padding(horizontal = 5.dp, vertical = 2.dp),
					)
				}
			}
		}

		Column(
			modifier = Modifier
				.align(Alignment.BottomStart)
				.fillMaxWidth()
				.padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
		) {
			Text(
				text = item.title,
				color = Color.White,
				fontSize = 13.sp,
				fontWeight = FontWeight.Bold,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			item.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
				Text(
					text = subtitle,
					color = Color.White.copy(alpha = 0.85f),
					fontSize = 10.sp,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
			Text(
				text = "${stringResource(item.release.labelRes)} · ${item.sourceLabel}",
				color = Color.White.copy(alpha = 0.7f),
				fontSize = 9.sp,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}
