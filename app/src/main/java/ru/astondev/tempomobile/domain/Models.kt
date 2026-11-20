package ru.astondev.tempomobile.domain

import java.time.LocalDate
import java.time.LocalDateTime

data class UserProfile(
    val name: String,
    val displayName: String,
    val emailAddress: String?,
    val key: String? = null,
)

data class IssueRef(
    val id: String,
    val key: String,
    val summary: String,
)

data class Worklog(
    val id: String,
    val issueKey: String?,
    val issueId: String?,
    val issueSummary: String?,
    val comment: String,
    val started: LocalDateTime,
    val timeSpentSeconds: Int,
    val worker: String?,
    val attributes: Map<String, String> = emptyMap(),
) {
    val date: LocalDate get() = started.toLocalDate()
    val end: LocalDateTime get() = started.plusSeconds(timeSpentSeconds.toLong())
}

data class WorkAttributeDef(
    val key: String,
    val name: String,
    val type: String,
    val required: Boolean,
    val values: List<String> = emptyList(),
)

data class DaySummary(
    val date: LocalDate,
    val loggedSeconds: Int,
    val targetSeconds: Int = 8 * 3600,
) {
    val progress: Float
        get() = if (targetSeconds <= 0) 0f else (loggedSeconds.toFloat() / targetSeconds).coerceAtMost(1.5f)
}

data class Credentials(
    val baseUrl: String,
    /** Value used for HTTP Basic Auth (what the user typed to log in). */
    val authUsername: String,
    val password: String,
    /** Jira user name (often used by Tempo as worker). */
    val jiraName: String,
    val jiraKey: String? = null,
    val email: String? = null,
) {
    /** Aliases to try when filtering Tempo worklogs by worker. */
    fun workerAliases(): List<String> =
        listOfNotNull(jiraName, jiraKey, email, authUsername)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }

    /**
     * Preferred Tempo worker for create/update.
     * On this Jira DC instance Tempo stores/validates worker as user key (e.g. JIRAUSER48848),
     * not the login name (m.yudzitski).
     */
    fun tempoWorker(): String =
        jiraKey?.takeIf { it.isNotBlank() }
            ?: jiraName.ifBlank { authUsername }
}
