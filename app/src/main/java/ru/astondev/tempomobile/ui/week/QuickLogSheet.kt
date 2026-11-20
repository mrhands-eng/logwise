package ru.astondev.tempomobile.ui.week

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.astondev.tempomobile.data.local.UserPreferences
import ru.astondev.tempomobile.data.repo.IssueRepository
import ru.astondev.tempomobile.data.repo.WorklogRepository
import ru.astondev.tempomobile.domain.IssueRef
import ru.astondev.tempomobile.ui.theme.TempoDimens
import ru.astondev.tempomobile.util.DateTimeFormats
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

data class QuickLogUiState(
    val issueKey: String = "",
    val issueSummary: String = "",
    val recentIssues: List<IssueRef> = emptyList(),
    val durationMinutes: Int = 60,
    val comment: String = "",
    val saving: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class QuickLogViewModel @Inject constructor(
    private val worklogRepository: WorklogRepository,
    private val issueRepository: IssueRepository,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(QuickLogUiState())
    val state = _state.asStateFlow()

    fun prepare(date: LocalDate) {
        viewModelScope.launch {
            val default = issueRepository.defaultIssue.first()
            val recent = userPreferences.recentIssues.first()
            val settings = userPreferences.settings.first()
            val dayLogs = worklogRepository.getWorklogs(date, date).getOrDefault(emptyList())
            val remaining = (settings.dailyTargetHours * 3600 - dayLogs.sumOf { it.timeSpentSeconds })
                .coerceAtLeast(0)
            val defaultMins = when {
                remaining >= 3600 -> 60
                remaining >= 1800 -> 30
                remaining >= 60 -> (remaining / 60).coerceAtLeast(1)
                else -> 60
            }
            _state.update {
                it.copy(
                    issueKey = default?.key.orEmpty(),
                    issueSummary = default?.summary.orEmpty(),
                    recentIssues = recent,
                    durationMinutes = defaultMins,
                    comment = "",
                    saving = false,
                    error = null,
                    done = false,
                )
            }
        }
    }

    fun selectIssue(issue: IssueRef) {
        _state.update { it.copy(issueKey = issue.key, issueSummary = issue.summary, error = null) }
    }

    fun setDurationMinutes(minutes: Int) {
        _state.update { it.copy(durationMinutes = minutes.coerceIn(1, 24 * 60)) }
    }

    fun onCommentChange(value: String) = _state.update { it.copy(comment = value) }

    fun save(date: LocalDate) {
        viewModelScope.launch {
            val s = _state.value
            if (s.issueKey.isBlank()) {
                _state.update { it.copy(error = "Выберите задачу") }
                return@launch
            }
            _state.update { it.copy(saving = true, error = null) }
            val settings = userPreferences.settings.first()
            val dayStart = LocalTime.of(settings.defaultDayStartHour, settings.defaultDayStartMinute)
            val dayLogs = worklogRepository.getWorklogs(date, date).getOrDefault(emptyList())
            val start = dayLogs.maxByOrNull { it.end }?.end?.toLocalTime() ?: dayStart
            val remembered = userPreferences.lastAttributeValues.first()
            val attrsResult = worklogRepository.getWorkAttributes()
            val attrs = attrsResult.getOrDefault(emptyList())
            val attrValues = attrs.associate { a ->
                a.key to (remembered[a.key] ?: a.values.firstOrNull()?.takeIf { a.required }.orEmpty())
            }.filterValues { it.isNotBlank() }

            val resolved = issueRepository.resolveIssue(s.issueKey.trim())
            if (resolved.isFailure) {
                _state.update {
                    it.copy(saving = false, error = resolved.exceptionOrNull()?.message)
                }
                return@launch
            }
            val issue = resolved.getOrThrow()
            userPreferences.rememberIssue(issue.key, issue.summary)

            val result = worklogRepository.create(
                issueKeyOrId = issue.key,
                started = LocalDateTime.of(date, start),
                timeSpentSeconds = s.durationMinutes * 60,
                comment = s.comment.trim(),
                attributes = attrValues,
            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickLogSheet(
    date: LocalDate,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    viewModel: QuickLogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    LaunchedEffect(date) {
        viewModel.prepare(date)
    }
    LaunchedEffect(state.done) {
        if (state.done) onSaved()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(TempoDimens.screenPadding)) {
            Text("Быстрый лог", style = MaterialTheme.typography.titleLarge)
            Text(
                date.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            if (state.recentIssues.isNotEmpty() || state.issueKey.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.recentIssues.take(5).forEach { issue ->
                        FilterChip(
                            selected = state.issueKey.equals(issue.key, true),
                            onClick = { viewModel.selectIssue(issue) },
                            label = { Text(issue.key) },
                        )
                    }
                }
            }
            if (state.issueSummary.isNotBlank()) {
                Text(state.issueSummary, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(12.dp))
            Text("Длительность", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 60, 120, 240).forEach { mins ->
                    FilterChip(
                        selected = state.durationMinutes == mins,
                        onClick = { viewModel.setDurationMinutes(mins) },
                        label = { Text(DateTimeFormats.formatDuration(mins * 60)) },
                    )
                }
            }
            Text(
                "Выбрано: ${DateTimeFormats.formatDuration(state.durationMinutes * 60)}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.comment,
                onValueChange = viewModel::onCommentChange,
                label = { Text("Комментарий (необязательно)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            if (state.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.save(date) },
                enabled = !state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Сохранить")
                }
            }
            TextButton(
                onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Отмена")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
