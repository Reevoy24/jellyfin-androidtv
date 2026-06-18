package org.jellyfin.androidtv.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime

class CalendarViewModel(
	private val calendarRepository: CalendarRepository,
) : ViewModel() {
	sealed interface State {
		data object Loading : State
		data object Empty : State
		data class Content(val days: List<CalendarDay>) : State
	}

	private val _state = MutableStateFlow<State>(State.Loading)
	val state = _state.asStateFlow()

	init {
		load()
	}

	fun load() {
		_state.value = State.Loading
		viewModelScope.launch {
			val days = calendarRepository.getUpcoming()
				.filter { it.localDate != null }
				.groupBy { it.localDate!! }
				.toSortedMap()
				.map { (date, items) ->
					CalendarDay(
						date = date,
						items = items.sortedWith(compareBy({ it.localTime ?: LocalTime.MIN }, { it.title })),
					)
				}

			_state.value = if (days.isEmpty()) State.Empty else State.Content(days)
		}
	}
}
