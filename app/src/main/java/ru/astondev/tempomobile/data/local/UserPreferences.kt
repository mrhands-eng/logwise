package ru.astondev.tempomobile.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.astondev.tempomobile.domain.AppPalette
import ru.astondev.tempomobile.domain.AppSettings
import ru.astondev.tempomobile.domain.IssueRef
import ru.astondev.tempomobile.domain.ThemeMode
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tempo_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val defaultIssueKey: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_ISSUE]?.takeIf { it.isNotBlank() }
    }

    val defaultIssueSummary: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_ISSUE_SUMMARY]?.takeIf { it.isNotBlank() }
    }

    val recentIssues: Flow<List<IssueRef>> = context.dataStore.data.map { prefs ->
        prefs[KEY_RECENT_ISSUES]?.let { raw ->
            runCatching { json.decodeFromString<List<StoredIssue>>(raw) }.getOrNull()
                ?.map { IssueRef(id = it.key, key = it.key, summary = it.summary) }
        }.orEmpty()
    }

    val lastAttributeValues: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_ATTRIBUTE_VALUES]?.let { raw ->
            runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrNull()
        }.orEmpty()
    }

    val timerState: Flow<TimerPrefs?> = context.dataStore.data.map { prefs ->
        val startedAt = prefs[KEY_TIMER_STARTED_AT] ?: return@map null
        val key = prefs[KEY_TIMER_ISSUE_KEY] ?: return@map null
        TimerPrefs(
            startedAtEpochMs = startedAt,
            issueKey = key,
            issueSummary = prefs[KEY_TIMER_ISSUE_SUMMARY].orEmpty(),
        )
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[KEY_THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.System,
            palette = prefs[KEY_PALETTE]?.let { runCatching { AppPalette.valueOf(it) }.getOrNull() }
                ?: AppPalette.Forest,
            hideWeekends = prefs[KEY_HIDE_WEEKENDS] ?: true,
            dailyTargetHours = (prefs[KEY_DAILY_TARGET_HOURS] ?: 8).coerceIn(1, 24),
            defaultDayStartHour = (prefs[KEY_DAY_START_HOUR] ?: 9).coerceIn(0, 23),
            defaultDayStartMinute = (prefs[KEY_DAY_START_MINUTE] ?: 0).coerceIn(0, 59),
        )
    }

    suspend fun setDefaultIssue(key: String, summary: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DEFAULT_ISSUE] = key
            prefs[KEY_DEFAULT_ISSUE_SUMMARY] = summary
        }
        rememberIssue(key, summary)
    }

    suspend fun rememberIssue(key: String, summary: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_RECENT_ISSUES]?.let {
                runCatching { json.decodeFromString<List<StoredIssue>>(it) }.getOrNull()
            }.orEmpty()
            val next = (listOf(StoredIssue(key, summary)) + current.filterNot { it.key.equals(key, true) })
                .take(8)
            prefs[KEY_RECENT_ISSUES] = json.encodeToString(next)
        }
    }

    suspend fun saveAttributeValues(values: Map<String, String>) {
        if (values.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_ATTRIBUTE_VALUES]?.let {
                runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull()
            }.orEmpty().toMutableMap()
            current.putAll(values.filterValues { it.isNotBlank() })
            prefs[KEY_ATTRIBUTE_VALUES] = json.encodeToString(current)
        }
    }

    suspend fun startTimer(issueKey: String, issueSummary: String, startedAtEpochMs: Long = System.currentTimeMillis()) {
        context.dataStore.edit {
            it[KEY_TIMER_STARTED_AT] = startedAtEpochMs
            it[KEY_TIMER_ISSUE_KEY] = issueKey
            it[KEY_TIMER_ISSUE_SUMMARY] = issueSummary
        }
    }

    suspend fun clearTimer() {
        context.dataStore.edit {
            it.remove(KEY_TIMER_STARTED_AT)
            it.remove(KEY_TIMER_ISSUE_KEY)
            it.remove(KEY_TIMER_ISSUE_SUMMARY)
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setPalette(palette: AppPalette) {
        context.dataStore.edit { it[KEY_PALETTE] = palette.name }
    }

    suspend fun setHideWeekends(hide: Boolean) {
        context.dataStore.edit { it[KEY_HIDE_WEEKENDS] = hide }
    }

    suspend fun setDailyTargetHours(hours: Int) {
        context.dataStore.edit { it[KEY_DAILY_TARGET_HOURS] = hours.coerceIn(1, 24) }
    }

    suspend fun setDefaultDayStart(hour: Int, minute: Int) {
        context.dataStore.edit {
            it[KEY_DAY_START_HOUR] = hour.coerceIn(0, 23)
            it[KEY_DAY_START_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    /** Clears only session-related prefs (issue default / recents / timer). Keeps theme/work settings. */
    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_DEFAULT_ISSUE)
            prefs.remove(KEY_DEFAULT_ISSUE_SUMMARY)
            prefs.remove(KEY_RECENT_ISSUES)
            prefs.remove(KEY_ATTRIBUTE_VALUES)
            prefs.remove(KEY_TIMER_STARTED_AT)
            prefs.remove(KEY_TIMER_ISSUE_KEY)
            prefs.remove(KEY_TIMER_ISSUE_SUMMARY)
        }
    }

    @Deprecated("Use clearSession()", ReplaceWith("clearSession()"))
    suspend fun clear() = clearSession()

    companion object {
        private val KEY_DEFAULT_ISSUE = stringPreferencesKey("default_issue_key")
        private val KEY_DEFAULT_ISSUE_SUMMARY = stringPreferencesKey("default_issue_summary")
        private val KEY_RECENT_ISSUES = stringPreferencesKey("recent_issues_json")
        private val KEY_ATTRIBUTE_VALUES = stringPreferencesKey("attribute_values_json")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_PALETTE = stringPreferencesKey("palette")
        private val KEY_HIDE_WEEKENDS = booleanPreferencesKey("hide_weekends")
        private val KEY_DAILY_TARGET_HOURS = intPreferencesKey("daily_target_hours")
        private val KEY_DAY_START_HOUR = intPreferencesKey("day_start_hour")
        private val KEY_DAY_START_MINUTE = intPreferencesKey("day_start_minute")
        private val KEY_TIMER_STARTED_AT = longPreferencesKey("timer_started_at")
        private val KEY_TIMER_ISSUE_KEY = stringPreferencesKey("timer_issue_key")
        private val KEY_TIMER_ISSUE_SUMMARY = stringPreferencesKey("timer_issue_summary")
    }
}

@kotlinx.serialization.Serializable
private data class StoredIssue(
    val key: String,
    val summary: String,
)

data class TimerPrefs(
    val startedAtEpochMs: Long,
    val issueKey: String,
    val issueSummary: String,
)
