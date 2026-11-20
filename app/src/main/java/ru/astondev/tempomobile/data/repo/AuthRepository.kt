package ru.astondev.tempomobile.data.repo

import ru.astondev.tempomobile.data.local.SecureCredentialsStore
import ru.astondev.tempomobile.data.local.UserPreferences
import ru.astondev.tempomobile.data.remote.AuthInterceptor
import ru.astondev.tempomobile.data.remote.DynamicBaseUrlInterceptor
import ru.astondev.tempomobile.data.remote.JiraApi
import ru.astondev.tempomobile.domain.Credentials
import ru.astondev.tempomobile.domain.UserProfile
import ru.astondev.tempomobile.util.AppLogger
import ru.astondev.tempomobile.util.UrlUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val jiraApi: JiraApi,
    private val credentialsStore: SecureCredentialsStore,
    private val userPreferences: UserPreferences,
    private val authInterceptor: AuthInterceptor,
    private val baseUrlInterceptor: DynamicBaseUrlInterceptor,
    private val appLogger: AppLogger,
) {
    fun isLoggedIn(): Boolean = credentialsStore.isLoggedIn()

    fun currentCredentials(): Credentials? = credentialsStore.read()

    suspend fun login(baseUrlRaw: String, username: String, password: String): Result<UserProfile> {
        return try {
            val baseUrl = UrlUtils.normalizeBaseUrl(baseUrlRaw)
            require(username.isNotBlank()) { "Введите логин" }
            require(password.isNotBlank()) { "Введите пароль" }

            baseUrlInterceptor.setOverrideBaseUrl(baseUrl)
            authInterceptor.setOverride(username, password)

            val myself = jiraApi.myself()
            val jiraName = myself.name ?: myself.key ?: username
            val profile = UserProfile(
                name = jiraName,
                displayName = myself.displayName ?: jiraName,
                emailAddress = myself.emailAddress,
                key = myself.key,
            )

            credentialsStore.save(
                Credentials(
                    baseUrl = baseUrl,
                    authUsername = username.trim(),
                    password = password,
                    jiraName = jiraName,
                    jiraKey = myself.key,
                    email = myself.emailAddress,
                )
            )
            appLogger.i(
                "Auth",
                "Login OK baseUrl=$baseUrl jiraName=$jiraName key=${myself.key} email=${myself.emailAddress}",
            )
            authInterceptor.clearOverride()
            baseUrlInterceptor.setOverrideBaseUrl(null)
            Result.success(profile)
        } catch (t: Throwable) {
            appLogger.e("Auth", "Login failed: ${t.message}", t)
            authInterceptor.clearOverride()
            baseUrlInterceptor.setOverrideBaseUrl(null)
            Result.failure(Exception(NetworkErrorMapper.toMessage(t), t))
        }
    }

    suspend fun logout() {
        appLogger.i("Auth", "Logout")
        credentialsStore.clear()
        userPreferences.clearSession()
        authInterceptor.clearOverride()
        baseUrlInterceptor.setOverrideBaseUrl(null)
    }
}
