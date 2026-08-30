package io.github.rt993.firetvjellyfin.ui.login

import android.os.Bundle
import android.text.InputType
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist.Guidance
import androidx.leanback.widget.GuidedAction
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinClientHolder

/** First login step: ask for the Jellyfin server address (e.g. http://192.168.1.10:8096). */
class ServerAddressStepFragment : GuidedStepSupportFragment() {

    override fun onCreateGuidance(savedInstanceState: Bundle?): Guidance = Guidance(
        getString(R.string.login_title),
        getString(R.string.login_server_address_description),
        null,
        null,
    )

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        actions += GuidedAction.Builder(requireContext())
            .id(ACTION_SERVER_ADDRESS)
            .title(getString(R.string.login_server_address_title))
            .description("")
            .descriptionEditable(true)
            .descriptionEditInputType(InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_CLASS_TEXT)
            .build()

        actions += GuidedAction.Builder(requireContext())
            .clickAction(GuidedAction.ACTION_ID_NEXT)
            .build()
    }

    override fun onGuidedActionEditedAndProceed(action: GuidedAction): Long {
        if (action.id == ACTION_SERVER_ADDRESS) proceed()
        return GuidedAction.ACTION_ID_NEXT
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        if (action.id == GuidedAction.ACTION_ID_NEXT) proceed()
    }

    private fun proceed() {
        val serverUrl = findActionById(ACTION_SERVER_ADDRESS)?.description?.toString()?.trim()
        if (serverUrl.isNullOrBlank()) return

        JellyfinClientHolder.connect(serverUrl)
        GuidedStepSupportFragment.add(parentFragmentManager, CredentialsStepFragment())
    }

    private companion object {
        const val ACTION_SERVER_ADDRESS = 1L
    }
}
