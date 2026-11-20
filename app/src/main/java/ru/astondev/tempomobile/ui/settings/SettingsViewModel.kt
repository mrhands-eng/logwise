package ru.astondev.tempomobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.astondev.tempomobile.BuildConfig
import ru.astondev.tempomobile.data.local.UserPreferences
import ru.astondev.tempomobile.data.repo.AuthRepository
import ru.astondev.tempomobile.data.repo.IssueRepository
import ru.astondev.tempomobile.domain.AppPalette
import ru.astondev.tempomobile.domain.IssueRef
import ru.astondev.tempomobile.domain.ThemeMode
import ru.astondev.tempomobile.util.AppLogger
import java.io.File
import javax.inject.Inject

data class SettingsUiState(
    val baseUrl: String = "",
    val username: String = "",
    val defaultIssueKey: String = "",
    val defaultIssueSummary: String = "",
    val issueInput: String = "",
    val savingIssue: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val loggedOut: Boolean = false,
    val logPath: String = "",
    val shareLogRequest: File? = null,
    val themeMode: ThemeMode = ThemeMode.System,
    val palette: AppPalette = AppPalette.Forest,
    val hideWeekends: Boolean = true,
    val dailyTargetHours: Int = 8,
    val dayStartHour: Int = 9,
    val dayStartMinute: Int = 0,
    val recentIssues: List<IssueRef> = emptyList(),
    val appVersion: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val issueRepository: IssueRepository,
    private val userPreferences: UserPreferences,
    private val appLogger: AppLogger,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(
            baseUrl = authRepository.currentCredentials()?.baseUrl.orEmpty(),
            username = authRepository.currentCredentials()?.jiraName.orEmpty(),
            logPath = appLogger.logFile.absolutePath,
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        )
    )

    val state: StateFlow<SettingsUiState> = combine(
        _state,
        issueRepository.defaultIssue,
        userPreferences.settings,
        userPreferences.recentIssues,
    ) { s, issue, settings, recent ->
        s.copy(
            defaultIssueKey = issue?.key.orEmpty(),
            defaultIssueSummary = issue?.summary.orEmpty(),
            issueInput = if (s.issueInput.isBlank() && issue != null) issue.key else s.issueInput,
            themeMode = settings.themeMode,
            palette = settings.palette,
            hideWeekends = settings.hideWeekends,
            dailyTargetHours = settings.dailyTargetHours,
            dayStartHour = settings.defaultDayStartHour,
            dayStartMinute = settings.defaultDayStartMinute,
            recentIssues = recent,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        _state.value,
    )

    fun onIssueInputChange(value: String) {
        _state.update { it.copy(issueInput = value, error = null, message = null) }
    }

    fun pickRecentIssue(issue: IssueRef) {
        _state.update { it.copy(issueInput = issue.key, error = null, message = null) }
    }

    fun saveDefaultIssue() {
        val key = _state.value.issueInput.trim()
        if (key.isEmpty()) {
            _state.update { it.copy(error = "Введите ключ задачи") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(savingIssue = true, error = null) }
            val result = issueRepository.setDefaultIssue(key)
            _state.update {
                if (result.isSuccess) {
                    val issue: IssueRef = result.getOrThrow()
                    it.copy(
                        savingIssue = false,
                        issueInput = issue.key,
                        message = "Задача ${issue.key} сохранена по умолчанию",
                    )
                } else {
                    it.copy(
                        savingIssue = false,
                        error = result.exceptionOrNull()?.message,
                    )
                }
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            userPreferences.setThemeMode(mode)
            _state.update { it.copy(message = "Тема: ${mode.label()}") }
        }
    }

    fun setPalette(palette: AppPalette) {
        viewModelScope.launch {
            userPreferences.setPalette(palette)
            _state.update { it.copy(message = "Палитра: ${palette.label()}") }
        }
    }

    fun setHideWeekends(hide: Boolean) {
        viewModelScope.launch {
            userPreferences.setHideWeekends(hide)
            _state.update {
                it.copy(message = if (hide) "Выходные скрыты" else "Показана вся неделя")
            }
        }
    }

    fun setDailyTargetHours(hours: Int) {
        viewModelScope.launch {
            userPreferences.setDailyTargetHours(hours)
            _state.update { it.copy(message = "Норма дня: ${hours}ч") }
        }
    }

    fun setDayStart(hour: Int, minute: Int = 0) {
        viewModelScope.launch {
            userPreferences.setDefaultDayStart(hour, minute)
            _state.update {
                it.copy(message = "Начало дня по умолчанию: %02d:%02d".format(hour, minute))
            }
        }
    }

    fun shareLogs() {
        appLogger.i("Settings", "Share logs requested")
        val file = appLogger.logFile
        if (!file.exists() || file.length() == 0L) {
            _state.update { it.copy(error = "Лог-файл пока пустой. Пользуйтесь приложением и повторите.") }
            return
        }
        _state.update { it.copy(shareLogRequest = file, error = null) }
    }

    fun onShareHandled() {
        _state.update { it.copy(shareLogRequest = null) }
    }

    fun clearLogs() {
        appLogger.clear()
        _state.update { it.copy(message = "Логи очищены", error = null) }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _state.update { it.copy(loggedOut = true) }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
    fun clearError() = _state.update { it.copy(error = null) }
}

fun ThemeMode.label(): String = when (this) {
    ThemeMode.System -> "Системная"
    ThemeMode.Light -> "Светлая"
    ThemeMode.Dark -> "Тёмная"
}

fun AppPalette.label(): String = when (this) {
    AppPalette.Forest -> "Лес"
    AppPalette.Ocean -> "Океан"
    AppPalette.Slate -> "Сланец"
    AppPalette.Ember -> "Янтарь"
}
