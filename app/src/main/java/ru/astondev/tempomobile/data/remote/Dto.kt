package ru.astondev.tempomobile.data.remote

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class MyselfDto(
    val name: String? = null,
    val key: String? = null,
    val displayName: String? = null,
    val emailAddress: String? = null,
    @SerialName("accountId") val accountId: String? = null,
)

@Serializable
data class IssueDto(
    val id: String,
    val key: String,
    val fields: IssueFieldsDto? = null,
)

@Serializable
data class IssueFieldsDto(
    val summary: String? = null,
    val timetracking: TimeTrackingDto? = null,
)

@Serializable
data class TimeTrackingDto(
    val originalEstimate: String? = null,
    val remainingEstimate: String? = null,
    val timeSpent: String? = null,
    val originalEstimateSeconds: Int? = null,
    val remainingEstimateSeconds: Int? = null,
    val timeSpentSeconds: Int? = null,
)

@Serializable
data class TempoWorklogDto(
    val tempoWorklogId: Long? = null,
    val originId: Long? = null,
    val id: Long? = null,
    val timeSpentSeconds: Int? = null,
    val billableSeconds: Int? = null,
    val started: String? = null,
    val startDate: String? = null,
    val dateCreated: String? = null,
    val dateUpdated: String? = null,
    val comment: String? = null,
    val worker: JsonElement? = null,
    val workerKey: String? = null,
    val updater: String? = null,
    val originTaskId: JsonElement? = null,
    val issue: TempoIssueDto? = null,
    val attributes: JsonObject? = null,
) {
    fun worklogId(): String =
        (tempoWorklogId ?: originId ?: id)?.toString()
            ?: error("Worklog without id")

    fun startedRaw(): String? = started ?: startDate

    fun workerString(): String? {
        workerKey?.takeIf { it.isNotBlank() }?.let { return it }
        val w = worker ?: return null
        return when (w) {
            is JsonPrimitive -> w.contentOrNull
            is JsonObject -> w["name"]?.jsonPrimitive?.contentOrNull
                ?: w["key"]?.jsonPrimitive?.contentOrNull
                ?: w["workerKey"]?.jsonPrimitive?.contentOrNull
                ?: w["value"]?.jsonPrimitive?.contentOrNull
            else -> null
        }
    }

    fun issueKeyOrId(): String? =
        issue?.key
            ?: issue?.id?.toString()
            ?: originTaskId?.asStringOrNull()

    fun attributeMap(): Map<String, String> {
        val attrs = attributes ?: return emptyMap()
        return attrs.mapNotNull { (key, value) ->
            val extracted = when (value) {
                is JsonPrimitive -> value.contentOrNull
                is JsonObject -> value["value"]?.jsonPrimitive?.contentOrNull
                    ?: value["name"]?.jsonPrimitive?.contentOrNull
                else -> null
            }
            if (extracted != null) key to extracted else null
        }.toMap()
    }
}

@Serializable
data class TempoIssueDto(
    val id: Long? = null,
    val key: String? = null,
    val summary: String? = null,
    @SerialName("issueType") val issueType: String? = null,
    @SerialName("projectId") val projectId: Long? = null,
    @SerialName("projectKey") val projectKey: String? = null,
)

@Serializable
data class WorklogSearchRequest(
    val from: String,
    val to: String,
    val worker: List<String>? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CreateWorklogRequest(
    val originTaskId: String,
    val started: String,
    val timeSpentSeconds: Int,
    val comment: String? = null,
    val worker: String,
    val billableSeconds: Int? = null,
    /** Required by this Tempo DC instance; must be present even when 0. */
    @EncodeDefault
    val remainingEstimate: Int = 0,
    val attributes: Map<String, AttributeValueDto>? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class UpdateWorklogRequest(
    val originTaskId: String? = null,
    val started: String? = null,
    val timeSpentSeconds: Int? = null,
    val comment: String? = null,
    val worker: String? = null,
    val billableSeconds: Int? = null,
    @EncodeDefault
    val remainingEstimate: Int = 0,
    val attributes: Map<String, AttributeValueDto>? = null,
)

@Serializable
data class AttributeValueDto(
    val value: String,
)

@Serializable
data class WorkAttributeDto(
    val key: String? = null,
    val name: String? = null,
    val type: String? = null,
    val required: Boolean? = null,
    val values: List<String>? = null,
    @SerialName("staticValues") val staticValues: List<String>? = null,
)

private fun JsonElement.asStringOrNull(): String? = when (this) {
    is JsonPrimitive -> contentOrNull
    else -> null
}
