package io.github.rt993.firetvjellyfin.util

/** Jellyfin runtimes are in 100-nanosecond .NET ticks; 10,000,000 ticks = 1 second. */
fun formatRuntimeTicks(ticks: Long?): String? {
    if (ticks == null) return null
    val totalMinutes = (ticks / 10_000_000L / 60L).toInt()
    if (totalMinutes <= 0) return null
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
