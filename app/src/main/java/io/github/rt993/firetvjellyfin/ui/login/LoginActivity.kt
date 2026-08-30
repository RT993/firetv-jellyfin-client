package io.github.rt993.firetvjellyfin.ui.login

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.GuidedStepSupportFragment
import io.github.rt993.firetvjellyfin.data.JellyfinClientHolder
import io.github.rt993.firetvjellyfin.ui.home.HomeActivity
import io.github.rt993.firetvjellyfin.ui.splash.SplashActivity

/**
 * Normally reached only after SplashActivity has already run its intro and found no stored
 * session - but it's a separately launchable activity (a Fire TV home-screen tile pinned from an
 * older install can hold a direct reference to it, bypassing Splash entirely), so if it's opened
 * any other way it forwards to Splash first instead of skipping the intro. It also re-checks for
 * a session itself rather than trusting that Splash already ruled that out, for the same reason.
 */
class LoginActivity : FragmentActivity() {

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

        if (savedInstanceState == null) {
            GuidedStepSupportFragment.addAsRoot(this, ServerAddressStepFragment(), android.R.id.content)
        }
    }
}
