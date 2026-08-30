package io.github.rt993.firetvjellyfin.ui.home

import android.os.Bundle
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinClientHolder
import org.jellyfin.sdk.model.api.CollectionType

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

        findViewById<TextView>(R.id.topbar_username).text = JellyfinClientHolder.currentUsername().orEmpty()
        setUpNavItem(R.id.nav_home, null)
        setUpNavItem(R.id.nav_movies, CollectionType.MOVIES)
        setUpNavItem(R.id.nav_shows, CollectionType.TVSHOWS)
    }

    private fun browseFragment(): MainBrowseFragment? =
        supportFragmentManager.findFragmentById(R.id.main_browse_fragment) as? MainBrowseFragment

    private fun setUpNavItem(viewId: Int, target: CollectionType?) {
        val view = findViewById<TextView>(viewId)
        view.setOnClickListener { browseFragment()?.scrollToLibrary(target) }
        view.setOnFocusChangeListener { _, hasFocus ->
            view.setTextColor(
                ContextCompat.getColor(this, if (hasFocus) R.color.text_primary else R.color.text_secondary),
            )
        }
    }
}
