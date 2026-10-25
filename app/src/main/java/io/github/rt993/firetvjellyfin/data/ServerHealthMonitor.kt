package io.github.rt993.firetvjellyfin.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ServerHealth { UNKNOWN, HEALTHY, UNREACHABLE }

private const val TAG = "ServerHealthMonitor"
private const val CHECK_INTERVAL_MS = 5 * 60 * 1000L
private const val LOG_FILE_NAME = "server_health.log"
private const val MAX_LOG_BYTES = 200_000L

/**
 * Pings the server - the same no-auth System/Info/Public call [JellyfinRepository
 * .getPublicServerName] already uses - every five minutes and keeps a durable local log of the
 * result, independent of whatever screen happens to be open. Started once a session connects (see
 * [JellyfinClientHolder.connect]) and stopped on sign-out, so it always tracks the one active
 * connection, never a stale one left running against a server nobody's signed into anymore.
 *
 * Logs to the same external-files-dir location as [io.github.rt993.firetvjellyfin.util
 * .CrashLogger], for the same reason: pullable via `adb pull
 * /sdcard/Android/data/io.github.rt993.firetvjellyfin/files/server_health.log` without root.
 */
object ServerHealthMonitor {

    private val _status = MutableStateFlow(ServerHealth.UNKNOWN)
    val status: StateFlow<ServerHealth> = _status.asStateFlow()

    // A process-lifetime scope, not one tied to any particular screen - the whole point is that
    // this keeps checking in the background regardless of which Activity happens to be in front.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var logFile: File? = null
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun start(context: Context, repository: JellyfinRepository) {
        job?.cancel()
        _status.value = ServerHealth.UNKNOWN
        val dir = context.applicationContext.getExternalFilesDir(null) ?: context.applicationContext.filesDir
        logFile = File(dir, LOG_FILE_NAME)
        job = scope.launch {
            while (isActive) {
                checkOnce(repository)
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _status.value = ServerHealth.UNKNOWN
    }

    private suspend fun checkOnce(repository: JellyfinRepository) {
        val startedAt = System.currentTimeMillis()
        // getPublicServerName() already swallows its own network/parse failures and returns null -
        // a null result here just means "server didn't answer", not a crash to guard against.
        val serverName = repository.getPublicServerName()
        val elapsedMs = System.currentTimeMillis() - startedAt
        val healthy = serverName != null
        _status.value = if (healthy) ServerHealth.HEALTHY else ServerHealth.UNREACHABLE

        val line = "${timestampFormat.format(Date())}  ${if (healthy) "OK" else "UNREACHABLE"}  responded in ${elapsedMs}ms"
        Log.i(TAG, line)
        appendLine(line)
    }

    private fun appendLine(line: String) {
        val file = logFile ?: return
        runCatching {
            // A flat size cap, not a rolling line-count trim - at one line every five minutes this
            // is months of history before it ever matters, so simplicity wins over precision here.
            if (file.length() > MAX_LOG_BYTES) file.delete()
            file.appendText("$line\n")
        }.onFailure { Log.e(TAG, "Failed to write server health log", it) }
    }

    /** The most recent log lines, oldest first - reads the file off the IO dispatcher. */
    suspend fun recentLines(limit: Int = 100): List<String> = withContext(Dispatchers.IO) {
        val file = logFile ?: return@withContext emptyList()
        runCatching { file.readLines().takeLast(limit) }.getOrDefault(emptyList())
    }
}
