package ru.astondev.tempomobile.util

object UrlUtils {
    fun normalizeBaseUrl(raw: String): String {
        var url = raw.trim().removeSuffix("/")
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        require(url.startsWith("https://")) {
            "Only HTTPS is supported"
        }
        return url
    }
}
