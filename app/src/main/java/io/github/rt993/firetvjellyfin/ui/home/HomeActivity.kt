package io.github.rt993.firetvjellyfin.ui.home

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinClientHolder
import io.github.rt993.firetvjellyfin.ui.library.LibraryGridActivity
import io.github.rt993.firetvjellyfin.ui.login.LoginActivity
import io.github.rt993.firetvjellyfin.ui.splash.SplashActivity
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.UUID

private const val ICON_SIZE_DP = 56
private const val ICON_PADDING_DP = 14

/**
 * Home screen chrome: a fixed left nav rail (avatar, Search, Home, one icon per library, Settings)
 * beside [MainBrowseFragment]'s row content - replaces the old top bar entirely.
 */
class HomeActivity : FragmentActivity(R.layout.activity_home) {

    private lateinit var sidebar: LinearLayout
    private lateinit var libraryContainer: LinearLayout
    private lateinit var userMenu: View
    private lateinit var userMenuBackCallback: OnBackPressedCallback
    private lateinit var navHome: View
    private lateinit var navSettings: View
    private lateinit var lastSidebarFocus: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sidebar = findViewById(R.id.sidebar)
        userMenu = findViewById(R.id.user_menu)

        // BrowseSupportFragment hosts its row content via a child fragment transaction, which
        // doesn't reliably attach when the fragment itself is declared via a static <fragment> XML
        // tag - add it programmatically into a FragmentContainerView instead. Hang onto the freshly
        // created instance directly (rather than looking it up again right after commit()) since
        // the transaction hasn't necessarily executed yet at that point.
        val fragment = if (savedInstanceState == null) {
            MainBrowseFragment().also {
                supportFragmentManager.beginTransaction().replace(R.id.main_browse_fragment, it).commit()
            }
        } else {
            browseFragment()
        }

        buildSidebar(fragment)

        userMenuBackCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = closeUserMenu()
        }
        onBackPressedDispatcher.addCallback(this, userMenuBackCallback)
        findViewById<View>(R.id.user_menu_info).setOnClickListener { showAccountInfo() }
        findViewById<View>(R.id.user_menu_logout).setOnClickListener { logOut() }
    }

    private fun buildSidebar(fragment: MainBrowseFragment?) {
        sidebar.addView(buildAvatar())
        sidebar.addView(gap(32))

        sidebar.addView(
            sidebarIcon(R.drawable.ic_nav_search, getString(R.string.nav_search)) {
                Toast.makeText(this, R.string.nav_search_unavailable, Toast.LENGTH_SHORT).show()
            },
        )
        sidebar.addView(gap(8))

        navHome = sidebarIcon(R.drawable.ic_nav_home, getString(R.string.nav_home)) {
            browseFragment()?.showHome()
        }
        sidebar.addView(navHome)
        sidebar.addView(gap(8))

        libraryContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        sidebar.addView(libraryContainer)

        sidebar.addView(Space(this).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) })

        navSettings = sidebarIcon(R.drawable.ic_nav_settings, getString(R.string.nav_settings)) { toggleUserMenu() }
        sidebar.addView(navSettings)

        lastSidebarFocus = navHome
        fragment?.onLibrariesLoaded = { views -> populateLibraryIcons(views) }
    }

    private fun populateLibraryIcons(views: List<BaseItemDto>) {
        libraryContainer.removeAllViews()
        views.forEach { view ->
            libraryContainer.addView(gap(8))
            libraryContainer.addView(
                sidebarIcon(R.drawable.ic_nav_library, view.name.orEmpty()) {
                    openLibraryGrid(view.id, view.name.orEmpty())
                },
            )
        }
    }

    private fun buildAvatar(): View {
        val size = dpToPx(ICON_SIZE_DP)
        val container = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(size, size)
            background = ContextCompat.getDrawable(this@HomeActivity, R.drawable.bg_avatar_circle)
        }
        val initial = (JellyfinClientHolder.currentUsername()?.firstOrNull()?.uppercaseChar() ?: '?').toString()
        container.addView(
            TextView(this).apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
                text = initial
                textSize = 20f
                setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.text_primary))
                setTypeface(typeface, Typeface.BOLD)
            },
        )
        return container
    }

    private fun sidebarIcon(iconRes: Int, description: String, onClick: () -> Unit): View {
        val size = dpToPx(ICON_SIZE_DP)
        val padding = dpToPx(ICON_PADDING_DP)
        return ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(size, size)
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(padding, padding, padding, padding)
            background = ContextCompat.getDrawable(this@HomeActivity, R.drawable.nav_item_bg)
            isFocusable = true
            contentDescription = description
            setOnClickListener { onClick() }
            setOnFocusChangeListener { view, hasFocus -> if (hasFocus) lastSidebarFocus = view }
        }
    }

    private fun gap(dp: Int): View =
        Space(this).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(dp)) }

    private fun dpToPx(dp: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()

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
        navSettings.requestFocus()
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

    private fun openLibraryGrid(libraryId: UUID, title: String) {
        startActivity(
            Intent(this, LibraryGridActivity::class.java)
                .putExtra(LibraryGridActivity.EXTRA_LIBRARY_ID, libraryId.toString())
                .putExtra(LibraryGridActivity.EXTRA_TITLE, title),
        )
    }

    /**
     * Leanback's BrowseSupportFragment/BrowseFrameLayout keeps D-pad focus contained within its own
     * row content and swallows an arrow key itself (consumes it, returns true) even when there's
     * nowhere left for it to move focus to - so a plain onKeyDown() override (which only fires for
     * events nothing else consumed) never sees DPAD_LEFT at the leftmost card of a row. Peek at
     * what default focus search *would* do before the row content gets a chance to swallow the
     * event: if it can't find anything further left (the leftmost-column case), redirect to the
     * sidebar ourselves; otherwise let the event through so normal left/right scrolling within a
     * row keeps working exactly as before.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN && userMenu.visibility != View.VISIBLE) {
            if (!isFocusInSidebar() && currentFocus?.focusSearch(View.FOCUS_LEFT) == null) {
                lastSidebarFocus.requestFocus()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun isFocusInSidebar(): Boolean {
        var view = currentFocus
        while (view != null) {
            if (view === sidebar) return true
            view = view.parent as? View
        }
        return false
    }
}
