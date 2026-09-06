package io.github.rt993.firetvjellyfin.util

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val FILE_NAME = "crash_log.txt"
private const val MAX_FILE_BYTES = 200_000L

/**
 * Persists every uncaught exception - and anything else worth a durable breadcrumb, see [logEvent]
 * - to a plain text file in external files storage, pullable via `adb pull
 * /sdcard/Android/data/io.github.rt993.firetvjellyfin/files/crash_log.txt` without needing
 * `run-as`. logcat's ring buffer and dropbox both turned out to be unreliable for catching a real
 * on-device failure after the fact (see the session this was added during) - this is a second,
 * durable copy that survives until the file itself is cleared. [install]'s handler delegates to
 * whatever handler was already installed afterwards, so the OS's own crash dialog/restart behavior
 * is unaffected.
 */
object CrashLogger {

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
                logEvent(appContext, "UNCAUGHT (thread: ${thread.name})\n$stackTrace")
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * A one-line (or multi-line) breadcrumb, timestamped and appended to the same durable file as
     * a real crash - e.g. [android.app.Activity.onTrimMemory] callbacks, which the OS sends as a
     * warning before it kills a process for memory pressure. A kill like that isn't a Java
     * exception - it doesn't show up in [install]'s handler, dropbox, or a crash-only log buffer -
     * so it would otherwise look identical to total silence.
     */
    fun logEvent(context: Context, message: String) {
        runCatching {
            val dir = context.applicationContext.getExternalFilesDir(null) ?: context.filesDir
            val file = File(dir, FILE_NAME)
            if (file.length() > MAX_FILE_BYTES) file.delete()

            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            file.appendText("\n===== $timestamp =====\n$message")
        }
    }
}
