package io.github.rt993.firetvjellyfin.ui.login

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist.Guidance
import androidx.leanback.widget.GuidedAction
import androidx.lifecycle.lifecycleScope
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinClientHolder
import io.github.rt993.firetvjellyfin.data.JellyfinRepository
import io.github.rt993.firetvjellyfin.ui.home.HomeActivity
import kotlinx.coroutines.launch

/** Second login step: username/password, or a hand-off to [QuickConnectStepFragment]. */
class CredentialsStepFragment : GuidedStepSupportFragment() {

    override fun onCreateGuidance(savedInstanceState: Bundle?): Guidance = Guidance(
        getString(R.string.login_title),
        getString(R.string.login_username_title),
        null,
        null,
    )

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        actions += GuidedAction.Builder(requireContext())
            .id(ACTION_USERNAME)
            .title(getString(R.string.login_username_title))
            .description("")
            .descriptionEditable(true)
            .descriptionEditInputType(InputType.TYPE_CLASS_TEXT)
            .build()

        actions += GuidedAction.Builder(requireContext())
            .id(ACTION_PASSWORD)
            .title(getString(R.string.login_password_title))
            .description("")
            .descriptionEditable(true)
            .descriptionEditInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
            .build()

        actions += GuidedAction.Builder(requireContext())
            .id(ACTION_SIGN_IN)
            .title(getString(R.string.login_sign_in_title))
            .build()

        actions += GuidedAction.Builder(requireContext())
            .id(ACTION_QUICK_CONNECT)
            .title(getString(R.string.login_quick_connect_title))
            .description(getString(R.string.login_quick_connect_description))
            .build()
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        when (action.id) {
            ACTION_SIGN_IN -> signIn()
            ACTION_QUICK_CONNECT -> startQuickConnect()
        }
    }

    private fun signIn() {
        val username = findActionById(ACTION_USERNAME)?.description?.toString()?.trim().orEmpty()
        val password = findActionById(ACTION_PASSWORD)?.description?.toString().orEmpty()
        if (username.isBlank()) return

        val api = JellyfinClientHolder.api ?: return
        val repository = JellyfinRepository(api)

        lifecycleScope.launch {
            runCatching { repository.loginWithPassword(username, password) }
                .onSuccess { result ->
                    val token = result.accessToken
                    val userId = result.user?.id
                    if (token != null && userId != null) {
                        JellyfinClientHolder.persistSession(token, userId.toString())
                        startHome()
                    } else {
                        showError()
                    }
                }
                .onFailure { showError() }
        }
    }

    private fun startQuickConnect() {
        val api = JellyfinClientHolder.api ?: return
        val repository = JellyfinRepository(api)

        lifecycleScope.launch {
            runCatching { repository.initiateQuickConnect() }
                .onSuccess { result ->
                    GuidedStepSupportFragment.add(
                        parentFragmentManager,
                        QuickConnectStepFragment.newInstance(result.secret, result.code),
                    )
                }
                .onFailure { showError() }
        }
    }

    private fun showError() {
        Toast.makeText(requireContext(), R.string.login_error_generic, Toast.LENGTH_LONG).show()
    }

    private fun startHome() {
        startActivity(Intent(requireActivity(), HomeActivity::class.java))
        requireActivity().finish()
    }

    private companion object {
        const val ACTION_USERNAME = 1L
        const val ACTION_PASSWORD = 2L
        const val ACTION_SIGN_IN = 3L
        const val ACTION_QUICK_CONNECT = 4L
    }
}
