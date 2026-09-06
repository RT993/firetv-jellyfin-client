package io.github.rt993.firetvjellyfin.data

/**
 * A plain in-memory "sticky note" cache: remembers the last value stored under a key for
 * [ttlMillis], then treats it as gone. Deliberately not persisted to disk and not shared beyond
 * one [JellyfinRepository] instance's lifetime - it exists purely to avoid re-asking the server
 * for the same answer twice within a few minutes of browsing (Home <-> a library <-> Home again),
 * not to be a durable offline store. Nothing here is thread-safe beyond what a single-threaded
 * Dispatchers.Main-confined caller (every call site in this app) already guarantees.
 */
class TtlCache<K, V>(private val ttlMillis: Long) {
    private data class Entry<V>(val value: V, val storedAtMillis: Long)

    private val entries = mutableMapOf<K, Entry<V>>()

    fun get(key: K): V? {
        val entry = entries[key] ?: return null
        if (System.currentTimeMillis() - entry.storedAtMillis > ttlMillis) {
            entries.remove(key)
            return null
        }
        return entry.value
    }

    fun put(key: K, value: V) {
        entries[key] = Entry(value, System.currentTimeMillis())
    }

    fun invalidate(key: K) {
        entries.remove(key)
    }
}
