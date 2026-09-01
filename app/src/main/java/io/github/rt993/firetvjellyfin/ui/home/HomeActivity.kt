package io.github.rt993.firetvjellyfin.ui.home

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinClientHolder
import io.github.rt993.firetvjellyfin.ui.login.LoginActivity
import io.github.rt993.firetvjellyfin.ui.splash.SplashActivity
import org.jellyfin.sdk.model.api.CollectionType

class HomeActivity : FragmentActivity(R.layout.activity_home) {

    private lateinit var userMenu: View
    private lateinit var userMenuBackCallback: OnBackPressedCallback

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

        val userTrigger = findViewById<TextView>(R.id.topbar_username)
        userTrigger.text = JellyfinClientHolder.currentUsername().orEmpty()
        userMenu = findViewById(R.id.user_menu)
        userTrigger.setOnClickListener { toggleUserMenu() }
        findViewById<View>(R.id.user_menu_info).setOnClickListener { showAccountInfo() }
        findViewById<View>(R.id.user_menu_logout).setOnClickListener { logOut() }

        userMenuBackCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = closeUserMenu()
        }
        onBackPressedDispatcher.addCallback(this, userMenuBackCallback)

        setUpNavItem(R.id.nav_home, null)
        setUpNavItem(R.id.nav_movies, CollectionType.MOVIES)
        setUpNavItem(R.id.nav_shows, CollectionType.TVSHOWS)
    }

    private fun toggleUserMenu() {
        if (userMenu.visibility == View.VISIBLE) closeUserMenu() else openUserMenu()
    }

    private fun openUserMenu() {
        userMenu.visibility = View.VISIBLE
        userMenuBackCallback.isEnabled = true
        findViewById<View>(R.id.user_menu_info).requestFocus()
    }

    private fun closeUserMenu() {
        userMenu.visibility = View.GONE
        userMenuBackCallback.isEnabled = false
        findViewById<View>(R.id.topbar_username).requestFocus()
    }

    private fun showAccountInfo() {
        val username = JellyfinClientHolder.currentUsername().orEmpty()
        val serverUrl = JellyfinClientHolder.api?.baseUrl.orEmpty()
        Toast.makeText(
            this,
            getString(R.string.user_menu_info_format, username, serverUrl),
            Toast.LENGTH_LONG,
        ).show()
        closeUserMenu()
    }

    private fun logOut() {
        JellyfinClientHolder.signOut()
        startActivity(
            Intent(this, LoginActivity::class.java).putExtra(SplashActivity.EXTRA_FROM_SPLASH, true),
        )
        finish()
    }

    private fun browseFragment(): MainBrowseFragment? =
        supportFragmentManager.findFragmentById(R.id.main_browse_fragment) as? MainBrowseFragment

    /**
     * Leanback's BrowseSupportFragment/BrowseFrameLayout keeps D-pad focus contained within its
     * own row content and swallows DPAD_UP itself (returns true / consumes it) even when there's
     * nowhere left for it to move focus to, rather than letting it bubble back up unconsumed - so
     * catching it in onKeyDown() (which only fires for events nothing else consumed) never fired.
     * dispatchKeyEvent() runs before the view hierarchy gets a chance to swallow anything, so
     * intercept it there instead - but only when actually on the top row and not already in the
     * top bar, so DPAD_UP still moves between rows normally everywhere else.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN && userMenu.visibility != View.VISIBLE) {
            val inTopBar = isFocusInTopBar()
            val fragment = browseFragment()
            val atTop = fragment?.isAtTopRow()
            Log.d(TAG, "DPAD_UP: inTopBar=$inTopBar fragment=$fragment atTopRow=$atTop currentFocus=$currentFocus")
            if (!inTopBar && atTop == true) {
                findViewById<View>(R.id.nav_home).requestFocus()
                Log.d(TAG, "DPAD_UP: redirected to nav_home, now focused=${findViewById<View>(R.id.nav_home).isFocused}")
                return true
            }
        }
        return super.dispatchKeyEvent(event)
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

    private companion object {
        const val TAG = "HomeActivity"
    }

    private fun setUpNavItem(viewId: Int, target: CollectionType?) {
        val view = findViewById<TextView>(viewId)
        view.setOnClickListener {
            Log.d(TAG, "nav item ${resources.getResourceEntryName(viewId)} clicked -> showLibrary($target)")
            browseFragment()?.showLibrary(target)
        }
        view.setOnFocusChangeListener { _, hasFocus ->
            view.setTextColor(
                ContextCompat.getColor(this, if (hasFocus) R.color.text_primary else R.color.text_secondary),
            )
        }
    }
}
