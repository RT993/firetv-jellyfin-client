package io.github.rt993.firetvjellyfin.ui.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinClientHolder
import io.github.rt993.firetvjellyfin.data.JellyfinRepository
import io.github.rt993.firetvjellyfin.ui.profile.ProfileSelectActivity
import io.github.rt993.firetvjellyfin.ui.splash.SplashActivity
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * A single glass card that walks through add server (a plus button) -> server address ->
 * credentials (or Quick Connect) -> sign-in, swapping which section is visible instead of
 * Leanback's boxy GuidedStepSupportFragment default look.
 *
 * Three entry points, chosen via [EXTRA_MODE]:
 * - Onboarding (no extra, no server ever saved): the full WELCOME -> SERVER -> CREDENTIALS walk.
 * - [MODE_ADD_SERVER] (from the "switch server" screen's + button): starts at SERVER directly.
 * - [MODE_ADD_PROFILE] (from the profile picker's + button): starts at CREDENTIALS directly,
 *   against whichever server is already the active connection.
 *
 * A successful sign-in in any mode saves the profile (and, for the first two, the server) and
 * always hands off to [ProfileSelectActivity] - this screen's only job is authenticating, not
 * deciding what happens afterward.
 *
 * Normally reached only after SplashActivity has already run its intro - but it's a separately
 * launchable activity (a Fire TV home-screen tile pinned from an older install can hold a direct
 * reference to it, bypassing Splash entirely), so a plain onboarding launch that skips Splash
 * forwards there first instead of skipping the intro. Direct in-app navigations (add server/add
 * profile) carry [EXTRA_MODE] and are exempt from that check - they're not launcher shortcuts.
 */
class LoginActivity : FragmentActivity(R.layout.activity_login) {

    private enum class Step { WELCOME, SERVER, CREDENTIALS, QUICK_CONNECT }

    private lateinit var subtitle: TextView
    private lateinit var stepWelcome: View
    private lateinit var stepServer: View
    private lateinit var stepCredentials: View
    private lateinit var stepQuickConnect: View
    private lateinit var errorText: TextView

    private lateinit var btnAddServer: View
    private lateinit var inputServerAddress: EditText
    private lateinit var btnContinue: Button
    private lateinit var inputUsername: EditText
    private lateinit var inputPassword: EditText
    private lateinit var btnSignIn: Button
    private lateinit var btnQuickConnect: TextView
    private lateinit var quickConnectCode: TextView
    private lateinit var quickConnectInstructions: TextView

    private var step = Step.WELCOME
    private var entryStep = Step.WELCOME
    private var quickConnectJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mode = intent.getStringExtra(EXTRA_MODE)

        if (mode == null && !intent.getBooleanExtra(SplashActivity.EXTRA_FROM_SPLASH, false)) {
            startActivity(Intent(this, SplashActivity::class.java))
            finish()
            return
        }
        if (mode == null && JellyfinClientHolder.hasAnyProfiles()) {
            // A stale onboarding entry despite profiles already existing (e.g. a shortcut cached
            // from before any server was added) - the picker is the real destination now.
            finishToProfileSelect()
            return
        }
        if (mode == MODE_ADD_PROFILE && JellyfinClientHolder.repository == null) {
            // Usually already connected (the picker only offers "add profile" for the server it's
            // currently showing), but the connection is only pre-warmed when the last-active
            // profile's session was still valid - reconnect against the persisted current server
            // URL rather than assuming that always held.
            val serverUrl = JellyfinClientHolder.currentServerUrl()
            if (serverUrl == null) {
                finish()
                return
            }
            JellyfinClientHolder.connect(serverUrl)
        }

        entryStep = when (mode) {
            MODE_ADD_SERVER -> Step.SERVER
            MODE_ADD_PROFILE -> Step.CREDENTIALS
            else -> Step.WELCOME
        }

        subtitle = findViewById(R.id.login_subtitle)
        stepWelcome = findViewById(R.id.step_welcome)
        stepServer = findViewById(R.id.step_server)
        stepCredentials = findViewById(R.id.step_credentials)
        stepQuickConnect = findViewById(R.id.step_quick_connect)
        errorText = findViewById(R.id.login_error)

        btnAddServer = findViewById(R.id.btn_add_server)
        inputServerAddress = findViewById(R.id.input_server_address)
        btnContinue = findViewById(R.id.btn_continue)
        inputUsername = findViewById(R.id.input_username)
        inputPassword = findViewById(R.id.input_password)
        btnSignIn = findViewById(R.id.btn_sign_in)
        btnQuickConnect = findViewById(R.id.btn_quick_connect)
        quickConnectCode = findViewById(R.id.quick_connect_code)
        quickConnectInstructions = findViewById(R.id.quick_connect_instructions)

        btnAddServer.setOnClickListener { showStep(Step.SERVER) }
        btnContinue.setOnClickListener { connectToServer() }
        btnSignIn.setOnClickListener { signIn() }
        btnQuickConnect.setOnClickListener { startQuickConnect() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (step == entryStep) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    return
                }
                when (step) {
                    Step.WELCOME -> Unit
                    Step.SERVER -> showStep(Step.WELCOME)
                    Step.CREDENTIALS -> showStep(Step.SERVER)
                    Step.QUICK_CONNECT -> showStep(Step.CREDENTIALS)
                }
            }
        })

        showStep(entryStep)
    }

    private fun showStep(newStep: Step) {
        if (newStep != Step.QUICK_CONNECT) {
            quickConnectJob?.cancel()
            quickConnectJob = null
        }
        step = newStep
        clearError()

        stepWelcome.visibility = if (newStep == Step.WELCOME) View.VISIBLE else View.GONE
        stepServer.visibility = if (newStep == Step.SERVER) View.VISIBLE else View.GONE
        stepCredentials.visibility = if (newStep == Step.CREDENTIALS) View.VISIBLE else View.GONE
        stepQuickConnect.visibility = if (newStep == Step.QUICK_CONNECT) View.VISIBLE else View.GONE

        subtitle.text = when (newStep) {
            Step.WELCOME -> getString(R.string.login_step_welcome_subtitle)
            Step.SERVER -> getString(R.string.login_step_server_subtitle)
            Step.CREDENTIALS -> getString(R.string.login_step_credentials_subtitle)
            Step.QUICK_CONNECT -> getString(R.string.login_quick_connect_title)
        }

        when (newStep) {
            Step.WELCOME -> btnAddServer.requestFocus()
            Step.SERVER -> inputServerAddress.requestFocus()
            Step.CREDENTIALS -> inputUsername.requestFocus()
            Step.QUICK_CONNECT -> Unit
        }
    }

    private fun connectToServer() {
        val serverUrl = inputServerAddress.text?.toString()?.trim()
        if (serverUrl.isNullOrBlank()) return

        JellyfinClientHolder.connect(serverUrl)
        // Best-effort, independent of whether sign-in itself succeeds afterward - the server
        // being reachable enough to answer this doesn't require valid credentials.
        lifecycleScope.launch {
            val name = runCatching { JellyfinClientHolder.repository?.getPublicServerName() }.getOrNull()
            JellyfinClientHolder.upsertCurrentServerName(name)
        }
        showStep(Step.CREDENTIALS)
    }

    private fun signIn() {
        val username = inputUsername.text?.toString()?.trim().orEmpty()
        val password = inputPassword.text?.toString().orEmpty()
        if (username.isBlank()) return

        val repository = JellyfinClientHolder.repository ?: return

        setCredentialsStepEnabled(false)
        lifecycleScope.launch {
            runCatching { repository.loginWithPassword(username, password) }
                .onSuccess { result ->
                    val token = result.accessToken
                    val userId = result.user?.id
                    if (token != null && userId != null) {
                        JellyfinClientHolder.persistSession(token, userId.toString(), result.user?.name)
                        finishToProfileSelect()
                    } else {
                        setCredentialsStepEnabled(true)
                        showError()
                    }
                }
                .onFailure {
                    setCredentialsStepEnabled(true)
                    showError(it)
                }
        }
    }

    private fun startQuickConnect() {
        val repository = JellyfinClientHolder.repository ?: return

        setCredentialsStepEnabled(false)
        lifecycleScope.launch {
            runCatching { repository.initiateQuickConnect() }
                .onSuccess { result ->
                    setCredentialsStepEnabled(true)
                    showStep(Step.QUICK_CONNECT)
                    quickConnectCode.text = result.code
                    quickConnectInstructions.text = getString(R.string.login_quick_connect_code_format, result.code)
                    pollForApproval(repository, result.secret)
                }
                .onFailure {
                    setCredentialsStepEnabled(true)
                    showError(it)
                }
        }
    }

    private fun pollForApproval(repository: JellyfinRepository, secret: String) {
        quickConnectJob = lifecycleScope.launch {
            runCatching {
                while (true) {
                    val state = repository.getQuickConnectState(secret)
                    if (state.authenticated) break
                    delay(POLL_INTERVAL_MS)
                }
                repository.completeQuickConnectLogin(secret)
            }.onSuccess { result ->
                val token = result.accessToken
                val userId = result.user?.id
                if (token != null && userId != null) {
                    JellyfinClientHolder.persistSession(token, userId.toString(), result.user?.name)
                    finishToProfileSelect()
                } else {
                    showStep(Step.CREDENTIALS)
                    showError()
                }
            }.onFailure {
                showStep(Step.CREDENTIALS)
                showError(it)
            }
        }
    }

    private fun setCredentialsStepEnabled(enabled: Boolean) {
        btnSignIn.isEnabled = enabled
        btnQuickConnect.isEnabled = enabled
        btnSignIn.text = if (enabled) getString(R.string.login_sign_in_title) else getString(R.string.login_connecting)
    }

    private fun showError(cause: Throwable? = null) {
        Log.e(TAG, "Login failed", cause)
        errorText.text = when {
            // A 401 here (unlike a wrong password further down the same status family, which the
            // server generally reports the same way) most often means the server itself is
            // refusing the connection because it doesn't consider this client's IP "local" -
            // common with a VPN/Tailscale address the server's own network settings don't know
            // about, and easy to mistake for a bad username/password otherwise.
            cause is InvalidStatusException && cause.status == 401 ->
                getString(R.string.login_error_401)
            cause != null -> "${getString(R.string.login_error_generic)}\n${cause.javaClass.simpleName}: ${cause.message}"
            else -> getString(R.string.login_error_generic)
        }
        errorText.visibility = View.VISIBLE
    }

    private fun clearError() {
        errorText.visibility = View.GONE
    }

    private fun finishToProfileSelect() {
        startActivity(
            Intent(this, ProfileSelectActivity::class.java)
                .putExtra(SplashActivity.EXTRA_FROM_SPLASH, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }

    companion object {
        /** Skips WELCOME, starts at SERVER - reached from the "switch server" screen's + button. */
        const val MODE_ADD_SERVER = "add_server"

        /** Skips WELCOME/SERVER, starts at CREDENTIALS - reached from the profile picker's + button. */
        const val MODE_ADD_PROFILE = "add_profile"
        const val EXTRA_MODE = "extra_mode"

        private const val TAG = "LoginActivity"
        private const val POLL_INTERVAL_MS = 2000L
    }
}
