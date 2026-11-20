package ru.astondev.tempomobile.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import ru.astondev.tempomobile.data.remote.AuthInterceptor
import ru.astondev.tempomobile.data.remote.DynamicBaseUrlInterceptor
import ru.astondev.tempomobile.data.remote.FileHttpLogger
import ru.astondev.tempomobile.data.remote.JiraApi
import ru.astondev.tempomobile.data.remote.TempoApi
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttp(
        authInterceptor: AuthInterceptor,
        baseUrlInterceptor: DynamicBaseUrlInterceptor,
        fileHttpLogger: FileHttpLogger,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(baseUrlInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(fileHttpLogger)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            // Placeholder; DynamicBaseUrlInterceptor rewrites host/path
            .baseUrl("https://placeholder.invalid/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideJiraApi(retrofit: Retrofit): JiraApi = retrofit.create(JiraApi::class.java)

    @Provides
    @Singleton
    fun provideTempoApi(retrofit: Retrofit): TempoApi = retrofit.create(TempoApi::class.java)
}
