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
	// null = still loading; otherwise the (possibly empty) loaded items.
	private val _items = MutableStateFlow<List<CalendarItem>?>(null)
	val items = _items.asStateFlow()

	private val _view = MutableStateFlow(CalendarView.WEEK)
	val view = _view.asStateFlow()

	private val _anchor = MutableStateFlow(LocalDate.now())
	val anchor = _anchor.asStateFlow()

	init {
		load()
	}

	fun load() {
		_items.value = null
		viewModelScope.launch {
			_items.value = calendarRepository.getUpcoming()
		}
	}

	fun setView(view: CalendarView) {
		_view.value = view
	}

	fun goToday() {
		_anchor.value = LocalDate.now()
	}

	fun goPrevious() = shiftAnchor(-1)
	fun goNext() = shiftAnchor(1)

	private fun shiftAnchor(direction: Int) {
		val current = _anchor.value
		_anchor.value = when (_view.value) {
			CalendarView.DAY -> current.plusDays(direction.toLong())
			CalendarView.WEEK -> current.plusWeeks(direction.toLong())
			CalendarView.MONTH -> current.plusMonths(direction.toLong())
			CalendarView.AGENDA -> current
		}
	}
}
