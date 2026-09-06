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

    // One JellyfinRepository shared for the whole signed-in session, instead of every
    // Activity/Fragment building its own throwaway instance - each fresh instance meant the
    // repository's in-memory caches (see JellyfinRepository) were rebuilt empty on every screen
    // open, defeating the point of caching entirely. Rebuilt alongside [api] in [connect] so a
    // new server connection (or account) never sees a previous one's cached data.
    var repository: JellyfinRepository? = null
        private set

    fun initialize(context: Context) {
        credentialStore = CredentialStore(context)
        jellyfin = createJellyfin {
            this.context = context
            clientInfo = ClientInfo(name = "TreeHouse", version = BuildConfigVersion)
        }

        // Pre-warms a connection for whichever profile was last active, purely so picking that
        // same profile again in ProfileSelectActivity is instant - it does NOT skip the picker
        // itself (see hasAnyProfiles/activateProfile), and a missing/expired session here is not
        // an error: the picker's own activateProfile call establishes a fresh one regardless of
        // whatever state this leaves api/repository in.
        val savedServerUrl = credentialStore.serverUrl
        if (savedServerUrl != null && credentialStore.hasSession) {
            connect(savedServerUrl)
            api?.update(accessToken = credentialStore.accessToken)
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
        repository = JellyfinRepository(client)
        credentialStore.serverUrl = normalizedUrl
        return client
    }

    /** Persists a successful sign-in as both the active session and a saved profile for it. */
    fun persistSession(accessToken: String, userId: String, username: String?) {
        api?.update(accessToken = accessToken)
        credentialStore.accessToken = accessToken
        credentialStore.userId = userId
        credentialStore.username = username
        credentialStore.loginTimestamp = System.currentTimeMillis()

        val serverUrl = credentialStore.serverUrl
        if (serverUrl != null) {
            credentialStore.upsertProfile(SavedProfile(serverUrl, userId, username.orEmpty(), accessToken))
        }
    }

    /** Records (or updates) the current server's display name once it's known. */
    fun upsertCurrentServerName(name: String?) {
        val url = credentialStore.serverUrl ?: return
        credentialStore.upsertServer(SavedServer(url, name.orEmpty()))
    }

    fun savedServers(): List<SavedServer> = credentialStore.servers

    fun currentServerUrl(): String? = credentialStore.serverUrl

    /** Saved profiles for whichever server is currently active - what the picker shows. */
    fun savedProfilesForCurrentServer(): List<SavedProfile> =
        credentialStore.serverUrl?.let { credentialStore.profilesForServer(it) }.orEmpty()

    fun hasAnyProfiles(): Boolean = credentialStore.profiles.isNotEmpty()

    /**
     * Switches to [profile]: connects to its server first if that isn't already the live
     * connection, then authenticates as it. This is the only path that should ever make a
     * profile "active" - picking a profile in the UI should never silently keep whatever
     * session happened to be pre-warmed by [initialize] if it belongs to someone else.
     */
    fun activateProfile(profile: SavedProfile) {
        val client = if (api?.baseUrl == profile.serverUrl) requireNotNull(api) else connect(profile.serverUrl)
        client.update(accessToken = profile.accessToken)
        credentialStore.accessToken = profile.accessToken
        credentialStore.userId = profile.userId
        credentialStore.username = profile.username
        credentialStore.loginTimestamp = System.currentTimeMillis()
    }

    fun currentUserId(): String? = credentialStore.userId

    fun currentUsername(): String? = credentialStore.username

    fun hasStoredSession(): Boolean = credentialStore.hasSession

    /**
     * Deactivates the current session only - every saved server/profile stays in place, so the
     * profile picker still shows them all and picking one (even the one just signed out of)
     * simply re-authenticates it fresh via [activateProfile].
     */
    fun signOut() {
        credentialStore.clearCurrentSession()
        api = null
        repository = null
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
    private const val BuildConfigVersion = "0.3.2"
}
