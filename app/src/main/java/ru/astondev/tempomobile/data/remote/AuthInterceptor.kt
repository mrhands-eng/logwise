package ru.astondev.tempomobile.data.remote

import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Response
import ru.astondev.tempomobile.data.local.SecureCredentialsStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val credentialsStore: SecureCredentialsStore,
) : Interceptor {
    @Volatile
    private var overrideCredentials: Pair<String, String>? = null

    fun setOverride(username: String, password: String) {
        overrideCredentials = username to password
    }

    fun clearOverride() {
        overrideCredentials = null
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val pair = overrideCredentials
            ?: credentialsStore.read()?.let { it.authUsername to it.password }
        if (pair == null) {
            return chain.proceed(request)
        }
        val (username, password) = pair
        val authenticated = request.newBuilder()
            .header("Authorization", Credentials.basic(username, password))
            .header("X-Atlassian-Token", "no-check")
            .header("Accept", "application/json")
            .build()
        return chain.proceed(authenticated)
    }
}
