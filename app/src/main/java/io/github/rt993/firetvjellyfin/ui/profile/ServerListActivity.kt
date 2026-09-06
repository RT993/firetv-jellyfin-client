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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import io.github.rt993.firetvjellyfin.data.SavedServer
import io.github.rt993.firetvjellyfin.ui.login.LoginActivity
import io.github.rt993.firetvjellyfin.ui.splash.SplashActivity
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseAccent
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseBackground
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseSurface
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseTextPrimary
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseTextSecondary
import io.github.rt993.firetvjellyfin.ui.theme.TreeHouseTheme

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
        }
        finish()
    }

    private fun addServer() {
        startActivity(Intent(this, LoginActivity::class.java).putExtra(LoginActivity.EXTRA_MODE, LoginActivity.MODE_ADD_SERVER))
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
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) TreeHouseAccent.copy(alpha = 0.3f) else TreeHouseSurface)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(id = if (server == null) R.drawable.ic_add else R.drawable.ic_server),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(16.dp))
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
