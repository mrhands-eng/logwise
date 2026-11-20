package ru.astondev.tempomobile.ui.timer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.astondev.tempomobile.data.local.TimerPrefs
import ru.astondev.tempomobile.data.local.UserPreferences
import ru.astondev.tempomobile.data.repo.IssueRepository
import ru.astondev.tempomobile.domain.IssueRef
import ru.astondev.tempomobile.util.DateTimeFormats
import javax.inject.Inject

data class TimerUiState(
    val running: TimerPrefs? = null,
    val recentIssues: List<IssueRef> = emptyList(),
    val defaultIssue: IssueRef? = null,
)

@HiltViewModel
class TimerViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    issueRepository: IssueRepository,
) : ViewModel() {

    val state: StateFlow<TimerUiState> = combine(
        userPreferences.timerState,
        userPreferences.recentIssues,
        issueRepository.defaultIssue,
    ) { timer, recent, default ->
        TimerUiState(running = timer, recentIssues = recent, defaultIssue = default)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimerUiState())

    fun start(issue: IssueRef, onStarted: () -> Unit) {
        viewModelScope.launch {
            userPreferences.startTimer(issue.key, issue.summary)
            onStarted()
        }
    }

    fun clear() {
        viewModelScope.launch { userPreferences.clearTimer() }
    }
}

@Composable
fun TimerDialog(
    onDismiss: () -> Unit,
    onStopAndLog: () -> Unit,
    viewModel: TimerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* continue anyway */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.running) {
        while (state.running != null) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val running = state.running
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (running != null) "Таймер идёт" else "Таймер") },
        text = {
            Column {
                if (running != null) {
                    val elapsed = ((now - running.startedAtEpochMs) / 1000).toInt().coerceAtLeast(0)
                    Text(
                        DateTimeFormats.formatDuration(elapsed),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        listOfNotNull(running.issueKey, running.issueSummary.takeIf { it.isNotBlank() })
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Остановите таймер, чтобы открыть форму лога с prefilla.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Выберите задачу и запустите учёт времени.")
                    Spacer(Modifier.height(12.dp))
                    val options = buildList {
                        state.defaultIssue?.let { add(it) }
                        addAll(state.recentIssues)
                    }.distinctBy { it.key }.take(6)
                    if (options.isEmpty()) {
                        Text(
                            "Сначала задайте задачу по умолчанию в настройках или залогируйте время.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // chips wrap poorly in Row; show first few
                        }
                        options.forEach { issue ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    viewModel.start(issue) {
                                        TimerService.start(context, issue.key, issue.summary)
                                        onDismiss()
                                    }
                                },
                                label = { Text(issue.key) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (running != null) {
                TextButton(onClick = {
                    TimerService.stop(context)
                    onStopAndLog()
                }) { Text("Стоп и лог") }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (running != null) {
                    TimerService.stop(context)
                    viewModel.clear()
                }
                onDismiss()
            }) {
                Text(if (running != null) "Сбросить" else "Закрыть")
            }
        },
    )
}
