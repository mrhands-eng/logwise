package ru.astondev.tempomobile.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface JiraApi {
    @GET("rest/api/2/myself")
    suspend fun myself(): MyselfDto

    @GET("rest/api/2/issue/{issueKey}")
    suspend fun getIssue(
        @Path("issueKey") issueKey: String,
        @Query("fields") fields: String = "summary",
    ): IssueDto

    @GET("rest/api/2/search")
    suspend fun searchIssues(
        @Query("jql") jql: String,
        @Query("maxResults") maxResults: Int = 20,
        @Query("fields") fields: String = "summary",
    ): SearchResultDto
}

@kotlinx.serialization.Serializable
data class SearchResultDto(
    val issues: List<IssueDto> = emptyList(),
)
