package io.github.rt993.firetvjellyfin.ui.login

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.GuidedStepSupportFragment
import io.github.rt993.firetvjellyfin.data.JellyfinClientHolder
import io.github.rt993.firetvjellyfin.ui.home.HomeActivity

class LoginActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (JellyfinClientHolder.hasStoredSession() && JellyfinClientHolder.api != null) {
            startHome()
            return
        }

        if (savedInstanceState == null) {
            GuidedStepSupportFragment.addAsRoot(this, ServerAddressStepFragment(), android.R.id.content)
        }
    }

    private fun startHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
