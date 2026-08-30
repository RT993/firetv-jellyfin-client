package io.github.rt993.firetvjellyfin.ui.login

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.GuidedStepSupportFragment

/** Only reached when SplashActivity found no stored session, so it always shows the login step. */
class LoginActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            GuidedStepSupportFragment.addAsRoot(this, ServerAddressStepFragment(), android.R.id.content)
        }
    }
}
