package io.github.rt993.firetvjellyfin.ui.splash

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.fragment.app.FragmentActivity
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinClientHolder
import io.github.rt993.firetvjellyfin.ui.home.HomeActivity
import io.github.rt993.firetvjellyfin.ui.login.LoginActivity

/**
 * Launcher activity: fades the logo in, holds it, fades it out, then hands off to Home (if a
 * session is already stored) or Login - the routing LoginActivity used to do on its own onCreate.
 */
class SplashActivity : FragmentActivity(R.layout.activity_splash) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playIntro(findViewById(R.id.splash_logo))
    }

    private fun playIntro(logo: View) {
        logo.alpha = 0f
        logo.animate()
            .alpha(1f)
            .setDuration(FADE_IN_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                logo.animate()
                    .alpha(1f)
                    .setStartDelay(HOLD_MS)
                    .setDuration(0)
                    .withEndAction { fadeOut(logo) }
                    .start()
            }
            .start()
    }

    private fun fadeOut(logo: View) {
        logo.animate()
            .alpha(0f)
            .setDuration(FADE_OUT_MS)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { proceed() }
            .start()
    }

    private fun proceed() {
        if (isFinishing) return
        val destination = if (JellyfinClientHolder.hasStoredSession() && JellyfinClientHolder.api != null) {
            HomeActivity::class.java
        } else {
            LoginActivity::class.java
        }
        startActivity(Intent(this, destination).putExtra(EXTRA_FROM_SPLASH, true))
        finish()
    }

    companion object {
        // A cached/stale launcher shortcut can hold a direct reference to LoginActivity, skipping
        // this intro entirely - LoginActivity checks for this extra and, if it's missing, forwards
        // to Splash itself instead of showing the login step straight away.
        const val EXTRA_FROM_SPLASH = "from_splash"

        private const val FADE_IN_MS = 700L
        private const val HOLD_MS = 3600L
        private const val FADE_OUT_MS = 700L
    }
}
