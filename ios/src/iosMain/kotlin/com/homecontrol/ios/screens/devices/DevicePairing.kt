package com.homecontrol.ios.screens.devices

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.homecontrol.core.model.DeviceCapabilities
import com.homecontrol.core.model.DeviceType
import com.homecontrol.core.model.PairedDevice
import com.homecontrol.ios.adb.AdbApprovalTimeoutException
import com.homecontrol.ios.adb.AdbConnection
import com.homecontrol.ios.adb.KeyOrigin
import com.homecontrol.ios.storage.PairedDeviceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val MANUAL_DEVICE_TYPE_OPTIONS: List<Pair<String, DeviceType>> = listOf(
    "Fire TV" to DeviceType.FIRE_TV,
    "Android TV / Google TV" to DeviceType.ANDROID_TV,
    "Sony TV" to DeviceType.SONY_TV,
    "Samsung TV" to DeviceType.SAMSUNG_TV,
    "Windows PC" to DeviceType.WINDOWS_PC,
)

/** Only these use the ADB-over-TCP pairing this iOS build actually implements — see [AdbConnection]'s doc comment. */
val ADB_SUPPORTED_TYPES = setOf(DeviceType.ANDROID_TV, DeviceType.GOOGLE_TV, DeviceType.FIRE_TV)

fun deviceTypeLabel(deviceType: DeviceType): String = when (deviceType) {
    DeviceType.GOOGLE_TV -> "Google TV"
    DeviceType.UNKNOWN -> "Unknown device"
    else -> MANUAL_DEVICE_TYPE_OPTIONS.firstOrNull { it.second == deviceType }?.first ?: deviceType.name
}

sealed interface PairingUiState {
    data object Idle : PairingUiState
    data class InProgress(val deviceName: String) : PairingUiState
    data class Success(val deviceName: String, val keyOrigin: KeyOrigin?, val retrieveMissStatus: Long?) : PairingUiState
    data class Failed(val deviceName: String, val reason: String) : PairingUiState
}

/**
 * Shared pairing flow used by both [DeviceDiscoveryScreen] (tap a found
 * device) and [AddDeviceManuallyScreen] (fill in an IP by hand) — each
 * screen gets its own controller instance via [rememberPairingController],
 * but the pairing logic itself (ADB handshake, persisting to
 * [PairedDeviceStore], the in-progress/success/failure dialog states) only
 * needs to exist once.
 */
class PairingController internal constructor(
    private val scope: CoroutineScope,
    private val store: PairedDeviceStore,
    private val onPaired: () -> Unit,
) {
    var state by mutableStateOf<PairingUiState>(PairingUiState.Idle)
        private set

    fun start(ip: String, deviceName: String, selectedType: DeviceType) {
        if (selectedType !in ADB_SUPPORTED_TYPES) {
            state = PairingUiState.Failed(
                deviceName,
                "${deviceTypeLabel(selectedType)} pairing isn't supported on iOS yet — coming soon.",
            )
            return
        }
        state = PairingUiState.InProgress(deviceName)
        // Dispatchers.IO is internal on Kotlin/Native (not part of the public API for
        // this coroutines version's iOS target) -- Default is the portable choice here.
        scope.launch(Dispatchers.Default) {
            try {
                val connection = AdbConnection()
                connection.pair(ip)
                store.add(
                    PairedDevice(
                        id = "adb:$ip",
                        name = deviceName,
                        manufacturer = "",
                        model = "",
                        ipAddress = ip,
                        macAddress = null,
                        deviceType = selectedType,
                        firmwareVersion = null,
                        capabilities = DeviceCapabilities.NONE,
                        isOnline = true,
                        pluginId = "androidtv-adb-ios",
                    ),
                )
                withContext(Dispatchers.Main) {
                    state = PairingUiState.Success(deviceName, connection.lastKeyOrigin, connection.lastRetrieveMissStatus)
                }
            } catch (e: AdbApprovalTimeoutException) {
                withContext(Dispatchers.Main) {
                    state = PairingUiState.Failed(deviceName, "Approval timed out — check the TV's screen and try again.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    state = PairingUiState.Failed(deviceName, e.message ?: "Couldn't connect to $ip")
                }
            }
        }
    }

    fun dismiss() {
        val wasSuccess = state is PairingUiState.Success
        state = PairingUiState.Idle
        if (wasSuccess) onPaired()
    }
}

@Composable
fun rememberPairingController(onPaired: () -> Unit): PairingController {
    val scope = rememberCoroutineScope()
    val store = remember { PairedDeviceStore() }
    return remember { PairingController(scope, store, onPaired) }
}

@Composable
fun PairingDialogHost(controller: PairingController) {
    when (val state = controller.state) {
        is PairingUiState.InProgress -> PairingDialog(
            title = "Pairing with ${state.deviceName}",
            body = "Check the TV's screen and select \"Allow\" (ideally \"Always allow\") on the " +
                "debugging prompt. This can take up to two minutes.",
            onDismiss = null,
        )

        is PairingUiState.Success -> PairingDialog(
            title = "Paired with ${state.deviceName}",
            body = "You can now control it from the dashboard." +
                // Diagnostic for the "keeps asking to re-approve" bug report: if this
                // reads "freshly generated" on anything but the very first-ever pairing
                // attempt, the identity key isn't actually persisting in the Keychain --
                // retrieveMissStatus is the raw Keychain OSStatus that made it look
                // like nothing was stored, straight from SecItemCopyMatching.
                when (state.keyOrigin) {
                    KeyOrigin.REUSED_EXISTING -> "\n\n(Reused existing identity key.)"
                    KeyOrigin.FRESHLY_GENERATED -> "\n\n(Generated a new identity key for this pairing." +
                        (state.retrieveMissStatus?.let { " Keychain lookup status: $it." } ?: "") + ")"
                    null -> ""
                },
            onDismiss = { controller.dismiss() },
        )

        is PairingUiState.Failed -> PairingDialog(
            title = "Couldn't pair with ${state.deviceName}",
            body = state.reason,
            onDismiss = { controller.dismiss() },
        )

        PairingUiState.Idle -> Unit
    }
}

@Composable
private fun PairingDialog(title: String, body: String, onDismiss: (() -> Unit)?) {
    AlertDialog(
        onDismissRequest = { onDismiss?.invoke() },
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            if (onDismiss != null) {
                TextButton(onClick = onDismiss) { Text("OK") }
            }
        },
    )
}
