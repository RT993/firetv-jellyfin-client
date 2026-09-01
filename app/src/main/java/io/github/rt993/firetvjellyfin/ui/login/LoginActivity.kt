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
import io.github.rt993.firetvjellyfin.ui.home.HomeActivity
import io.github.rt993.firetvjellyfin.ui.splash.SplashActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A single glass card that walks through add server (a plus button, since this is normally only
 * ever seen once - see [JellyfinClientHolder.hasStoredSession]) -> server address -> credentials
 * (or Quick Connect) -> sign-in, swapping which section is visible instead of Leanback's boxy
 * GuidedStepSupportFragment default look.
 *
 * Normally reached only after SplashActivity has already run its intro and found no stored
 * session - but it's a separately launchable activity (a Fire TV home-screen tile pinned from an
 * older install can hold a direct reference to it, bypassing Splash entirely), so if it's opened
 * any other way it forwards to Splash first instead of skipping the intro. It also re-checks for
 * a session itself rather than trusting that Splash already ruled that out, for the same reason.
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
    private var quickConnectJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!intent.getBooleanExtra(SplashActivity.EXTRA_FROM_SPLASH, false)) {
            startActivity(Intent(this, SplashActivity::class.java))
            finish()
            return
        }

        if (JellyfinClientHolder.hasStoredSession() && JellyfinClientHolder.api != null) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
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
                when (step) {
                    Step.WELCOME -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                    Step.SERVER -> showStep(Step.WELCOME)
                    Step.CREDENTIALS -> showStep(Step.SERVER)
                    Step.QUICK_CONNECT -> showStep(Step.CREDENTIALS)
                }
            }
        })

        showStep(Step.WELCOME)
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
        showStep(Step.CREDENTIALS)
    }

    private fun signIn() {
        val username = inputUsername.text?.toString()?.trim().orEmpty()
        val password = inputPassword.text?.toString().orEmpty()
        if (username.isBlank()) return

        val api = JellyfinClientHolder.api ?: return
        val repository = JellyfinRepository(api)

        setCredentialsStepEnabled(false)
        lifecycleScope.launch {
            runCatching { repository.loginWithPassword(username, password) }
                .onSuccess { result ->
                    val token = result.accessToken
                    val userId = result.user?.id
                    if (token != null && userId != null) {
                        JellyfinClientHolder.persistSession(token, userId.toString(), result.user?.name)
                        startHome()
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
        val api = JellyfinClientHolder.api ?: return
        val repository = JellyfinRepository(api)

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
                    startHome()
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
        errorText.text = if (cause != null) {
            "${getString(R.string.login_error_generic)}\n${cause.javaClass.simpleName}: ${cause.message}"
        } else {
            getString(R.string.login_error_generic)
        }
        errorText.visibility = View.VISIBLE
    }

    private fun clearError() {
        errorText.visibility = View.GONE
    }

    private fun startHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    private companion object {
        const val TAG = "LoginActivity"
        const val POLL_INTERVAL_MS = 2000L
    }
}
