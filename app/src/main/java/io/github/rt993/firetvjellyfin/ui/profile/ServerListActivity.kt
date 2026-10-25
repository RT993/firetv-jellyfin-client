package io.github.rt993.firetvjellyfin.ui.profile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinClientHolder
import io.github.rt993.firetvjellyfin.data.SavedServer
import io.github.rt993.firetvjellyfin.data.ServerHealth
import io.github.rt993.firetvjellyfin.data.ServerHealthMonitor
import io.github.rt993.firetvjellyfin.ui.login.LoginActivity
import io.github.rt993.firetvjellyfin.ui.splash.SplashActivity
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseAccent
import io.github.rt993.firetvjellyfin.ui.theme.fadeToNextScreen
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseBackground
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseHealthy
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseSurface
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseTextPrimary
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseTextSecondary
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseTheme
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseUnreachable

/**
 * Every server this device has ever connected to, reached from the profile picker's server icon.
 * Picking one makes it the active server (its own saved profiles are what the picker shows next);
 * a "+" row adds a brand new one via [LoginActivity] in [LoginActivity.MODE_ADD_SERVER].
 */
class ServerListActivity : ComponentActivity() {

    private var servers by mutableStateOf<List<SavedServer>>(emptyList())
    private var currentUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refresh()
        setContent {
            ServerListScreen(
                servers = servers,
                currentUrl = currentUrl,
                onSelectServer = ::selectServer,
                onAddServer = ::addServer,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        servers = JellyfinClientHolder.savedServers()
        currentUrl = JellyfinClientHolder.currentServerUrl()
    }

    private fun selectServer(server: SavedServer) {
        if (server.url != currentUrl) {
            JellyfinClientHolder.connect(server.url)
            // Explicit navigation rather than just finish()ing back to whatever's underneath -
            // this screen is reached two ways (the profile picker's own server icon, and Home's
            // "Change Server" menu action, which finishes Home and has no picker left to fall
            // back to), and both need to land on a picker showing the newly-selected server's
            // profiles. CLEAR_TOP/SINGLE_TOP reuse an existing picker instance when there is one
            // (the first case) instead of stacking a redundant second one.
            startActivity(
                Intent(this, ProfileSelectActivity::class.java)
                    .putExtra(SplashActivity.EXTRA_FROM_SPLASH, true)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
            fadeToNextScreen()
        }
        finish()
    }

    private fun addServer() {
        startActivity(Intent(this, LoginActivity::class.java).putExtra(LoginActivity.EXTRA_MODE, LoginActivity.MODE_ADD_SERVER))
        fadeToNextScreen()
    }
}

@Composable
private fun ServerListScreen(
    servers: List<SavedServer>,
    currentUrl: String?,
    onSelectServer: (SavedServer) -> Unit,
    onAddServer: () -> Unit,
) {
    val firstFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { firstFocusRequester.requestFocus() }
    }

    TreeHouseTheme {
        Box(Modifier.fillMaxSize().background(TreeHouseBackground)) {
            Column(modifier = Modifier.align(Alignment.Center).width(480.dp)) {
                Text(
                    stringResource(R.string.server_list_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = TreeHouseTextPrimary,
                )
                Spacer(Modifier.height(24.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    itemsIndexed(servers) { index, server ->
                        ServerRow(
                            server = server,
                            isCurrent = server.url == currentUrl,
                            onClick = { onSelectServer(server) },
                            modifier = if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier,
                        )
                    }
                    item {
                        ServerRow(
                            server = null,
                            isCurrent = false,
                            onClick = onAddServer,
                            modifier = if (servers.isEmpty()) Modifier.focusRequester(firstFocusRequester) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

/** [server] null renders the "add a new server" row instead of a saved one. */
@Composable
private fun ServerRow(server: SavedServer?, isCurrent: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) TreeHouseAccent.copy(alpha = 0.3f) else TreeHouseSurface)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (server == null) {
            AddServerTile()
        } else {
            // Only the currently-connected server has a live health check running (see
            // ServerHealthMonitor) - every other saved server is just a stored URL nobody's
            // actively pinging, so only this row's light actually pulses with real status.
            val health by ServerHealthMonitor.status.collectAsState()
            ServerRackTile(isCurrent = isCurrent, health = health)
        }
        Spacer(Modifier.width(20.dp))
        Column {
            Text(
                server?.name?.takeIf { it.isNotBlank() } ?: server?.url ?: stringResource(R.string.server_add),
                color = TreeHouseTextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (server != null && server.name.isNotBlank()) {
                Text(server.url, color = TreeHouseTextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (isCurrent) {
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.server_current), color = TreeHouseAccent, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * A compact version of Home's Server Status rack graphic (see HomeScreen's ServerRackIcon) sized
 * for a list row - two bars instead of three, the top one's light pulsing only when [isCurrent],
 * colored by the live [health] check. A non-current row has no live health to show, so its lights
 * stay a static, dim grey rather than pulsing with a status nobody's actually checking.
 */
@Composable
private fun ServerRackTile(isCurrent: Boolean, health: ServerHealth, modifier: Modifier = Modifier) {
    val dotColor = if (!isCurrent) {
        TreeHouseTextSecondary
    } else {
        when (health) {
            ServerHealth.HEALTHY -> TreeHouseHealthy
            ServerHealth.UNREACHABLE -> TreeHouseUnreachable
            ServerHealth.UNKNOWN -> TreeHouseTextSecondary
        }
    }
    val infiniteTransition = rememberInfiniteTransition(label = "serverListPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "serverListPulseAlpha",
    )
    Column(modifier = modifier.width(52.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(2) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(TreeHouseTextSecondary.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .background(TreeHouseTextSecondary.copy(alpha = 0.28f), RoundedCornerShape(1.5.dp)),
                )
                Spacer(Modifier.width(5.dp))
                val dotAlpha = when {
                    isCurrent && index == 0 -> pulseAlpha
                    isCurrent -> 0.5f
                    else -> 0.35f
                }
                Box(Modifier.size(5.dp).background(dotColor.copy(alpha = dotAlpha), CircleShape))
            }
        }
    }
}

/** A "+" in the same rounded tile shape as [ServerRackTile], for the add-a-new-server row. */
@Composable
private fun AddServerTile(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(52.dp)
            .height(32.dp)
            .background(TreeHouseTextSecondary.copy(alpha = 0.16f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("+", color = TreeHouseAccent, style = MaterialTheme.typography.headlineSmall)
    }
}
