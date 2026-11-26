package com.mailtabs.app.ui.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mailtabs.app.data.notes.NoteEntity
import com.mailtabs.app.data.notes.NotesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

/** Видимая сетка месяца: 6 недель начиная с первого дня недели локали. */
data class MonthGrid(val month: YearMonth, val days: List<LocalDate>) {
    companion object {
        fun of(month: YearMonth, firstDayOfWeek: DayOfWeek): MonthGrid {
            val start = month.atDay(1).with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
            return MonthGrid(month, List(42) { start.plusDays(it.toLong()) })
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    private val notes: NotesRepository,
) : ViewModel() {

    val firstDayOfWeek: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

    /** Выбранный день (epochDay) — переживает пересоздание и смерть процесса. */
    val selectedDay: StateFlow<LocalDate> = saved.getStateFlow(KEY_DAY, LocalDate.now().toEpochDay())
        .map { LocalDate.ofEpochDay(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocalDate.now())

    /** Показываемый месяц: "yyyy-MM". */
    val grid: StateFlow<MonthGrid> = saved.getStateFlow(KEY_MONTH, YearMonth.now().toString())
        .map { MonthGrid.of(YearMonth.parse(it), firstDayOfWeek) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MonthGrid.of(YearMonth.now(), firstDayOfWeek))

    /** Заметки всех дней видимой сетки, сгруппированные по дню. */
    val notesByDay: StateFlow<Map<LocalDate, List<NoteEntity>>> = grid
        .flatMapLatest { g -> notes.range(g.days.first().toEpochDay(), g.days.last().toEpochDay()) }
        .map { list -> list.groupBy { LocalDate.ofEpochDay(it.day) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val dayNotes: StateFlow<List<NoteEntity>> = combine(selectedDay, notesByDay) { day, map -> map[day].orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun select(day: LocalDate) {
        saved[KEY_DAY] = day.toEpochDay()
        val m = YearMonth.from(day)
        if (m.toString() != saved.get<String>(KEY_MONTH)) saved[KEY_MONTH] = m.toString()
    }

    fun showMonth(month: YearMonth) {
        saved[KEY_MONTH] = month.toString()
    }

    fun nextMonth() = showMonth(grid.value.month.plusMonths(1))
    fun prevMonth() = showMonth(grid.value.month.minusMonths(1))
    fun today() = select(LocalDate.now())

    fun save(note: NoteEntity) {
        if (note.title.isBlank() && note.text.isBlank()) return
        viewModelScope.launch { notes.save(note) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { notes.delete(id) }
    }

    private companion object {
        const val KEY_DAY = "calendar_day"
        const val KEY_MONTH = "calendar_month"
    }
}
