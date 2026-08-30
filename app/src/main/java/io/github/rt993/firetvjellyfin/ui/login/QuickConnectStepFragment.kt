package io.github.rt993.firetvjellyfin.ui.login

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist.Guidance
import androidx.leanback.widget.GuidedAction
import androidx.lifecycle.lifecycleScope
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinClientHolder
import io.github.rt993.firetvjellyfin.data.JellyfinRepository
import io.github.rt993.firetvjellyfin.ui.home.HomeActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Shows the Quick Connect code and polls the server until it has been approved from another
 * signed-in device, then completes the login.
 */
class QuickConnectStepFragment : GuidedStepSupportFragment() {

    private val secret: String get() = requireArguments().getString(ARG_SECRET)!!
    private val code: String get() = requireArguments().getString(ARG_CODE)!!

    override fun onCreateGuidance(savedInstanceState: Bundle?): Guidance = Guidance(
        getString(R.string.login_quick_connect_title),
        getString(R.string.login_quick_connect_code_format, code),
        code,
        null,
    )

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        actions += GuidedAction.Builder(requireContext())
            .id(ACTION_STATUS)
            .title(getString(R.string.login_connecting))
            .infoOnly(true)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pollForApproval()
    }

    private fun pollForApproval() {
        val api = JellyfinClientHolder.api ?: return
        val repository = JellyfinRepository(api)

        lifecycleScope.launch {
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
                    startActivity(Intent(requireActivity(), HomeActivity::class.java))
                    requireActivity().finish()
                } else {
                    showError()
                }
            }.onFailure { showError() }
        }
    }

    private fun showError() {
        Toast.makeText(requireContext(), R.string.login_error_generic, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val ARG_SECRET = "secret"
        private const val ARG_CODE = "code"
        private const val POLL_INTERVAL_MS = 2000L
        private const val ACTION_STATUS = 1L

        fun newInstance(secret: String, code: String): QuickConnectStepFragment =
            QuickConnectStepFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SECRET, secret)
                    putString(ARG_CODE, code)
                }
            }
    }
}
