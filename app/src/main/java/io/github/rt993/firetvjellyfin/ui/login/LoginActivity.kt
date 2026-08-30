package io.github.rt993.firetvjellyfin.ui.login

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.GuidedStepSupportFragment
import io.github.rt993.firetvjellyfin.data.JellyfinClientHolder
import io.github.rt993.firetvjellyfin.ui.home.HomeActivity

/**
 * Normally reached only after SplashActivity has already found no stored session - but it's a
 * separately launchable activity (a Fire TV home-screen tile pinned from an older install can
 * hold a direct reference to it, bypassing Splash entirely), so it re-checks for a session itself
 * rather than assuming Splash already ruled that out.
 */
class LoginActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (JellyfinClientHolder.hasStoredSession() && JellyfinClientHolder.api != null) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        if (savedInstanceState == null) {
            GuidedStepSupportFragment.addAsRoot(this, ServerAddressStepFragment(), android.R.id.content)
        }
    }
}
