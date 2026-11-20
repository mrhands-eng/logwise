package ru.astondev.tempomobile.ui.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.astondev.tempomobile.data.local.UserPreferences
import ru.astondev.tempomobile.data.repo.WorklogRepository
import ru.astondev.tempomobile.domain.DaySummary
import ru.astondev.tempomobile.domain.Worklog
import ru.astondev.tempomobile.util.DateTimeFormats
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class WeekUiState(
    val weekStart: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    val selectedDate: LocalDate = defaultSelectedDate(hideWeekends = true),
    val worklogs: List<Worklog> = emptyList(),
    val loading: Boolean = false,
    val weekLoading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val copyDialogVisible: Boolean = false,
    val copyTargetDate: LocalDate = LocalDate.now(),
    val hideWeekends: Boolean = true,
    val forceShowWeekends: Boolean = false,
    val dailyTargetHours: Int = 8,
    val pendingDelete: Worklog? = null,
    val undoDelete: Worklog? = null,
) {
    val effectiveHideWeekends: Boolean
        get() = hideWeekends && !forceShowWeekends

    private val visibleDayCount: Int get() = if (effectiveHideWeekends) 5 else 7

    val weekDays: List<LocalDate>
        get() = (0 until visibleDayCount).map { weekStart.plusDays(it.toLong()) }

    val selectedWorklogs: List<Worklog>
        get() = worklogs.filter { it.date == selectedDate }

    fun daySummary(date: LocalDate): DaySummary {
        val seconds = worklogs.filter { it.date == date }.sumOf { it.timeSpentSeconds }
        return DaySummary(
            date = date,
            loggedSeconds = seconds,
            targetSeconds = dailyTargetHours * 3600,
        )
    }

    val weekLoggedSeconds: Int
        get() = worklogs.filter { it.date in weekDays }.sumOf { it.timeSpentSeconds }

    val weekTargetSeconds: Int
        get() = visibleDayCount * dailyTargetHours * 3600

    val weekLabel: String
        get() {
            val end = weekStart.plusDays((visibleDayCount - 1).toLong())
            return "${weekStart.format(DateTimeFormats.weekRange)} – ${end.format(DateTimeFormats.weekRange)}"
        }

    val hasHiddenWeekendLogs: Boolean
        get() {
            if (!hideWeekends || forceShowWeekends) return false
            return worklogs.any {
                it.date.dayOfWeek == DayOfWeek.SATURDAY || it.date.dayOfWeek == DayOfWeek.SUNDAY
            }
        }
}

internal fun defaultSelectedDate(hideWeekends: Boolean): LocalDate {
    val today = LocalDate.now()
    if (!hideWeekends) return today
    return when (today.dayOfWeek) {
        DayOfWeek.SATURDAY -> today.minusDays(1)
        DayOfWeek.SUNDAY -> today.minusDays(2)
        else -> today
    }
}

private fun nextWorkday(from: LocalDate, hideWeekends: Boolean): LocalDate {
    var d = from.plusDays(1)
    if (!hideWeekends) return d
    while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) {
        d = d.plusDays(1)
    }
    return d
}

@HiltViewModel
class WeekViewModel @Inject constructor(
    private val worklogRepository: WorklogRepository,
    userPreferences: UserPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(WeekUiState())
    private var undoJob: Job? = null

    val state: StateFlow<WeekUiState> = combine(
        _state,
        userPreferences.settings,
    ) { s, settings ->
        val hide = settings.hideWeekends
        val effectiveHide = hide && !s.forceShowWeekends
        val selected = if (effectiveHide &&
            (s.selectedDate.dayOfWeek == DayOfWeek.SATURDAY || s.selectedDate.dayOfWeek == DayOfWeek.SUNDAY)
        ) {
            defaultSelectedDate(true)
        } else {
            s.selectedDate
        }
        s.copy(
            hideWeekends = hide,
            dailyTargetHours = settings.dailyTargetHours,
            selectedDate = selected,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value)

    fun selectDate(date: LocalDate) {
        _state.update { it.copy(selectedDate = date) }
    }

    fun previousWeek() {
        shiftWeek(-1)
    }

    fun nextWeek() {
        shiftWeek(1)
    }

    fun goToToday() {
        val hide = _state.value.effectiveHideWeekends
        val today = defaultSelectedDate(hide)
        val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        _state.update {
            it.copy(weekStart = start, selectedDate = today, forceShowWeekends = false)
        }
        refresh(showWeekLoading = true)
    }

    fun revealWeekends() {
        _state.update { s ->
            val weekend = s.worklogs
                .map { it.date }
                .firstOrNull {
                    it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY
                }
            s.copy(
                forceShowWeekends = true,
                selectedDate = weekend ?: s.selectedDate,
            )
        }
    }

    private fun shiftWeek(deltaWeeks: Long) {
        val s = _state.value
        val start = s.weekStart.plusWeeks(deltaWeeks)
        val maxIdx = if (s.effectiveHideWeekends) 4 else 6
        val idx = (s.selectedDate.dayOfWeek.value - 1).coerceIn(0, maxIdx)
        _state.update {
            it.copy(weekStart = start, selectedDate = start.plusDays(idx.toLong()))
        }
        refresh(showWeekLoading = true)
    }

    fun refresh(fromPull: Boolean = false, showWeekLoading: Boolean = false) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = !fromPull && it.worklogs.isEmpty(),
                    weekLoading = showWeekLoading || (!fromPull && it.worklogs.isNotEmpty()),
                    refreshing = fromPull,
                    error = null,
                )
            }
            val weekStart = _state.value.weekStart
            val result = worklogRepository.getWorklogs(
                from = weekStart,
                to = weekStart.plusDays(6),
                forceRefresh = fromPull || showWeekLoading,
            )
            _state.update {
                if (result.isSuccess) {
                    it.copy(
                        worklogs = result.getOrDefault(emptyList()),
                        loading = false,
                        weekLoading = false,
                        refreshing = false,
                        error = null,
                    )
                } else {
                    it.copy(
                        loading = false,
                        weekLoading = false,
                        refreshing = false,
                        error = result.exceptionOrNull()?.message,
                    )
                }
            }
        }
    }

    fun requestDelete(worklog: Worklog) {
        _state.update { it.copy(pendingDelete = worklog) }
    }

    fun cancelDelete() {
        _state.update { it.copy(pendingDelete = null) }
    }

    fun confirmDelete() {
        val pending = _state.value.pendingDelete ?: return
        _state.update { it.copy(pendingDelete = null) }
        viewModelScope.launch {
            val result = worklogRepository.delete(pending.id)
            if (result.isSuccess) {
                worklogRepository.invalidateCache()
                _state.update { s ->
                    s.copy(
                        worklogs = s.worklogs.filterNot { it.id == pending.id },
                        undoDelete = pending,
                        message = "Запись удалена",
                    )
                }
                undoJob?.cancel()
                undoJob = viewModelScope.launch {
                    delay(5_000)
                    _state.update { it.copy(undoDelete = null) }
                }
            } else {
                _state.update { it.copy(error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun undoDelete() {
        val wl = _state.value.undoDelete ?: return
        undoJob?.cancel()
        _state.update { it.copy(undoDelete = null, message = null) }
        viewModelScope.launch {
            val issue = wl.issueId ?: wl.issueKey
            if (issue == null) {
                _state.update { it.copy(error = "Нельзя восстановить: нет задачи") }
                return@launch
            }
            val result = worklogRepository.create(
                issueKeyOrId = issue,
                started = wl.started,
                timeSpentSeconds = wl.timeSpentSeconds,
                comment = wl.comment,
                attributes = wl.attributes,
            )
            if (result.isSuccess) {
                worklogRepository.invalidateCache()
                _state.update { it.copy(message = "Запись восстановлена") }
                refresh()
            } else {
                _state.update { it.copy(error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun showCopyDialog(visible: Boolean) {
        _state.update {
            it.copy(
                copyDialogVisible = visible,
                copyTargetDate = nextWorkday(it.selectedDate, it.effectiveHideWeekends),
            )
        }
    }

    fun onCopyTargetChange(date: LocalDate) {
        _state.update { it.copy(copyTargetDate = date) }
    }

    fun copySelectedDay() {
        viewModelScope.launch {
            val s = state.value
            val source = s.selectedWorklogs
            if (source.isEmpty()) {
                _state.update { it.copy(error = "Нет записей для копирования", copyDialogVisible = false) }
                return@launch
            }
            val result = worklogRepository.copyDay(s.selectedDate, s.copyTargetDate, source)
            worklogRepository.invalidateCache()
            _state.update {
                it.copy(
                    copyDialogVisible = false,
                    message = if (result.isSuccess) {
                        "Скопировано записей: ${result.getOrDefault(0)}"
                    } else {
                        null
                    },
                    error = result.exceptionOrNull()?.message,
                )
            }
            if (result.isSuccess || (result.exceptionOrNull()?.message?.contains("Скопировано") == true)) {
                refresh()
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
    fun clearError() = _state.update { it.copy(error = null) }
}
