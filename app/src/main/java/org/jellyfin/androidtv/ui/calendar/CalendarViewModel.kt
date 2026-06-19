package org.jellyfin.androidtv.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The calendar display modes, matching the plugin's web calendar. */
enum class CalendarView { AGENDA, DAY, WEEK, MONTH }

class CalendarViewModel(
	private val calendarRepository: CalendarRepository,
) : ViewModel() {
	companion object {
		// Must match the window CalendarRepository fetches.
		private const val WINDOW_PAST_DAYS = 14L
		private const val WINDOW_FUTURE_DAYS = 180L
	}

	sealed interface State {
		data object Loading : State
		data object Error : State
		data class Content(val items: List<CalendarItem>) : State
	}

	private val _state = MutableStateFlow<State>(State.Loading)
	val state = _state.asStateFlow()

	private val _view = MutableStateFlow(CalendarView.WEEK)
	val view = _view.asStateFlow()

	private val _anchor = MutableStateFlow(LocalDate.now())
	val anchor = _anchor.asStateFlow()

	// Anchor is clamped to the loaded window so navigation can't run into empty (unfetched) periods.
	private val windowStart get() = LocalDate.now().minusDays(WINDOW_PAST_DAYS)
	private val windowEnd get() = LocalDate.now().plusDays(WINDOW_FUTURE_DAYS)

	init {
		load()
	}

	fun load() {
		_state.value = State.Loading
		viewModelScope.launch {
			val items = calendarRepository.getUpcoming()
			_state.value = if (items == null) State.Error else State.Content(items)
		}
	}

	fun setView(view: CalendarView) {
		_view.value = view
	}

	fun goToday() {
		_anchor.value = LocalDate.now().coerceIn(windowStart, windowEnd)
	}

	fun goPrevious() = shiftAnchor(-1)
	fun goNext() = shiftAnchor(1)

	/** Whether navigating in [direction] (-1/+1) would stay within the loaded window. */
	fun canShift(direction: Int): Boolean = nextAnchor(direction) != _anchor.value

	private fun shiftAnchor(direction: Int) {
		_anchor.value = nextAnchor(direction)
	}

	private fun nextAnchor(direction: Int): LocalDate {
		val raw = when (_view.value) {
			CalendarView.DAY -> _anchor.value.plusDays(direction.toLong())
			CalendarView.WEEK -> _anchor.value.plusWeeks(direction.toLong())
			CalendarView.MONTH -> _anchor.value.plusMonths(direction.toLong())
			CalendarView.AGENDA -> _anchor.value
		}
		return raw.coerceIn(windowStart, windowEnd)
	}
}
