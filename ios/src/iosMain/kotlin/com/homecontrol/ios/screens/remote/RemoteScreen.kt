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
import com.homecontrol.core.model.DeviceType
import com.homecontrol.core.model.RemoteKey
import com.homecontrol.ios.adb.AdbConnection
import com.homecontrol.ios.adb.KeyOrigin
import com.homecontrol.ios.companion.CompanionConnection
import com.homecontrol.ios.samsung.SamsungConnection
import com.homecontrol.ios.screens.devices.COMPANION_SUPPORTED_TYPES
import com.homecontrol.ios.screens.devices.SAMSUNG_SUPPORTED_TYPES
import com.homecontrol.ios.screens.devices.SONY_SUPPORTED_TYPES
import com.homecontrol.ios.sony.SonyConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidBecomeActiveNotification

private sealed interface ConnectionUiState {
    data object Connecting : ConnectionUiState
    data object Connected : ConnectionUiState
    data class Failed(val message: String) : ConnectionUiState
}

private sealed interface RemoteTransportKind {
    data object Adb : RemoteTransportKind
    data object WindowsCompanion : RemoteTransportKind
    data object Samsung : RemoteTransportKind
    data object Sony : RemoteTransportKind
}

private fun transportKindFor(deviceType: DeviceType): RemoteTransportKind = when {
    deviceType in COMPANION_SUPPORTED_TYPES -> RemoteTransportKind.WindowsCompanion
    deviceType in SAMSUNG_SUPPORTED_TYPES -> RemoteTransportKind.Samsung
    deviceType in SONY_SUPPORTED_TYPES -> RemoteTransportKind.Sony
    else -> RemoteTransportKind.Adb
}

/**
 * Sends key presses over an [AdbConnection] (ADB-supported types), a
 * [CompanionConnection] (Windows PC, see [COMPANION_SUPPORTED_TYPES]), a
 * [SamsungConnection] (see [SAMSUNG_SUPPORTED_TYPES]), or a [SonyConnection]
 * (see [SONY_SUPPORTED_TYPES]) held open for the lifetime of this screen —
 * connecting once on entry (fast, since [connect][AdbConnection.connect]
 * reuses an already-trusted key from pairing, no approval wait expected)
 * rather than reconnecting per button press. Only one of the four connection
 * objects is ever created, picked once from [deviceType] -- see [transportKindFor].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(deviceName: String, ipAddress: String, deviceType: DeviceType, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val transportKind = remember(deviceType) { transportKindFor(deviceType) }
    val adbConnection = remember { if (transportKind == RemoteTransportKind.Adb) AdbConnection() else null }
    val companionConnection = remember { if (transportKind == RemoteTransportKind.WindowsCompanion) CompanionConnection() else null }
    val samsungConnection = remember { if (transportKind == RemoteTransportKind.Samsung) SamsungConnection() else null }
    val sonyConnection = remember { if (transportKind == RemoteTransportKind.Sony) SonyConnection() else null }
    var connectionState by remember { mutableStateOf<ConnectionUiState>(ConnectionUiState.Connecting) }

    DisposableEffect(ipAddress) {
        // Guards against two overlapping connect() calls to the same device --
        // e.g. the app backgrounding and returning (see the
        // UIApplicationDidBecomeActiveNotification observer below) while the
        // initial connect from screen-entry is still waiting. AdbConnection's
        // own connect() already reuses a live socket instead of always
        // reopening, but that only helps once a connection exists; a second
        // *concurrent* attempt while the first is still mid-handshake would
        // still race it. Plain var, not a Job, because both call sites here
        // (this effect's launch and the notification callback below) run on
        // the main thread -- Compose's own and NSNotificationCenter's queue =
        // null delivery are both main-thread, so there's no data race to guard
        // against beyond "don't start a second one while one is running".
        var connecting = false

        fun startConnect() {
            if (connecting) return
            connecting = true
            scope.launch(Dispatchers.Default) {
                try {
                    when (transportKind) {
                        RemoteTransportKind.WindowsCompanion -> companionConnection!!.connect(ipAddress)
                        RemoteTransportKind.Samsung -> samsungConnection!!.connect(ipAddress)
                        RemoteTransportKind.Sony -> sonyConnection!!.connect(ipAddress)
                        RemoteTransportKind.Adb -> adbConnection!!.connect(ipAddress)
                    }
                    withContext(Dispatchers.Main) { connectionState = ConnectionUiState.Connected }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        connectionState = ConnectionUiState.Failed(e.message ?: "Couldn't connect to $ipAddress")
                    }
                } finally {
                    connecting = false
                }
            }
        }

        startConnect()

        // The initial connect above only runs once, on screen entry -- it
        // doesn't notice a connection that died while this screen was in the
        // background (phone locked, user switched apps). Re-validate on every
        // return to the foreground rather than leaving a dead remote until the
        // user notices and manually goes back and re-enters the screen.
        val activeObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = null,
        ) { _ -> startConnect() }

        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(activeObserver)
            scope.launch(Dispatchers.Default) {
                when (transportKind) {
                    RemoteTransportKind.WindowsCompanion -> companionConnection?.disconnect()
                    RemoteTransportKind.Samsung -> samsungConnection?.disconnect()
                    RemoteTransportKind.Sony -> sonyConnection?.disconnect()
                    RemoteTransportKind.Adb -> adbConnection?.disconnect()
                }
            }
        }
    }

    fun sendKey(key: RemoteKey) {
        if (connectionState !is ConnectionUiState.Connected) return
        scope.launch(Dispatchers.Default) {
            runCatching {
                when (transportKind) {
                    RemoteTransportKind.WindowsCompanion -> companionConnection!!.sendCommand("key_event", buildJsonObject { put("key", JsonPrimitive(key.name)) })
                    RemoteTransportKind.Samsung -> samsungConnection!!.sendKey(key)
                    RemoteTransportKind.Sony -> sonyConnection!!.sendKey(key)
                    RemoteTransportKind.Adb -> adbConnection!!.shell("input keyevent ${androidKeycodeFor(key)}")
                }
            }
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
                adbConnection?.lastKeyOrigin?.let { origin ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = keyOriginDiagnosticText(origin, adbConnection.lastRetrieveMissStatus),
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
                keyOrigin = adbConnection?.lastKeyOrigin,
                retrieveMissStatus = adbConnection?.lastRetrieveMissStatus,
            )
        }
    }
}

@Composable
private fun RemoteControls(
    modifier: Modifier,
    onKey: (RemoteKey) -> Unit,
    keyOrigin: KeyOrigin?,
    retrieveMissStatus: Long?,
) {
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
                text = keyOriginDiagnosticText(keyOrigin, retrieveMissStatus, connected = true),
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

/**
 * retrieveMissStatus is the raw Keychain OSStatus from [SecureKeyStorage.lastRetrieveMissStatus]
 * when [origin] is [KeyOrigin.FRESHLY_GENERATED] -- the actual reason the stored identity key
 * looked missing, surfaced directly instead of guessing at the "always re-asks to pair" bug.
 */
private fun keyOriginDiagnosticText(origin: KeyOrigin, retrieveMissStatus: Long?, connected: Boolean = false): String =
    when (origin) {
        KeyOrigin.REUSED_EXISTING -> if (connected) "Connected with existing identity key" else "(Used existing identity key)"
        KeyOrigin.FRESHLY_GENERATED -> {
            val base = if (connected) {
                "Connected with a FRESH identity key (unexpected if already paired)"
            } else {
                "(Generated a FRESH identity key -- unexpected if already paired)"
            }
            base + (retrieveMissStatus?.let { " [Keychain status: $it]" } ?: "")
        }
    }
