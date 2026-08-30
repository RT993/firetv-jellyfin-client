package io.github.rt993.firetvjellyfin.ui.home

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import io.github.rt993.firetvjellyfin.R

class HomeActivity : FragmentActivity(R.layout.activity_home) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // BrowseSupportFragment hosts its row content via a child fragment transaction
        // (getChildFragmentManager().add/replace internally), which doesn't reliably attach when
        // the fragment itself is declared via a static <fragment> XML tag - add it programmatically
        // into a FragmentContainerView instead.
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, MainBrowseFragment())
                .commit()
        }
    }
}
