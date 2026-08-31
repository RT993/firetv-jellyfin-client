package io.github.rt993.firetvjellyfin.util

import java.text.DateFormat
import java.util.Calendar

/** Jellyfin runtimes are in 100-nanosecond .NET ticks; 10,000,000 ticks = 1 second. */
fun formatRuntimeTicks(ticks: Long?): String? {
    if (ticks == null) return null
    val totalMinutes = (ticks / 10_000_000L / 60L).toInt()
    if (totalMinutes <= 0) return null
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

/**
 * The clock time playback would finish at if started now, for [remainingTicks] left to play -
 * e.g. "Ends at 9:48 PM", matching the locale's short time format. Uses Calendar/DateFormat
 * rather than java.time - the latter needs API 26+ without core library desugaring, which this
 * project doesn't have configured, but minSdk here is 23.
 */
fun formatEndsAt(remainingTicks: Long): String? {
    val remainingMinutes = remainingTicks / 10_000_000L / 60L
    if (remainingMinutes <= 0) return null
    val endsAt = Calendar.getInstance().apply { add(Calendar.MINUTE, remainingMinutes.toInt()) }
    return DateFormat.getTimeInstance(DateFormat.SHORT).format(endsAt.time)
}
