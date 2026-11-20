package ru.astondev.tempomobile

import android.app.Application
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.astondev.tempomobile.data.local.UserPreferences
import ru.astondev.tempomobile.util.AppIconManager
import ru.astondev.tempomobile.util.AppLogger
import javax.inject.Inject

@HiltAndroidApp
class TempoApp : Application() {

    @Inject
    lateinit var appLogger: AppLogger

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var appIconManager: AppIconManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appLogger.installCrashHandler()
        appLogger.i(
            "App",
            "Logwise ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) started. " +
                "sdk=${Build.VERSION.SDK_INT}, device=${Build.MANUFACTURER} ${Build.MODEL}, " +
                "log=${appLogger.logFile.absolutePath}",
        )
        appScope.launch {
            val icon = userPreferences.settings.first().appIcon
            appIconManager.apply(icon)
        }
    }
}
