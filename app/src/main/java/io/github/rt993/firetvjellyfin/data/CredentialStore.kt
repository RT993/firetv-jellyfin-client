package io.github.rt993.firetvjellyfin.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists the last-used server address and login session so the app can reconnect
 * without showing the login screen every launch.
 *
 * This stores the access token in plain SharedPreferences for simplicity. Before any
 * wider release this should move to EncryptedSharedPreferences (androidx.security).
 */
class CredentialStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var serverUrl: String?
        get() = prefs.getString(KEY_SERVER_URL, null)
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    var username: String?
        get() = prefs.getString(KEY_USERNAME, null)
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    val hasSession: Boolean
        get() = !serverUrl.isNullOrBlank() && !accessToken.isNullOrBlank()

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "jellyfin_session"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_USERNAME = "username"
    }
}
