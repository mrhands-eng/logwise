package ru.astondev.tempomobile.data.repo

import ru.astondev.tempomobile.data.local.UserPreferences
import ru.astondev.tempomobile.data.remote.JiraApi
import ru.astondev.tempomobile.domain.IssueRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IssueRepository @Inject constructor(
    private val jiraApi: JiraApi,
    private val userPreferences: UserPreferences,
) {
    val defaultIssue: Flow<IssueRef?> = combine(
        userPreferences.defaultIssueKey,
        userPreferences.defaultIssueSummary,
    ) { key, summary ->
        if (key == null) null else IssueRef(id = key, key = key, summary = summary.orEmpty())
    }

    suspend fun resolveIssue(keyOrId: String): Result<IssueRef> = try {
        val issue = jiraApi.getIssue(keyOrId.trim())
        Result.success(
            IssueRef(
                id = issue.id,
                key = issue.key,
                summary = issue.fields?.summary.orEmpty(),
            )
        )
    } catch (t: Throwable) {
        Result.failure(Exception(NetworkErrorMapper.toMessage(t), t))
    }

    suspend fun setDefaultIssue(keyOrId: String): Result<IssueRef> {
        val resolved = resolveIssue(keyOrId)
        resolved.getOrNull()?.let {
            userPreferences.setDefaultIssue(it.key, it.summary)
        }
        return resolved
    }

    suspend fun searchByKeyFragment(query: String): Result<List<IssueRef>> {
        return try {
            val q = query.trim()
            if (q.isEmpty()) return Result.success(emptyList())
            val jql = if (q.matches(Regex("""[A-Za-z][A-Za-z0-9]+-\d+"""))) {
                """key = "$q""""
            } else {
                """text ~ "$q" ORDER BY updated DESC"""
            }
            val result = jiraApi.searchIssues(jql = jql, maxResults = 15)
            Result.success(
                result.issues.map {
                    IssueRef(
                        id = it.id,
                        key = it.key,
                        summary = it.fields?.summary.orEmpty(),
                    )
                }
            )
        } catch (t: Throwable) {
            Result.failure(Exception(NetworkErrorMapper.toMessage(t), t))
        }
    }
}
