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
 * Persists every uncaught exception to a plain text file in external files storage - pullable via
 * `adb pull /sdcard/Android/data/io.github.rt993.firetvjellyfin/files/crash_log.txt` without
 * needing `run-as` - so a crash is never lost to logcat's ring buffer wrapping or dropbox rotating
 * it out before anyone gets a chance to look, the way an actual on-device crash from "yesterday"
 * turned out to be unrecoverable from either (see the session this was added during). Delegates to
 * whatever handler was already installed afterwards, so the OS's own crash dialog/restart behavior
 * is unaffected - this only adds a second, durable copy alongside it.
 */
object CrashLogger {

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(appContext, thread, throwable) }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, FILE_NAME)
        if (file.length() > MAX_FILE_BYTES) file.delete()

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        file.appendText("\n===== $timestamp (thread: ${thread.name}) =====\n$stackTrace")
    }
}
