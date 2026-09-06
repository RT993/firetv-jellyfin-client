package io.github.rt993.firetvjellyfin.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One server this device has ever connected to - just enough to list and re-select it. */
@Serializable
data class SavedServer(val url: String, val name: String = "")

/**
 * One signed-in Jellyfin user on a given server, with the access token from when they last signed
 * in - lets the profile picker switch straight to them without asking for a password again, the
 * same way a real login normally would.
 */
@Serializable
data class SavedProfile(val serverUrl: String, val userId: String, val username: String, val accessToken: String)

/**
 * Persists every server/profile this device has ever signed into, plus which one is currently
 * active, so the app can reconnect without showing the login screen every launch.
 *
 * This stores access tokens in plain SharedPreferences for simplicity. Before any wider release
 * this should move to EncryptedSharedPreferences (androidx.security).
 */
class CredentialStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The currently active server - also which server's profiles the picker shows. */
    var serverUrl: String?
        get() = prefs.getString(KEY_SERVER_URL, null)
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    /** The currently active profile's session, established by picking a profile. */
    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    var username: String?
        get() = prefs.getString(KEY_USERNAME, null)
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    /** When the current access token was issued, so it can be aged out after [SESSION_TTL_MS]. */
    var loginTimestamp: Long
        get() = prefs.getLong(KEY_LOGIN_TIMESTAMP, 0L)
        set(value) = prefs.edit().putLong(KEY_LOGIN_TIMESTAMP, value).apply()

    private val isSessionExpired: Boolean
        get() = loginTimestamp > 0L && System.currentTimeMillis() - loginTimestamp > SESSION_TTL_MS

    val hasSession: Boolean
        get() = !serverUrl.isNullOrBlank() && !accessToken.isNullOrBlank() && !isSessionExpired

    /** Every server this device has ever connected to, for the "switch server" screen. */
    var servers: List<SavedServer>
        get() = decode(prefs.getString(KEY_SERVERS, null))
        set(value) = prefs.edit().putString(KEY_SERVERS, Json.encodeToString(value)).apply()

    /** Every signed-in profile across every server, for the profile picker. */
    var profiles: List<SavedProfile>
        get() = decode(prefs.getString(KEY_PROFILES, null))
        set(value) = prefs.edit().putString(KEY_PROFILES, Json.encodeToString(value)).apply()

    fun profilesForServer(url: String): List<SavedProfile> = profiles.filter { it.serverUrl == url }

    /** Adds [server], or replaces the existing entry for the same URL if one is already saved. */
    fun upsertServer(server: SavedServer) {
        servers = servers.filterNot { it.url == server.url } + server
    }

    /** Adds [profile], or replaces the existing entry for the same server+user if already saved. */
    fun upsertProfile(profile: SavedProfile) {
        profiles = profiles.filterNot { it.serverUrl == profile.serverUrl && it.userId == profile.userId } + profile
    }

    /**
     * Forgets which profile is currently active (used when signing out) without touching the
     * saved server/profile registry - the profile picker still shows everything, just with
     * nothing pre-selected, so a fresh pick re-authenticates against its own saved token.
     */
    fun clearCurrentSession() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_USERNAME)
            .remove(KEY_LOGIN_TIMESTAMP)
            .apply()
    }

    /** Wipes every saved server and profile - a true full reset, not just the current session. */
    fun clear() {
        prefs.edit().clear().apply()
    }

    private inline fun <reified T> decode(json: String?): List<T> {
        if (json == null) return emptyList()
        return runCatching { Json.decodeFromString<List<T>>(json) }.getOrDefault(emptyList())
    }

    private companion object {
        const val PREFS_NAME = "jellyfin_session"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_USERNAME = "username"
        const val KEY_LOGIN_TIMESTAMP = "login_timestamp"
        const val KEY_SERVERS = "servers"
        const val KEY_PROFILES = "profiles"
        const val SESSION_TTL_MS = 90L * 24 * 60 * 60 * 1000 // 90 days
    }
}
