package ru.astondev.tempomobile.data.repo

import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object NetworkErrorMapper {
    fun toMessage(throwable: Throwable): String = when (throwable) {
        is UnknownHostException ->
            "Нет связи с Jira (проверьте VPN и URL)"
        is SocketTimeoutException ->
            "Таймаут соединения с Jira (проверьте VPN)"
        is IOException ->
            "Нет связи с Jira (проверьте VPN и сеть)"
        is HttpException -> httpMessage(throwable)
        is IllegalArgumentException -> throwable.message ?: "Некорректные данные"
        else -> throwable.message ?: "Неизвестная ошибка"
    }

    private fun httpMessage(error: HttpException): String {
        val body = try {
            error.response()?.errorBody()?.string().orEmpty()
        } catch (_: Exception) {
            ""
        }
        val fromBody = parseTempoErrors(body)
        return when {
            fromBody != null -> fromBody
            error.code() == 401 || error.code() == 403 -> "Неверный логин или пароль"
            error.code() == 404 -> "Ресурс не найден. Проверьте URL Jira и плагин Tempo"
            error.code() == 405 -> "Метод API не поддерживается сервером Tempo (${error.code()})"
            else -> "Ошибка сервера (${error.code()})"
        }
    }

    private fun parseTempoErrors(body: String): String? {
        if (body.isBlank()) return null
        return try {
            val json = JSONObject(body)
            val errors = json.optJSONObject("errors")
            val parts = mutableListOf<String>()
            if (errors != null) {
                val keys = errors.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    parts += "$key: ${errors.optString(key)}"
                }
            }
            val messages = json.optJSONArray("errorMessages")
            if (messages != null) {
                for (i in 0 until messages.length()) {
                    parts += messages.optString(i)
                }
            }
            parts.takeIf { it.isNotEmpty() }?.joinToString("; ")
        } catch (_: Exception) {
            null
        }
    }
}
