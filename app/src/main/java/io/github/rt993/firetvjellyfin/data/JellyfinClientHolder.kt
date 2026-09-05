package io.github.rt993.firetvjellyfin.data

import android.content.Context
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import kotlin.time.Duration.Companion.minutes

/**
 * Process-wide holder for the Jellyfin SDK instance and the current [ApiClient].
 *
 * A single [ApiClient] is reused for the lifetime of a server connection: logging in does not
 * create a new instance, it just calls [ApiClient.update] with the new access token.
 */
object JellyfinClientHolder {

    private lateinit var jellyfin: Jellyfin
    private lateinit var credentialStore: CredentialStore

    var api: ApiClient? = null
        private set

    fun initialize(context: Context) {
        credentialStore = CredentialStore(context)
        jellyfin = createJellyfin {
            this.context = context
            clientInfo = ClientInfo(name = "TreeHouse", version = BuildConfigVersion)
        }

        val savedServerUrl = credentialStore.serverUrl
        if (savedServerUrl != null && credentialStore.hasSession) {
            connect(savedServerUrl)
            api?.update(accessToken = credentialStore.accessToken)
        } else if (savedServerUrl != null) {
            // A stored server with no valid (or expired) session - drop it so the login flow
            // starts clean instead of silently trying a dead token.
            credentialStore.clear()
        }
    }

    /** Connect (or reconnect) to a server, discarding any previous session's access token. */
    fun connect(serverUrl: String): ApiClient {
        val normalizedUrl = normalizeServerUrl(serverUrl)
        // The SDK's own default request/socket timeout is 30s. GetPostedPlaybackInfo can take
        // much longer than that on its own: Jellyfin extracts every embedded text subtitle track
        // into its own .srt file synchronously, as part of building that one response, so it can
        // hand back a deliveryUrl for each - a movie with two dozen+ subtitle tracks (dubs/SDH
        // variants are common on 4K rips) on a slow disk can take well past 30s before the server
        // replies, which the SDK surfaces as a plain TimeoutException indistinguishable from the
        // server being unreachable, and PlaybackActivity was giving up on it accordingly.
        val client = jellyfin.createApi(
            baseUrl = normalizedUrl,
            httpClientOptions = HttpClientOptions(requestTimeout = 2.minutes, socketTimeout = 2.minutes),
        )
        api = client
        credentialStore.serverUrl = normalizedUrl
        return client
    }

    fun persistSession(accessToken: String, userId: String, username: String?) {
        api?.update(accessToken = accessToken)
        credentialStore.accessToken = accessToken
        credentialStore.userId = userId
        credentialStore.username = username
        credentialStore.loginTimestamp = System.currentTimeMillis()
    }

    fun currentUserId(): String? = credentialStore.userId

    fun currentUsername(): String? = credentialStore.username

    fun hasStoredSession(): Boolean = credentialStore.hasSession

    fun signOut() {
        credentialStore.clear()
        api = null
    }

    private fun normalizeServerUrl(input: String): String {
        val trimmed = input.trim().trimEnd('/')
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }

    // Kept separate from BuildConfig.VERSION_NAME so this file has no Gradle-generated dependency -
    // which means it has to be bumped by hand alongside app/build.gradle.kts's versionName.
    private const val BuildConfigVersion = "0.2.1"
}
