package io.github.rt993.firetvjellyfin.ui.theme

import android.app.Activity
import io.github.rt993.firetvjellyfin.R

/**
 * The fade used right after every startActivity() call across the app's navigation flow (Home,
 * Details, Playback, the profile/server pickers), instead of each screen change defaulting to the
 * platform's abrupt hard cut.
 */
fun Activity.fadeToNextScreen() {
    @Suppress("DEPRECATION")
    overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
}
