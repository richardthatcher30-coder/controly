package com.homecontrol.ios.screens.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.homecontrol.core.model.DeviceCapabilities
import com.homecontrol.core.model.DeviceType
import com.homecontrol.core.model.PairedDevice
import com.homecontrol.ios.adb.AdbApprovalTimeoutException
import com.homecontrol.ios.adb.AdbConnection
import com.homecontrol.ios.storage.PairedDeviceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val MANUAL_DEVICE_TYPE_OPTIONS: List<Pair<String, DeviceType>> = listOf(
    "Fire TV" to DeviceType.FIRE_TV,
    "Android TV / Google TV" to DeviceType.ANDROID_TV,
    "Sony TV" to DeviceType.SONY_TV,
    "Samsung TV" to DeviceType.SAMSUNG_TV,
    "Windows PC" to DeviceType.WINDOWS_PC,
)

/** Only these use the ADB-over-TCP pairing this iOS build actually implements — see [AdbConnection]'s doc comment. */
private val ADB_SUPPORTED_TYPES = setOf(DeviceType.ANDROID_TV, DeviceType.GOOGLE_TV, DeviceType.FIRE_TV)

private sealed interface PairingUiState {
    data object Idle : PairingUiState
    data class InProgress(val deviceName: String) : PairingUiState
    data class Success(val deviceName: String) : PairingUiState
    data class Failed(val deviceName: String, val reason: String) : PairingUiState
}

/**
 * Manual "Add by IP" form + pairing flow. True auto-discovery (mDNS/SSDP,
 * matching Android's `DevicesScreen`) is deferred — see this build's own
 * scope notes — so manual entry is the primary path on iOS for now, exactly
 * like Android's own "Add by IP" fallback for devices that don't advertise
 * themselves (Fire TV included).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceScreen(onBack: () -> Unit, onPaired: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var ipAddress by remember { mutableStateOf("") }
    var deviceType by remember { mutableStateOf(DeviceType.ANDROID_TV) }
    var pairingState by remember { mutableStateOf<PairingUiState>(PairingUiState.Idle) }
    val scope = rememberCoroutineScope()
    val store = remember { PairedDeviceStore() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add device") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Auto-discovery is coming in a future update — for now, enter your " +
                    "device's IP address directly.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = ipAddress,
                onValueChange = { ipAddress = it },
                label = { Text("IP address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(24.dp))

            Text(text = "Device type", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            MANUAL_DEVICE_TYPE_OPTIONS.forEach { (label, type) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { deviceType = type }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = deviceType == type, onClick = { deviceType = type })
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = label)
                    if (type !in ADB_SUPPORTED_TYPES) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "(coming soon)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    val trimmedIp = ipAddress.trim()
                    val deviceName = name.trim().ifEmpty { deviceTypeLabel(deviceType) }
                    val selectedType = deviceType

                    if (selectedType !in ADB_SUPPORTED_TYPES) {
                        pairingState = PairingUiState.Failed(
                            deviceName,
                            "${deviceTypeLabel(selectedType)} pairing isn't supported on iOS yet — coming soon.",
                        )
                        return@Button
                    }

                    pairingState = PairingUiState.InProgress(deviceName)
                    scope.launch(Dispatchers.IO) {
                        try {
                            AdbConnection().pair(trimmedIp)
                            store.add(
                                PairedDevice(
                                    id = "adb:$trimmedIp",
                                    name = deviceName,
                                    manufacturer = "",
                                    model = "",
                                    ipAddress = trimmedIp,
                                    macAddress = null,
                                    deviceType = selectedType,
                                    firmwareVersion = null,
                                    capabilities = DeviceCapabilities.NONE,
                                    isOnline = true,
                                    pluginId = "androidtv-adb-ios",
                                ),
                            )
                            withContext(Dispatchers.Main) {
                                pairingState = PairingUiState.Success(deviceName)
                            }
                        } catch (e: AdbApprovalTimeoutException) {
                            withContext(Dispatchers.Main) {
                                pairingState = PairingUiState.Failed(
                                    deviceName,
                                    "Approval timed out — check the TV's screen and try again.",
                                )
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                pairingState = PairingUiState.Failed(
                                    deviceName,
                                    e.message ?: "Couldn't connect to $trimmedIp",
                                )
                            }
                        }
                    }
                },
                enabled = ipAddress.isNotBlank() && pairingState !is PairingUiState.InProgress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Pair")
            }
        }
    }

    when (val state = pairingState) {
        is PairingUiState.InProgress -> PairingDialog(
            title = "Pairing with ${state.deviceName}",
            body = "Check the TV's screen and select \"Allow\" (ideally \"Always allow\") on the " +
                "debugging prompt. This can take up to two minutes.",
            onDismiss = null,
        )

        is PairingUiState.Success -> PairingDialog(
            title = "Paired with ${state.deviceName}",
            body = "You can now control it from the dashboard.",
            onDismiss = {
                pairingState = PairingUiState.Idle
                onPaired()
            },
        )

        is PairingUiState.Failed -> PairingDialog(
            title = "Couldn't pair with ${state.deviceName}",
            body = state.reason,
            onDismiss = { pairingState = PairingUiState.Idle },
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

private fun deviceTypeLabel(deviceType: DeviceType): String =
    MANUAL_DEVICE_TYPE_OPTIONS.firstOrNull { it.second == deviceType }?.first ?: deviceType.name
