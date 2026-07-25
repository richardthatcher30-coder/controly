package com.homecontrol.ios.screens.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.homecontrol.core.model.RemoteKey
import com.homecontrol.ios.adb.AdbConnection
import com.homecontrol.ios.adb.KeyOrigin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface ConnectionUiState {
    data object Connecting : ConnectionUiState
    data object Connected : ConnectionUiState
    data class Failed(val message: String) : ConnectionUiState
}

/**
 * Sends key presses over an [AdbConnection] held open for the lifetime of
 * this screen — connecting once on entry (fast, since [connect] reuses an
 * already-trusted key from pairing, no approval wait expected) rather than
 * reconnecting per button press.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(deviceName: String, ipAddress: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val connection = remember { AdbConnection() }
    var connectionState by remember { mutableStateOf<ConnectionUiState>(ConnectionUiState.Connecting) }

    DisposableEffect(ipAddress) {
        scope.launch(Dispatchers.Default) {
            try {
                connection.connect(ipAddress)
                withContext(Dispatchers.Main) { connectionState = ConnectionUiState.Connected }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    connectionState = ConnectionUiState.Failed(e.message ?: "Couldn't connect to $ipAddress")
                }
            }
        }
        onDispose {
            scope.launch(Dispatchers.Default) { connection.disconnect() }
        }
    }

    fun sendKey(key: RemoteKey) {
        if (connectionState !is ConnectionUiState.Connected) return
        scope.launch(Dispatchers.Default) {
            runCatching { connection.shell("input keyevent ${androidKeycodeFor(key)}") }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(deviceName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                },
            )
        },
    ) { padding ->
        when (val state = connectionState) {
            ConnectionUiState.Connecting -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            is ConnectionUiState.Failed -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(text = "Couldn't connect", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = state.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                connection.lastKeyOrigin?.let { origin ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (origin) {
                            KeyOrigin.REUSED_EXISTING -> "(Used existing identity key)"
                            KeyOrigin.FRESHLY_GENERATED -> "(Generated a FRESH identity key -- unexpected if already paired)"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onBack) { Text("Back") }
            }

            ConnectionUiState.Connected -> RemoteControls(
                modifier = Modifier.fillMaxSize().padding(padding),
                onKey = ::sendKey,
                keyOrigin = connection.lastKeyOrigin,
            )
        }
    }
}

@Composable
private fun RemoteControls(modifier: Modifier, onKey: (RemoteKey) -> Unit, keyOrigin: KeyOrigin?) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Diagnostic for the "keeps asking to re-approve" bug report: if this reads
        // "fresh identity key" on a device that was already paired, the Keychain
        // isn't actually persisting the identity key between connection attempts.
        if (keyOrigin != null) {
            Text(
                text = when (keyOrigin) {
                    KeyOrigin.REUSED_EXISTING -> "Connected with existing identity key"
                    KeyOrigin.FRESHLY_GENERATED -> "Connected with a FRESH identity key (unexpected if already paired)"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = { onKey(RemoteKey.POWER) }, modifier = Modifier.fillMaxWidth()) {
            Text("Power")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { onKey(RemoteKey.BACK) }, modifier = Modifier.weight(1f)) { Text("Back") }
            OutlinedButton(onClick = { onKey(RemoteKey.HOME) }, modifier = Modifier.weight(1f)) { Text("Home") }
            OutlinedButton(onClick = { onKey(RemoteKey.MENU) }, modifier = Modifier.weight(1f)) { Text("Menu") }
        }

        DPad(onKey = onKey)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { onKey(RemoteKey.VOLUME_DOWN) }, modifier = Modifier.weight(1f)) { Text("Vol −") }
            OutlinedButton(onClick = { onKey(RemoteKey.MUTE) }, modifier = Modifier.weight(1f)) { Text("Mute") }
            OutlinedButton(onClick = { onKey(RemoteKey.VOLUME_UP) }, modifier = Modifier.weight(1f)) { Text("Vol +") }
        }
    }
}

@Composable
private fun DPad(onKey: (RemoteKey) -> Unit) {
    val buttonSize = 64.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onKey(RemoteKey.DPAD_UP) }, modifier = Modifier.size(buttonSize)) { Text("▲") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onKey(RemoteKey.DPAD_LEFT) }, modifier = Modifier.size(buttonSize)) { Text("◀") }
            Button(onClick = { onKey(RemoteKey.DPAD_CENTER) }, modifier = Modifier.size(buttonSize)) { Text("OK") }
            OutlinedButton(onClick = { onKey(RemoteKey.DPAD_RIGHT) }, modifier = Modifier.size(buttonSize)) { Text("▶") }
        }
        OutlinedButton(onClick = { onKey(RemoteKey.DPAD_DOWN) }, modifier = Modifier.size(buttonSize)) { Text("▼") }
    }
}

private fun androidKeycodeFor(key: RemoteKey): String = when (key) {
    RemoteKey.DPAD_UP -> "KEYCODE_DPAD_UP"
    RemoteKey.DPAD_DOWN -> "KEYCODE_DPAD_DOWN"
    RemoteKey.DPAD_LEFT -> "KEYCODE_DPAD_LEFT"
    RemoteKey.DPAD_RIGHT -> "KEYCODE_DPAD_RIGHT"
    RemoteKey.DPAD_CENTER -> "KEYCODE_DPAD_CENTER"
    RemoteKey.BACK -> "KEYCODE_BACK"
    RemoteKey.HOME -> "KEYCODE_HOME"
    RemoteKey.MENU -> "KEYCODE_MENU"
    RemoteKey.PLAY -> "KEYCODE_MEDIA_PLAY"
    RemoteKey.PAUSE -> "KEYCODE_MEDIA_PAUSE"
    RemoteKey.PLAY_PAUSE -> "KEYCODE_MEDIA_PLAY_PAUSE"
    RemoteKey.STOP -> "KEYCODE_MEDIA_STOP"
    RemoteKey.FAST_FORWARD -> "KEYCODE_MEDIA_FAST_FORWARD"
    RemoteKey.REWIND -> "KEYCODE_MEDIA_REWIND"
    RemoteKey.VOLUME_UP -> "KEYCODE_VOLUME_UP"
    RemoteKey.VOLUME_DOWN -> "KEYCODE_VOLUME_DOWN"
    RemoteKey.MUTE -> "KEYCODE_VOLUME_MUTE"
    RemoteKey.POWER -> "KEYCODE_POWER"
    RemoteKey.BACKSPACE -> "KEYCODE_DEL"
    RemoteKey.CHANNEL_UP -> "KEYCODE_CHANNEL_UP"
    RemoteKey.CHANNEL_DOWN -> "KEYCODE_CHANNEL_DOWN"
    RemoteKey.INPUT_SOURCE -> "KEYCODE_TV_INPUT"
    RemoteKey.SMART_HUB -> "KEYCODE_TV_CONTENTS_MENU"
    RemoteKey.VOICE_ASSIST -> "KEYCODE_ASSIST"
}
