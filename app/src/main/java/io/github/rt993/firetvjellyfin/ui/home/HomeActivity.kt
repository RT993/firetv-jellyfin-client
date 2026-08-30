package io.github.rt993.firetvjellyfin.ui.home

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
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

    /**
     * Leanback's BrowseSupportFragment keeps D-pad focus contained within its own row content
     * and does not hand DPAD_UP off to sibling views outside it (the top bar lives in the
     * Activity's own layout, not inside BrowseSupportFragment's view tree), so pressing up from
     * the top row never reaches the nav bar via normal focus search. Catch it explicitly here:
     * this only fires when nothing below already consumed the key (i.e. focus had nowhere left
     * to go), so it's safe to always redirect to the nav bar rather than trying to detect
     * "currently on the top row".
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && !isFocusInTopBar()) {
            findViewById<View>(R.id.nav_home).requestFocus()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun isFocusInTopBar(): Boolean {
        val topBar = findViewById<View>(R.id.top_bar)
        var view = currentFocus
        while (view != null) {
            if (view === topBar) return true
            view = view.parent as? View
        }
        return false
    }

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
