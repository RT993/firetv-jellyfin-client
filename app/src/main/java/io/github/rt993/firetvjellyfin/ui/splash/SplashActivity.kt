package io.github.rt993.firetvjellyfin.ui.splash

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.fragment.app.FragmentActivity
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinClientHolder
import io.github.rt993.firetvjellyfin.ui.home.HomeActivity
import io.github.rt993.firetvjellyfin.ui.login.LoginActivity

/**
 * Launcher activity: plays a short floating-logo intro, then hands off to Home (if a session is
 * already stored) or Login - the routing LoginActivity used to do on its own onCreate.
 */
class SplashActivity : FragmentActivity(R.layout.activity_splash) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playIntro(findViewById(R.id.splash_logo))
    }

    private fun playIntro(logo: View) {
        logo.alpha = 0f
        logo.scaleX = ENTRANCE_SCALE
        logo.scaleY = ENTRANCE_SCALE
        logo.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(ENTRANCE_MS)
            .setInterpolator(OvershootInterpolator())
            .withEndAction { floatLogo(logo, FLOAT_OFFSETS.iterator()) }
            .start()
    }

    /** Drifts the logo through a small wander loop - a gentle "floating" motion - before it vanishes. */
    private fun floatLogo(logo: View, offsets: Iterator<Pair<Float, Float>>) {
        if (!offsets.hasNext()) {
            vanish(logo)
            return
        }
        val (dx, dy) = offsets.next()
        logo.animate()
            .translationX(dpToPx(dx))
            .translationY(dpToPx(dy))
            .rotation(if (dx >= 0) 3f else -3f)
            .setDuration(FLOAT_STEP_MS)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction { floatLogo(logo, offsets) }
            .start()
    }

    private fun vanish(logo: View) {
        logo.animate()
            .alpha(0f)
            .scaleX(EXIT_SCALE).scaleY(EXIT_SCALE)
            .setDuration(EXIT_MS)
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
        startActivity(Intent(this, destination))
        finish()
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density

    private companion object {
        const val ENTRANCE_MS = 450L
        const val FLOAT_STEP_MS = 650L
        const val EXIT_MS = 450L
        const val ENTRANCE_SCALE = 0.6f
        const val EXIT_SCALE = 1.35f

        // A small wander loop - up-left, down-right, up-right, back to center - before the fade.
        val FLOAT_OFFSETS = listOf(-18f to -14f, 18f to 12f, 14f to -10f, 0f to 0f)
    }
}
