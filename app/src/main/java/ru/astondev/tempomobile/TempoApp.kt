package ru.astondev.tempomobile

import android.app.Application
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import ru.astondev.tempomobile.util.AppLogger
import javax.inject.Inject

@HiltAndroidApp
class TempoApp : Application() {

    @Inject
    lateinit var appLogger: AppLogger

    override fun onCreate() {
        super.onCreate()
        appLogger.installCrashHandler()
        appLogger.i(
            "App",
            "Logwise ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) started. " +
                "sdk=${Build.VERSION.SDK_INT}, device=${Build.MANUFACTURER} ${Build.MODEL}, " +
                "log=${appLogger.logFile.absolutePath}",
        )
    }
}
