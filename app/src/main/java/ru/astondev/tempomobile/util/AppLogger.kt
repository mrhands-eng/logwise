package ru.astondev.tempomobile.util

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLogger @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val timeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        .withZone(ZoneId.systemDefault())

    private val logDir: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "logs").also { it.mkdirs() }

    val logFile: File
        get() = File(logDir, LOG_FILE_NAME)

    fun i(tag: String, message: String) = write("I", tag, message)

    fun w(tag: String, message: String, throwable: Throwable? = null) =
        write("W", tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        write("E", tag, message, throwable)

    fun d(tag: String, message: String) = write("D", tag, message)

    fun clear() {
        executor.execute {
            synchronized(this) {
                if (logFile.exists()) logFile.writeText("")
            }
        }
    }

    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeSync("E", "Crash", "Uncaught on ${thread.name}", throwable)
            } catch (_: Exception) {
                // ignore
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun write(level: String, tag: String, message: String, throwable: Throwable? = null) {
        executor.execute {
            writeSync(level, tag, message, throwable)
        }
    }

    @Synchronized
    private fun writeSync(level: String, tag: String, message: String, throwable: Throwable? = null) {
        val safeMessage = redact(message)
        val line = buildString {
            append(timeFmt.format(Instant.now()))
            append(' ')
            append(level)
            append('/')
            append(tag)
            append(": ")
            append(safeMessage)
            if (throwable != null) {
                append('\n')
                append(stackTrace(throwable))
            }
            append('\n')
        }
        when (level) {
            "E" -> Log.e(tag, safeMessage, throwable)
            "W" -> Log.w(tag, safeMessage, throwable)
            "D" -> Log.d(tag, safeMessage)
            else -> Log.i(tag, safeMessage)
        }
        try {
            rotateIfNeeded()
            FileOutputStream(logFile, true).bufferedWriter().use { it.append(line) }
        } catch (t: Exception) {
            Log.e(TAG, "Failed to write log file", t)
        }
    }

    private fun rotateIfNeeded() {
        if (!logFile.exists()) return
        if (logFile.length() < MAX_BYTES) return
        val bak = File(logDir, "$LOG_FILE_NAME.bak")
        if (bak.exists()) bak.delete()
        logFile.renameTo(bak)
    }

    private fun stackTrace(t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return redact(sw.toString())
    }

    private fun redact(text: String): String {
        var out = text
        REDACT_PATTERNS.forEach { (regex, replacement) ->
            out = regex.replace(out, replacement)
        }
        return out
    }

    companion object {
        private const val TAG = "AppLogger"
        const val LOG_FILE_NAME = "tempo-mobile.log"
        private const val MAX_BYTES = 2L * 1024L * 1024L // 2 MB

        private val REDACT_PATTERNS = listOf(
            Regex("""(?i)(authorization\s*[:=]\s*)\S+""") to "$1***",
            Regex("""(?i)("password"\s*:\s*")[^"]*(")""") to "$1***$2",
            Regex("""(?i)(password[=:]\s*)\S+""") to "$1***",
            Regex("""(?i)(Basic\s+)[A-Za-z0-9+/=]+""") to "$1***",
        )
    }
}
