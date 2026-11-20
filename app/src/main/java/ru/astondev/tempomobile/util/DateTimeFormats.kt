package ru.astondev.tempomobile.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

object DateTimeFormats {
    private val tempoStartedFormats = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXX"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
    )

    val dateApi: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    val timeUi: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val dayShort: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    val dayNum: DateTimeFormatter = DateTimeFormatter.ofPattern("d")
    val weekRange: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

    fun parseTempoStarted(value: String?): LocalDateTime {
        if (value.isNullOrBlank()) return LocalDateTime.now()
        val trimmed = value.trim()
        for (fmt in tempoStartedFormats) {
            try {
                return when {
                    fmt.toString().contains("HH") || trimmed.contains("T") || trimmed.contains(" ") ->
                        LocalDateTime.parse(trimmed.take(29).let {
                            // strip timezone for local parse variants without Z
                            it.replace(Regex("[+-]\\d{2}:?\\d{2}$"), "")
                                .replace(Regex("Z$"), "")
                        }, fmt)
                    else -> LocalDate.parse(trimmed.take(10), dateApi).atStartOfDay()
                }
            } catch (_: DateTimeParseException) {
                // try next
            }
        }
        return try {
            LocalDate.parse(trimmed.take(10)).atStartOfDay()
        } catch (_: Exception) {
            LocalDateTime.now()
        }
    }

    /** Tempo DC on this instance returns/accepts "yyyy-MM-dd HH:mm:ss.SSS" (space, not T). */
    fun formatTempoStarted(dateTime: LocalDateTime): String =
        dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))

    fun formatDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    fun secondsBetween(start: LocalTime, end: LocalTime): Int {
        var secs = end.toSecondOfDay() - start.toSecondOfDay()
        if (secs <= 0) secs += 24 * 3600
        return secs
    }
}
