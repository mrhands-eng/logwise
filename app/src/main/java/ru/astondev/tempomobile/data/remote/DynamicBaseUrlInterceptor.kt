package ru.astondev.tempomobile.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import ru.astondev.tempomobile.data.local.SecureCredentialsStore
import ru.astondev.tempomobile.util.UrlUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DynamicBaseUrlInterceptor @Inject constructor(
    private val credentialsStore: SecureCredentialsStore,
) : Interceptor {
    @Volatile
    private var overrideBaseUrl: String? = null

    fun setOverrideBaseUrl(url: String?) {
        overrideBaseUrl = url?.let { UrlUtils.normalizeBaseUrl(it) }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val base = overrideBaseUrl
            ?: credentialsStore.read()?.baseUrl
            ?: return chain.proceed(original)

        val baseHttpUrl = base.toHttpUrlOrNull()
            ?: return chain.proceed(original)

        val newUrl = original.url.newBuilder()
            .scheme(baseHttpUrl.scheme)
            .host(baseHttpUrl.host)
            .port(baseHttpUrl.port)
            .build()

        // Preserve path prefix if Jira is under a context path (e.g. /jira)
        val contextPath = baseHttpUrl.encodedPath.trimEnd('/')
        val rebuilt = if (contextPath.isNotEmpty() && contextPath != "/") {
            val path = original.url.encodedPath
            val withContext = if (path.startsWith(contextPath)) {
                path
            } else {
                contextPath + path
            }
            newUrl.newBuilder().encodedPath(withContext).build()
        } else {
            newUrl
        }

        return chain.proceed(original.newBuilder().url(rebuilt).build())
    }
}
