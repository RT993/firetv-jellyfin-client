package io.github.rt993.firetvjellyfin.ui.profile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinClientHolder
import io.github.rt993.firetvjellyfin.data.SavedProfile
import io.github.rt993.firetvjellyfin.ui.home.HomeActivity
import io.github.rt993.firetvjellyfin.ui.login.LoginActivity
import io.github.rt993.firetvjellyfin.ui.splash.SplashActivity
import io.github.rt993.firetvjellyfin.ui.theme.FocusableCard
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseAccent
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseBackground
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseSurface
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseTextPrimary
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseTheme

/**
 * "Who's watching?" - shown after every splash intro, regardless of whether a session happens to
 * already be pre-warmed (see [JellyfinClientHolder.hasAnyProfiles]/[SplashActivity]), so switching
 * between people sharing one server (or one device) is always a couple of D-pad presses away
 * rather than requiring a full sign-out first.
 *
 * Shows the profiles saved for whichever server is currently active, a tile to add another
 * profile to that same server, and a small server icon (bottom-right) to switch servers entirely.
 */
class ProfileSelectActivity : ComponentActivity() {

    private var profiles by mutableStateOf<List<SavedProfile>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!intent.getBooleanExtra(SplashActivity.EXTRA_FROM_SPLASH, false)) {
            startActivity(Intent(this, SplashActivity::class.java))
            finish()
            return
        }

        profiles = JellyfinClientHolder.savedProfilesForCurrentServer()

        setContent {
            ProfileSelectScreen(
                profiles = profiles,
                onSelectProfile = ::openHome,
                onAddProfile = ::openAddProfile,
                onSwitchServer = ::openServerList,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Refreshes after returning from "add a profile" or "switch server" - both can change
        // what this screen should show without this Activity ever being recreated.
        profiles = JellyfinClientHolder.savedProfilesForCurrentServer()
    }

    private fun openHome(profile: SavedProfile) {
        JellyfinClientHolder.activateProfile(profile)
        startActivity(Intent(this, HomeActivity::class.java))
        // Finished, not just backgrounded - pressing Back from Home should exit the app the same
        // way it always has, not bounce back to this picker.
        finish()
    }

    private fun openAddProfile() {
        startActivity(Intent(this, LoginActivity::class.java).putExtra(LoginActivity.EXTRA_MODE, LoginActivity.MODE_ADD_PROFILE))
    }

    private fun openServerList() {
        startActivity(Intent(this, ServerListActivity::class.java))
    }
}

@Composable
private fun ProfileSelectScreen(
    profiles: List<SavedProfile>,
    onSelectProfile: (SavedProfile) -> Unit,
    onAddProfile: () -> Unit,
    onSwitchServer: () -> Unit,
) {
    val firstFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { firstFocusRequester.requestFocus() }
    }

    TreeHouseTheme {
        Box(Modifier.fillMaxSize().background(TreeHouseBackground)) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.profile_select_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = TreeHouseTextPrimary,
                )
                Spacer(Modifier.height(40.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    profiles.forEachIndexed { index, profile ->
                        ProfileTile(
                            name = profile.username,
                            icon = R.drawable.ic_profile,
                            onClick = { onSelectProfile(profile) },
                            modifier = if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier,
                        )
                    }
                    ProfileTile(
                        name = stringResource(R.string.profile_add),
                        icon = R.drawable.ic_add,
                        onClick = onAddProfile,
                        modifier = if (profiles.isEmpty()) Modifier.focusRequester(firstFocusRequester) else Modifier,
                    )
                }
            }

            ServerIconButton(
                onClick = onSwitchServer,
                modifier = Modifier.align(Alignment.BottomEnd).padding(32.dp),
            )
        }
    }
}

@Composable
private fun ProfileTile(name: String, icon: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // The incoming modifier (carrying the initial-focus FocusRequester, when passed) has to land
    // on FocusableCard itself, not this wrapping Column - a FocusRequester only works when it's on
    // the same modifier chain as an actual focusable node.
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FocusableCard(onClick = onClick, modifier = modifier.size(140.dp)) {
            Box(Modifier.fillMaxSize().background(TreeHouseSurface), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = ImageVector.vectorResource(id = icon),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            name,
            color = TreeHouseTextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ServerIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(if (isFocused) TreeHouseAccent.copy(alpha = 0.3f) else TreeHouseSurface)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(id = R.drawable.ic_server),
            contentDescription = stringResource(R.string.profile_switch_server),
            tint = Color.Unspecified,
        )
    }
}
