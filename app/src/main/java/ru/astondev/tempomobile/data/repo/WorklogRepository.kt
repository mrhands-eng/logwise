package ru.astondev.tempomobile.data.repo

import ru.astondev.tempomobile.data.local.SecureCredentialsStore
import ru.astondev.tempomobile.data.remote.AttributeValueDto
import ru.astondev.tempomobile.data.remote.CreateWorklogRequest
import ru.astondev.tempomobile.data.remote.JiraApi
import ru.astondev.tempomobile.data.remote.TempoApi
import ru.astondev.tempomobile.data.remote.TempoWorklogDto
import ru.astondev.tempomobile.data.remote.UpdateWorklogRequest
import ru.astondev.tempomobile.data.remote.WorklogSearchRequest
import ru.astondev.tempomobile.domain.WorkAttributeDef
import ru.astondev.tempomobile.domain.Worklog
import ru.astondev.tempomobile.util.AppLogger
import ru.astondev.tempomobile.util.DateTimeFormats
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorklogRepository @Inject constructor(
    private val tempoApi: TempoApi,
    private val jiraApi: JiraApi,
    private val credentialsStore: SecureCredentialsStore,
    private val appLogger: AppLogger,
) {
    @Volatile
    private var cache: Pair<Pair<LocalDate, LocalDate>, List<Worklog>>? = null

    fun invalidateCache() {
        cache = null
    }

    suspend fun getWorklogs(
        from: LocalDate,
        to: LocalDate,
        forceRefresh: Boolean = false,
    ): Result<List<Worklog>> = try {
        if (!forceRefresh) {
            cache?.let { (range, items) ->
                if (range.first == from && range.second == to) {
                    appLogger.i("Worklogs", "Cache hit $from..$to (${items.size})")
                    return Result.success(items)
                }
                // Serve subset if fully inside a cached week range
                if (!from.isBefore(range.first) && !to.isAfter(range.second)) {
                    val subset = items.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
                    appLogger.i("Worklogs", "Cache subset $from..$to (${subset.size})")
                    return Result.success(subset)
                }
            }
        }

        val creds = credentialsStore.read()
            ?: return Result.failure(Exception("Не авторизован"))
        val fromStr = from.format(DateTimeFormats.dateApi)
        val toStr = to.format(DateTimeFormats.dateApi)
        val aliases = creds.workerAliases()
        val aliasSet = aliases.map { it.lowercase() }.toSet()
        appLogger.i("Worklogs", "Search $fromStr..$toStr aliases=$aliases")

        // Prefer Jira user key first — that's what Tempo uses as worker on this instance.
        val workersForSearch = buildList {
            creds.jiraKey?.takeIf { it.isNotBlank() }?.let { add(it) }
            addAll(aliases)
        }.distinct()

        val raw = tempoApi.searchWorklogs(
            WorklogSearchRequest(
                from = fromStr,
                to = toStr,
                worker = workersForSearch,
            )
        )
        appLogger.i("Worklogs", "Search returned ${raw.size} items (workers=$workersForSearch)")

        // Keep only our aliases (search may be OR across workers; never show other users).
        val scoped = raw.filter { dto ->
            val w = dto.workerString()?.lowercase() ?: return@filter true
            aliasSet.isEmpty() || w in aliasSet
        }

        val mapped = scoped.mapNotNull { dto ->
            runCatching { dto.toDomain() }
                .onFailure { appLogger.w("Worklogs", "Skip worklog parse: ${it.message}", it) }
                .getOrNull()
        }.sortedBy { it.started }
        appLogger.i("Worklogs", "Mapped ${mapped.size} worklogs for UI")
        cache = (from to to) to mapped
        Result.success(mapped)
    } catch (t: Throwable) {
        appLogger.e("Worklogs", "getWorklogs failed: ${t.message}", t)
        Result.failure(Exception(NetworkErrorMapper.toMessage(t), t))
    }

    private fun TempoWorklogDto.toDomain(
        fallbackIssue: String? = null,
        fallbackComment: String = "",
        fallbackSeconds: Int? = null,
        fallbackStarted: String? = null,
        fallbackWorker: String? = null,
        fallbackAttrs: Map<String, String> = emptyMap(),
    ): Worklog = Worklog(
        id = worklogId(),
        issueKey = issue?.key ?: issueKeyOrId() ?: fallbackIssue,
        issueId = issue?.id?.toString(),
        issueSummary = issue?.summary,
        comment = comment ?: fallbackComment,
        started = DateTimeFormats.parseTempoStarted(startedRaw() ?: fallbackStarted),
        timeSpentSeconds = timeSpentSeconds ?: fallbackSeconds ?: 0,
        worker = workerString() ?: fallbackWorker,
        attributes = attributeMap().ifEmpty { fallbackAttrs },
    )

    suspend fun create(
        issueKeyOrId: String,
        started: LocalDateTime,
        timeSpentSeconds: Int,
        comment: String,
        attributes: Map<String, String> = emptyMap(),
    ): Result<Worklog> = try {
        val creds = credentialsStore.read()
            ?: return Result.failure(Exception("Не авторизован"))
        require(timeSpentSeconds > 0) { "Длительность должна быть больше 0" }
        val worker = creds.tempoWorker()
        val originTaskId = resolveOriginTaskId(issueKeyOrId)
        val remaining = fetchRemainingEstimateSeconds(originTaskId)
        val body = CreateWorklogRequest(
            originTaskId = originTaskId,
            started = DateTimeFormats.formatTempoStarted(started),
            timeSpentSeconds = timeSpentSeconds,
            comment = comment.ifBlank { null },
            worker = worker,
            billableSeconds = timeSpentSeconds,
            remainingEstimate = remaining,
            attributes = attributes.takeIf { it.isNotEmpty() }?.mapValues { AttributeValueDto(it.value) },
        )
        appLogger.i(
            "Worklogs",
            "Create worklog worker=$worker (jiraKey=${creds.jiraKey}) " +
                "originTaskId=$originTaskId started=${body.started} secs=$timeSpentSeconds remaining=$remaining",
        )
        val created = tempoApi.createWorklog(body)
        val dto = created.firstOrNull()
            ?: return Result.failure(Exception("Tempo не вернул созданную запись"))
        invalidateCache()
        Result.success(
            dto.toDomain(
                fallbackIssue = issueKeyOrId,
                fallbackComment = comment,
                fallbackSeconds = timeSpentSeconds,
                fallbackStarted = DateTimeFormats.formatTempoStarted(started),
                fallbackWorker = worker,
                fallbackAttrs = attributes,
            )
        )
    } catch (t: Throwable) {
        appLogger.e("Worklogs", "create failed: ${t.message}", t)
        Result.failure(Exception(NetworkErrorMapper.toMessage(t), t))
    }

    /** Tempo DC expects numeric issue id in originTaskId (e.g. 1504776), not always the key. */
    private suspend fun resolveOriginTaskId(issueKeyOrId: String): String {
        val trimmed = issueKeyOrId.trim()
        if (trimmed.all { it.isDigit() }) return trimmed
        return try {
            val issue = jiraApi.getIssue(trimmed)
            appLogger.i("Worklogs", "Resolved issue $trimmed -> id=${issue.id}")
            issue.id
        } catch (t: Throwable) {
            appLogger.w("Worklogs", "Could not resolve issue id for $trimmed, sending as-is", t)
            trimmed
        }
    }

    /**
     * This Tempo instance requires remainingEstimate on create.
     * Prefer current remaining from Jira timetracking; fall back to 0.
     */
    private suspend fun fetchRemainingEstimateSeconds(originTaskId: String): Int {
        return try {
            val issue = jiraApi.getIssue(originTaskId, fields = "timetracking")
            val remaining = issue.fields?.timetracking?.remainingEstimateSeconds
            appLogger.i("Worklogs", "Issue $originTaskId remainingEstimateSeconds=$remaining")
            remaining ?: 0
        } catch (t: Throwable) {
            appLogger.w("Worklogs", "Could not fetch remaining estimate for $originTaskId, using 0", t)
            0
        }
    }

    suspend fun update(
        id: String,
        issueKeyOrId: String,
        started: LocalDateTime,
        timeSpentSeconds: Int,
        comment: String,
        attributes: Map<String, String> = emptyMap(),
    ): Result<Worklog> = try {
        val creds = credentialsStore.read()
            ?: return Result.failure(Exception("Не авторизован"))
        require(timeSpentSeconds > 0) { "Длительность должна быть больше 0" }
        val worker = creds.tempoWorker()
        val originTaskId = resolveOriginTaskId(issueKeyOrId)
        val remaining = fetchRemainingEstimateSeconds(originTaskId)
        val body = UpdateWorklogRequest(
            originTaskId = originTaskId,
            started = DateTimeFormats.formatTempoStarted(started),
            timeSpentSeconds = timeSpentSeconds,
            comment = comment,
            worker = worker,
            billableSeconds = timeSpentSeconds,
            remainingEstimate = remaining,
            attributes = attributes.takeIf { it.isNotEmpty() }?.mapValues { AttributeValueDto(it.value) },
        )
        val dto = tempoApi.updateWorklog(id, body)
        invalidateCache()
        Result.success(
            dto.toDomain(
                fallbackIssue = issueKeyOrId,
                fallbackComment = comment,
                fallbackSeconds = timeSpentSeconds,
                fallbackStarted = DateTimeFormats.formatTempoStarted(started),
                fallbackWorker = worker,
                fallbackAttrs = attributes,
            )
        )
    } catch (t: Throwable) {
        appLogger.e("Worklogs", "update failed: ${t.message}", t)
        Result.failure(Exception(NetworkErrorMapper.toMessage(t), t))
    }

    suspend fun delete(id: String): Result<Unit> = try {
        tempoApi.deleteWorklog(id)
        invalidateCache()
        appLogger.i("Worklogs", "Deleted worklog id=$id")
        Result.success(Unit)
    } catch (t: Throwable) {
        appLogger.e("Worklogs", "delete failed: ${t.message}", t)
        Result.failure(Exception(NetworkErrorMapper.toMessage(t), t))
    }

    suspend fun getWorkAttributes(): Result<List<WorkAttributeDef>> = try {
        val attrs = tempoApi.getWorkAttributes().mapNotNull { dto ->
            val key = dto.key ?: return@mapNotNull null
            WorkAttributeDef(
                key = key,
                name = dto.name ?: key,
                type = dto.type ?: "string",
                required = dto.required == true,
                values = (dto.values ?: dto.staticValues).orEmpty(),
            )
        }
        Result.success(attrs)
    } catch (t: Throwable) {
        // This Jira DC build returns 404 for work-attributes; worklogs still work with empty attrs.
        val code = (t as? retrofit2.HttpException)?.code()
        if (code == 404 || code == 405) {
            appLogger.i("Worklogs", "Work attributes endpoint unavailable ($code) — treating as none")
            Result.success(emptyList())
        } else {
            appLogger.e("Worklogs", "getWorkAttributes failed: ${t.message}", t)
            Result.failure(Exception(NetworkErrorMapper.toMessage(t), t))
        }
    }

    suspend fun copyDay(from: LocalDate, to: LocalDate, worklogs: List<Worklog>): Result<Int> {
        var created = 0
        val errors = mutableListOf<String>()
        appLogger.i("Worklogs", "Copy day ${worklogs.size} entries from $from to $to")
        for (wl in worklogs) {
            // Prefer numeric issue id — Tempo create uses originTaskId as id on this instance.
            val issue = wl.issueId ?: wl.issueKey
            if (issue == null) {
                errors += "Нет задачи у записи ${wl.id}"
                continue
            }
            val started = LocalDateTime.of(to, wl.started.toLocalTime())
            val result = create(
                issueKeyOrId = issue,
                started = started,
                timeSpentSeconds = wl.timeSpentSeconds,
                comment = wl.comment,
                attributes = wl.attributes,
            )
            if (result.isSuccess) created++ else errors += result.exceptionOrNull()?.message.orEmpty()
        }
        return if (errors.isEmpty()) {
            Result.success(created)
        } else if (created > 0) {
            Result.failure(Exception("Скопировано $created, ошибки: ${errors.joinToString("; ")}"))
        } else {
            Result.failure(Exception(errors.joinToString("; ")))
        }
    }
}
