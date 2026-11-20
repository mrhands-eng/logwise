package ru.astondev.tempomobile.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.astondev.tempomobile.domain.Credentials
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureCredentialsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context,
            "tempo_secure_creds",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun save(credentials: Credentials) {
        prefs.edit()
            .putString(KEY_BASE_URL, credentials.baseUrl)
            .putString(KEY_AUTH_USERNAME, credentials.authUsername)
            .putString(KEY_USERNAME, credentials.authUsername) // backward compat
            .putString(KEY_PASSWORD, credentials.password)
            .putString(KEY_JIRA_NAME, credentials.jiraName)
            .putString(KEY_JIRA_KEY, credentials.jiraKey)
            .putString(KEY_EMAIL, credentials.email)
            .apply()
    }

    fun read(): Credentials? {
        val baseUrl = prefs.getString(KEY_BASE_URL, null) ?: return null
        val authUsername = prefs.getString(KEY_AUTH_USERNAME, null)
            ?: prefs.getString(KEY_USERNAME, null)
            ?: return null
        val password = prefs.getString(KEY_PASSWORD, null) ?: return null
        if (baseUrl.isBlank() || authUsername.isBlank() || password.isBlank()) return null
        val jiraName = prefs.getString(KEY_JIRA_NAME, null)?.takeIf { it.isNotBlank() } ?: authUsername
        return Credentials(
            baseUrl = baseUrl,
            authUsername = authUsername,
            password = password,
            jiraName = jiraName,
            jiraKey = prefs.getString(KEY_JIRA_KEY, null),
            email = prefs.getString(KEY_EMAIL, null),
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean = read() != null

    companion object {
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_AUTH_USERNAME = "auth_username"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_JIRA_NAME = "jira_name"
        private const val KEY_JIRA_KEY = "jira_key"
        private const val KEY_EMAIL = "email"
    }
}
