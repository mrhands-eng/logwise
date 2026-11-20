package ru.astondev.tempomobile.ui.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.astondev.tempomobile.MainActivity
import ru.astondev.tempomobile.data.local.UserPreferences
import ru.astondev.tempomobile.util.DateTimeFormats
import javax.inject.Inject

@AndroidEntryPoint
class TimerService : Service() {

    @Inject lateinit var userPreferences: UserPreferences

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticker: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val key = intent?.getStringExtra(EXTRA_ISSUE_KEY).orEmpty()
                val summary = intent?.getStringExtra(EXTRA_ISSUE_SUMMARY).orEmpty()
                ensureChannel()
                startForeground(NOTIFICATION_ID, buildNotification(key, summary, 0))
                ticker?.cancel()
                ticker = scope.launch {
                    val started = userPreferences.timerState.first()?.startedAtEpochMs
                        ?: System.currentTimeMillis()
                    while (isActive) {
                        val elapsed = ((System.currentTimeMillis() - started) / 1000).toInt()
                        val nm = getSystemService(NotificationManager::class.java)
                        nm.notify(NOTIFICATION_ID, buildNotification(key, summary, elapsed))
                        delay(15_000)
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        ticker?.cancel()
        super.onDestroy()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Таймер Logwise",
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    private fun buildNotification(issueKey: String, summary: String, elapsedSeconds: Int): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_TIMER, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, TimerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = if (issueKey.isBlank()) "Идёт учёт времени" else "Идёт учёт · $issueKey"
        val text = buildString {
            append(DateTimeFormats.formatDuration(elapsedSeconds.coerceAtLeast(0)))
            if (summary.isNotBlank()) {
                append(" · ")
                append(summary.take(40))
            }
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Стоп", stop)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "tempo_timer"
        const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "ru.astondev.tempomobile.TIMER_STOP"
        const val EXTRA_ISSUE_KEY = "issue_key"
        const val EXTRA_ISSUE_SUMMARY = "issue_summary"
        const val EXTRA_OPEN_TIMER = "open_timer"

        fun start(context: Context, issueKey: String, issueSummary: String) {
            val intent = Intent(context, TimerService::class.java).apply {
                putExtra(EXTRA_ISSUE_KEY, issueKey)
                putExtra(EXTRA_ISSUE_SUMMARY, issueSummary)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, TimerService::class.java).setAction(ACTION_STOP))
            context.stopService(Intent(context, TimerService::class.java))
        }
    }
}
