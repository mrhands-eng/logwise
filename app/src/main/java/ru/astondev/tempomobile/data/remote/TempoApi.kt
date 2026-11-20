package ru.astondev.tempomobile.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface TempoApi {
    /** Tempo Server/DC: listing is POST search (GET /worklogs returns 405). */
    @POST("rest/tempo-timesheets/4/worklogs/search")
    suspend fun searchWorklogs(
        @Body body: WorklogSearchRequest,
    ): List<TempoWorklogDto>

    @POST("rest/tempo-timesheets/4/worklogs")
    suspend fun createWorklog(
        @Body body: CreateWorklogRequest,
    ): List<TempoWorklogDto>

    @PUT("rest/tempo-timesheets/4/worklogs/{id}")
    suspend fun updateWorklog(
        @Path("id") id: String,
        @Body body: UpdateWorklogRequest,
    ): TempoWorklogDto

    @DELETE("rest/tempo-timesheets/4/worklogs/{id}")
    suspend fun deleteWorklog(
        @Path("id") id: String,
    )

    @GET("rest/tempo-timesheets/4/work-attributes")
    suspend fun getWorkAttributes(): List<WorkAttributeDto>
}
