package ru.astondev.tempomobile.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import ru.astondev.tempomobile.util.AppLogger
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Logs HTTP request/response to the app log file (Authorization redacted by AppLogger).
 * Response body is consumed and re-wrapped so callers still receive it.
 */
@Singleton
class FileHttpLogger @Inject constructor(
    private val appLogger: AppLogger,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestBody = request.body
        val requestBodyText = if (requestBody != null) {
            try {
                val buffer = Buffer()
                requestBody.writeTo(buffer)
                val charset: Charset = requestBody.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                truncate(buffer.readString(charset))
            } catch (t: Exception) {
                "<body read error: ${t.message}>"
            }
        } else {
            null
        }

        appLogger.d(
            TAG,
            "--> ${request.method} ${request.url}" +
                (requestBodyText?.let { "\nreq-body:\n$it" } ?: ""),
        )

        val startNs = System.nanoTime()
        val response: Response = try {
            chain.proceed(request)
        } catch (t: Exception) {
            appLogger.e(TAG, "<-- HTTP FAILED ${request.method} ${request.url}: ${t.message}", t)
            throw t
        }
        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)

        val rawBody = response.body
        val contentType = rawBody?.contentType()
        val bodyString = try {
            rawBody?.string().orEmpty()
        } catch (t: Exception) {
            appLogger.w(TAG, "Failed to read response body: ${t.message}")
            ""
        }

        appLogger.d(
            TAG,
            "<-- ${response.code} ${request.method} ${request.url} (${tookMs}ms)\n" +
                "resp-body(${bodyString.length}):\n${truncate(bodyString)}",
        )

        return response.newBuilder()
            .body(bodyString.toResponseBody(contentType))
            .build()
    }

    private fun truncate(text: String): String {
        if (text.length <= MAX_BODY) return text
        return text.take(MAX_BODY) + "\n… [truncated ${text.length - MAX_BODY} chars]"
    }

    companion object {
        private const val TAG = "HTTP"
        private const val MAX_BODY = 8_000
    }
}
