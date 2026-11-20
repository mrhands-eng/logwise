package ru.astondev.tempomobile.ui.logtime

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.astondev.tempomobile.data.local.UserPreferences
import ru.astondev.tempomobile.data.repo.IssueRepository
import ru.astondev.tempomobile.data.repo.WorklogRepository
import ru.astondev.tempomobile.domain.IssueRef
import ru.astondev.tempomobile.domain.WorkAttributeDef
import ru.astondev.tempomobile.util.DateTimeFormats
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

enum class TimeInputMode {
    Duration,
    Interval,
}

data class LogTimeUiState(
    val editingId: String? = null,
    val issueKey: String = "",
    val issueSummary: String = "",
    val date: LocalDate = LocalDate.now(),
    val startTime: LocalTime = LocalTime.of(9, 0),
    val endTime: LocalTime = LocalTime.of(10, 0),
    val durationHours: Int = 0,
    val durationMinutes: Int = 0,
    val timeInputMode: TimeInputMode = TimeInputMode.Duration,
    val comment: String = "",
    val attributes: List<WorkAttributeDef> = emptyList(),
    val attributeValues: Map<String, String> = emptyMap(),
    val attributesError: String? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
    val resolvingIssue: Boolean = false,
    val pinAsDefault: Boolean = true,
    val suggestedStart: LocalTime = LocalTime.of(9, 0),
    val remainingTargetSeconds: Int = 0,
    val recentIssues: List<IssueRef> = emptyList(),
    val issueSuggestions: List<IssueRef> = emptyList(),
    val searchingIssues: Boolean = false,
) {
    val durationSeconds: Int
        get() = when (timeInputMode) {
            TimeInputMode.Duration -> durationHours * 3600 + durationMinutes * 60
            TimeInputMode.Interval -> DateTimeFormats.secondsBetween(startTime, endTime)
        }

    val autoIntervalLabel: String
        get() {
            val end = startTime.plusSeconds(durationSeconds.toLong())
            return "${startTime.format(DateTimeFormats.timeUi)} – ${end.format(DateTimeFormats.timeUi)}"
        }
}

@HiltViewModel
class LogTimeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val worklogRepository: WorklogRepository,
    private val issueRepository: IssueRepository,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private val worklogId: String = savedStateHandle["worklogId"] ?: ""
    private val dateArg: String = savedStateHandle["date"] ?: ""
    private val duplicateFrom: String = savedStateHandle["duplicateFrom"] ?: ""
    private val timerPrefill: Boolean =
        (savedStateHandle.get<String>("timerPrefill") ?: "false") == "true"

    private val _state = MutableStateFlow(LogTimeUiState())
    val state: StateFlow<LogTimeUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch { bootstrap() }
        viewModelScope.launch {
            userPreferences.recentIssues.collect { recent ->
                _state.update { it.copy(recentIssues = recent) }
            }
        }
    }

    private suspend fun bootstrap() {
        _state.update { it.copy(loading = true, error = null) }
        val attrsResult = worklogRepository.getWorkAttributes()
        val attrs = attrsResult.getOrDefault(emptyList())
        val attrsError = attrsResult.exceptionOrNull()?.message
        val rememberedAttrs = userPreferences.lastAttributeValues.first()
        val defaultIssue = issueRepository.defaultIssue.first()
        val settings = userPreferences.settings.first()

        val initialDate = dateArg.takeIf { it.isNotBlank() }?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        } ?: LocalDate.now()

        val dayStartFallback = LocalTime.of(settings.defaultDayStartHour, settings.defaultDayStartMinute)
        val dayLogs = worklogRepository.getWorklogs(initialDate, initialDate).getOrDefault(emptyList())
        val loggedToday = dayLogs.sumOf { it.timeSpentSeconds }
        val remaining = (settings.dailyTargetHours * 3600 - loggedToday).coerceAtLeast(0)
        val lastEnd = dayLogs.maxByOrNull { log -> log.end }?.end?.toLocalTime()
        var suggestedStart = lastEnd ?: dayStartFallback

        var timerDurationSecs = 0
        if (timerPrefill) {
            val timer = userPreferences.timerState.first()
            if (timer != null) {
                suggestedStart = Instant.ofEpochMilli(timer.startedAtEpochMs)
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime()
                timerDurationSecs = ((System.currentTimeMillis() - timer.startedAtEpochMs) / 1000)
                    .toInt()
                    .coerceAtLeast(60)
            }
        }

        fun defaultAttributeValues(): Map<String, String> {
            val fromMemory = attrs.associate { a ->
                a.key to (rememberedAttrs[a.key]
                    ?: a.values.firstOrNull()?.takeIf { a.required }.orEmpty())
            }.filterValues { it.isNotBlank() }
            return fromMemory
        }

        when {
            worklogId.isNotBlank() -> {
                val all = worklogRepository.getWorklogs(
                    initialDate.minusDays(14),
                    initialDate.plusDays(14),
                    forceRefresh = true,
                ).getOrDefault(emptyList())
                val existing = all.find { it.id == worklogId }
                if (existing != null) {
                    val secs = existing.timeSpentSeconds
                    _state.update {
                        it.copy(
                            editingId = existing.id,
                            issueKey = existing.issueKey ?: defaultIssue?.key.orEmpty(),
                            issueSummary = existing.issueSummary ?: defaultIssue?.summary.orEmpty(),
                            date = existing.date,
                            startTime = existing.started.toLocalTime(),
                            endTime = existing.end.toLocalTime(),
                            durationHours = secs / 3600,
                            durationMinutes = (secs % 3600) / 60,
                            timeInputMode = TimeInputMode.Interval,
                            comment = existing.comment,
                            attributes = attrs,
                            attributeValues = existing.attributes,
                            attributesError = attrsError,
                            suggestedStart = existing.started.toLocalTime(),
                            remainingTargetSeconds = remaining,
                            pinAsDefault = false,
                            loading = false,
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            error = "Запись не найдена",
                            issueKey = defaultIssue?.key.orEmpty(),
                            issueSummary = defaultIssue?.summary.orEmpty(),
                            date = initialDate,
                            startTime = suggestedStart,
                            endTime = suggestedStart,
                            suggestedStart = suggestedStart,
                            attributes = attrs,
                            attributesError = attrsError,
                            remainingTargetSeconds = remaining,
                            loading = false,
                        )
                    }
                }
            }
            duplicateFrom.isNotBlank() -> {
                val all = worklogRepository.getWorklogs(
                    LocalDate.now().minusDays(60),
                    LocalDate.now().plusDays(7),
                    forceRefresh = true,
                ).getOrDefault(emptyList())
                val source = all.find { it.id == duplicateFrom }
                val secs = source?.timeSpentSeconds ?: 3600
                _state.update {
                    it.copy(
                        editingId = null,
                        issueKey = source?.issueKey ?: defaultIssue?.key.orEmpty(),
                        issueSummary = source?.issueSummary ?: defaultIssue?.summary.orEmpty(),
                        date = initialDate,
                        startTime = suggestedStart,
                        endTime = suggestedStart.plusSeconds(secs.toLong()),
                        durationHours = secs / 3600,
                        durationMinutes = (secs % 3600) / 60,
                        timeInputMode = TimeInputMode.Duration,
                        comment = source?.comment.orEmpty(),
                        attributes = attrs,
                        attributeValues = source?.attributes?.ifEmpty { defaultAttributeValues() }
                            ?: defaultAttributeValues(),
                        attributesError = attrsError,
                        suggestedStart = suggestedStart,
                        remainingTargetSeconds = remaining,
                        loading = false,
                    )
                }
            }
            else -> {
                val timerIssue = if (timerPrefill) userPreferences.timerState.first() else null
                val dur = timerDurationSecs
                _state.update {
                    it.copy(
                        issueKey = timerIssue?.issueKey ?: defaultIssue?.key.orEmpty(),
                        issueSummary = timerIssue?.issueSummary ?: defaultIssue?.summary.orEmpty(),
                        date = initialDate,
                        startTime = suggestedStart,
                        endTime = suggestedStart.plusSeconds(dur.toLong()),
                        durationHours = dur / 3600,
                        durationMinutes = (dur % 3600) / 60,
                        timeInputMode = TimeInputMode.Duration,
                        suggestedStart = suggestedStart,
                        attributes = attrs,
                        attributeValues = defaultAttributeValues(),
                        attributesError = attrsError,
                        remainingTargetSeconds = remaining,
                        loading = false,
                    )
                }
            }
        }
    }

    fun onIssueKeyChange(value: String) {
        _state.update { it.copy(issueKey = value, error = null) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            val q = value.trim()
            if (q.length < 2) {
                _state.update { it.copy(issueSuggestions = emptyList(), searchingIssues = false) }
                return@launch
            }
            _state.update { it.copy(searchingIssues = true) }
            val result = issueRepository.searchByKeyFragment(q)
            _state.update {
                it.copy(
                    searchingIssues = false,
                    issueSuggestions = result.getOrDefault(emptyList()),
                )
            }
        }
    }

    fun selectIssue(issue: IssueRef) {
        searchJob?.cancel()
        _state.update {
            it.copy(
                issueKey = issue.key,
                issueSummary = issue.summary,
                issueSuggestions = emptyList(),
                searchingIssues = false,
                error = null,
            )
        }
    }

    fun onCommentChange(value: String) = _state.update { it.copy(comment = value) }
    fun setPinAsDefault(pin: Boolean) = _state.update { it.copy(pinAsDefault = pin) }

    fun onDateChange(value: LocalDate) {
        _state.update { it.copy(date = value) }
        viewModelScope.launch { refreshSuggestedStart(value) }
    }

    fun onStartChange(value: LocalTime) {
        _state.update { s ->
            val end = if (s.timeInputMode == TimeInputMode.Duration) {
                value.plusSeconds(s.durationSeconds.toLong())
            } else {
                s.endTime
            }
            s.copy(startTime = value, endTime = end, suggestedStart = value)
        }
    }

    fun onEndChange(value: LocalTime) = _state.update { it.copy(endTime = value) }

    fun onDurationHoursChange(hours: Int) {
        setDurationSeconds(hours.coerceIn(0, 24) * 3600 + _state.value.durationMinutes * 60)
    }

    fun onDurationMinutesChange(minutes: Int) {
        setDurationSeconds(_state.value.durationHours * 3600 + minutes.coerceIn(0, 59) * 60)
    }

    fun addDuration(hours: Int = 0, minutes: Int = 0) {
        val addSecs = hours * 3600 + minutes * 60
        if (addSecs <= 0) return
        setDurationSeconds(_state.value.durationSeconds + addSecs)
    }

    fun fillRemainingTarget() {
        val remaining = _state.value.remainingTargetSeconds
        if (remaining >= 60) setDurationSeconds(remaining)
    }

    fun resetDurationTo(hours: Int = 0, minutes: Int = 0) {
        setDurationSeconds(hours * 3600 + minutes * 60)
    }

    private fun setDurationSeconds(totalSeconds: Int) {
        val capped = totalSeconds.coerceIn(0, 24 * 3600)
        val h = capped / 3600
        val m = (capped % 3600) / 60
        _state.update { s ->
            s.copy(
                durationHours = h,
                durationMinutes = m,
                endTime = s.startTime.plusSeconds(capped.toLong()),
            )
        }
    }

    fun setTimeInputMode(mode: TimeInputMode) {
        _state.update { s ->
            when (mode) {
                TimeInputMode.Duration -> {
                    val secs = DateTimeFormats.secondsBetween(s.startTime, s.endTime)
                    s.copy(
                        timeInputMode = mode,
                        durationHours = secs / 3600,
                        durationMinutes = (secs % 3600) / 60,
                    )
                }
                TimeInputMode.Interval -> {
                    val secs = s.durationSeconds
                    s.copy(
                        timeInputMode = mode,
                        endTime = s.startTime.plusSeconds(secs.toLong()),
                    )
                }
            }
        }
    }

    fun onAttributeChange(key: String, value: String) {
        _state.update {
            it.copy(attributeValues = it.attributeValues + (key to value))
        }
    }

    private suspend fun refreshSuggestedStart(date: LocalDate) {
        val settings = userPreferences.settings.first()
        val fallbackStart = LocalTime.of(settings.defaultDayStartHour, settings.defaultDayStartMinute)
        val dayLogs = worklogRepository.getWorklogs(date, date).getOrDefault(emptyList())
        val logged = dayLogs.sumOf { it.timeSpentSeconds }
        val remaining = (settings.dailyTargetHours * 3600 - logged).coerceAtLeast(0)
        val suggested = dayLogs.maxByOrNull { it.end }?.end?.toLocalTime() ?: fallbackStart
        _state.update { s ->
            if (s.timeInputMode == TimeInputMode.Duration && s.editingId == null) {
                s.copy(
                    suggestedStart = suggested,
                    startTime = suggested,
                    endTime = suggested.plusSeconds(s.durationSeconds.toLong()),
                    remainingTargetSeconds = remaining,
                )
            } else {
                s.copy(suggestedStart = suggested, remainingTargetSeconds = remaining)
            }
        }
    }

    fun resolveAndRememberIssue() {
        val key = _state.value.issueKey.trim()
        if (key.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(resolvingIssue = true, error = null) }
            val result = if (_state.value.pinAsDefault) {
                issueRepository.setDefaultIssue(key)
            } else {
                issueRepository.resolveIssue(key).also { r ->
                    r.getOrNull()?.let { userPreferences.rememberIssue(it.key, it.summary) }
                }
            }
            _state.update {
                if (result.isSuccess) {
                    val issue = result.getOrThrow()
                    it.copy(
                        resolvingIssue = false,
                        issueKey = issue.key,
                        issueSummary = issue.summary,
                        issueSuggestions = emptyList(),
                    )
                } else {
                    it.copy(resolvingIssue = false, error = result.exceptionOrNull()?.message)
                }
            }
        }
    }

    fun save() {
        val s = _state.value
        viewModelScope.launch {
            if (s.issueKey.isBlank()) {
                _state.update { it.copy(error = "Укажите задачу Jira") }
                return@launch
            }
            val duration = s.durationSeconds
            if (duration < 60) {
                _state.update { it.copy(error = "Длительность должна быть хотя бы 1 минута") }
                return@launch
            }
            val missing = s.attributes.filter { it.required && s.attributeValues[it.key].isNullOrBlank() }
            if (missing.isNotEmpty()) {
                _state.update {
                    it.copy(error = "Заполните обязательные поля: ${missing.joinToString { a -> a.name }}")
                }
                return@launch
            }
            _state.update { it.copy(saving = true, error = null) }

            val started = LocalDateTime.of(s.date, s.startTime)
            val attrs = s.attributeValues.filterValues { it.isNotBlank() }

            val resolved = issueRepository.resolveIssue(s.issueKey.trim())
            if (resolved.isFailure) {
                _state.update {
                    it.copy(saving = false, error = resolved.exceptionOrNull()?.message)
                }
                return@launch
            }
            val issue = resolved.getOrThrow()
            if (s.pinAsDefault) {
                userPreferences.setDefaultIssue(issue.key, issue.summary)
            } else {
                userPreferences.rememberIssue(issue.key, issue.summary)
            }
            userPreferences.saveAttributeValues(attrs)

            val result = if (s.editingId != null) {
                worklogRepository.update(
                    id = s.editingId,
                    issueKeyOrId = issue.key,
                    started = started,
                    timeSpentSeconds = duration,
                    comment = s.comment.trim(),
                    attributes = attrs,
                )
            } else {
                worklogRepository.create(
                    issueKeyOrId = issue.key,
                    started = started,
                    timeSpentSeconds = duration,
                    comment = s.comment.trim(),
                    attributes = attrs,
                )
            }
            if (result.isSuccess && timerPrefill) {
                userPreferences.clearTimer()
            }
            _state.update {
                if (result.isSuccess) {
                    it.copy(saving = false, done = true)
                } else {
                    it.copy(saving = false, error = result.exceptionOrNull()?.message)
                }
            }
        }
    }
}
