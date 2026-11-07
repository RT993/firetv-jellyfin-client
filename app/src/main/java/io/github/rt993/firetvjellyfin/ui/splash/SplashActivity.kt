package io.github.rt993.firetvjellyfin.ui.splash

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.fragment.app.FragmentActivity
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinClientHolder
import io.github.rt993.firetvjellyfin.ui.login.LoginActivity
import io.github.rt993.firetvjellyfin.ui.profile.ProfileSelectActivity
import io.github.rt993.firetvjellyfin.ui.theme.fadeToNextScreen

/**
 * Launcher activity: fades the logo in, holds it, fades it out, then hands off to the profile
 * picker (if at least one profile has ever been saved) or Login for first-time setup - the
 * routing LoginActivity used to do on its own onCreate. The picker shows every time, regardless of
 * whether a session happens to already be pre-warmed - see [JellyfinClientHolder.hasAnyProfiles].
 */
class SplashActivity : FragmentActivity(R.layout.activity_splash) {

    private var pulse: ObjectAnimator? = null

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
                startPulse(logo)
                logo.animate()
                    .alpha(1f)
                    .setStartDelay(HOLD_MS)
                    .setDuration(0)
                    .withEndAction { fadeOut(logo) }
                    .start()
            }
            .start()
    }

    private fun startPulse(logo: View) {
        pulse = ObjectAnimator.ofPropertyValuesHolder(
            logo,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, PULSE_SCALE),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, PULSE_SCALE),
        ).apply {
            duration = PULSE_MS
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun fadeOut(logo: View) {
        pulse?.cancel()
        logo.scaleX = 1f
        logo.scaleY = 1f
        logo.animate()
            .alpha(0f)
            .setDuration(FADE_OUT_MS)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { proceed() }
            .start()
    }

    private fun proceed() {
        if (isFinishing) return
        val destination = if (JellyfinClientHolder.hasAnyProfiles()) {
            ProfileSelectActivity::class.java
        } else {
            LoginActivity::class.java
        }
        startActivity(Intent(this, destination).putExtra(EXTRA_FROM_SPLASH, true))
        fadeToNextScreen()
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
        private const val PULSE_SCALE = 1.05f
        private const val PULSE_MS = 1200L
    }
}
