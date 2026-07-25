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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.homecontrol.core.model.DeviceCapabilities
import com.homecontrol.core.model.DeviceType
import com.homecontrol.core.model.DiscoveredDevice
import com.homecontrol.core.model.PairedDevice
import com.homecontrol.ios.adb.AdbApprovalTimeoutException
import com.homecontrol.ios.adb.AdbConnection
import com.homecontrol.ios.discovery.MdnsScanner
import com.homecontrol.ios.discovery.UdpScanResult
import com.homecontrol.ios.discovery.scanForWindowsPcs
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
 * Auto-discovery — [MdnsScanner] (Google Cast advertisement, for Android TV/
 * Google TV) and [scanForWindowsPcs] (UDP broadcast, for Windows PCs) run in
 * parallel — shown above a manual "Add by IP" form, mirroring Android's
 * `DevicesScreen`, which also keeps a manual fallback for devices that don't
 * advertise themselves (Fire TV included; it never shows up in the
 * discovered list here on either platform). Tapping a discovered device or
 * submitting the manual form both funnel into the same [startPairing] flow;
 * pairing itself is currently only implemented for ADB-based device types
 * (see [ADB_SUPPORTED_TYPES]) — a discovered Windows PC shows up in the
 * list, but tapping it surfaces "not supported yet" rather than hanging.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceScreen(onBack: () -> Unit, onPaired: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var ipAddress by remember { mutableStateOf("") }
    var deviceType by remember { mutableStateOf(DeviceType.ANDROID_TV) }
    var pairingState by remember { mutableStateOf<PairingUiState>(PairingUiState.Idle) }
    val discoveredDevices = remember { mutableStateListOf<DiscoveredDevice>() }
    var windowsScanResult by remember { mutableStateOf<UdpScanResult?>(null) }
    val scope = rememberCoroutineScope()
    val store = remember { PairedDeviceStore() }

    fun addDiscovered(device: DiscoveredDevice) {
        if (discoveredDevices.none { it.discoveryId == device.discoveryId }) {
            discoveredDevices.add(device)
        }
    }

    DisposableEffect(Unit) {
        val mdnsScanner = MdnsScanner()
        mdnsScanner.startScan { device -> addDiscovered(device) }

        // Windows PC discovery is a blocking UDP broadcast probe (unlike the
        // event-driven mDNS callback above), so it runs on its own
        // background coroutine rather than inline in this effect.
        val udpJob = scope.launch(Dispatchers.Default) {
            val result = scanForWindowsPcs { device ->
                scope.launch(Dispatchers.Main) { addDiscovered(device) }
            }
            withContext(Dispatchers.Main) { windowsScanResult = result }
        }

        onDispose {
            mdnsScanner.stopScan()
            udpJob.cancel()
        }
    }

    fun startPairing(ip: String, deviceName: String, selectedType: DeviceType) {
        if (selectedType !in ADB_SUPPORTED_TYPES) {
            pairingState = PairingUiState.Failed(
                deviceName,
                "${deviceTypeLabel(selectedType)} pairing isn't supported on iOS yet — coming soon.",
            )
            return
        }
        pairingState = PairingUiState.InProgress(deviceName)
        // Dispatchers.IO is internal on Kotlin/Native (not part of the public API for
        // this coroutines version's iOS target) -- Default is the portable choice here.
        scope.launch(Dispatchers.Default) {
            try {
                AdbConnection().pair(ip)
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
                withContext(Dispatchers.Main) { pairingState = PairingUiState.Success(deviceName) }
            } catch (e: AdbApprovalTimeoutException) {
                withContext(Dispatchers.Main) {
                    pairingState = PairingUiState.Failed(
                        deviceName,
                        "Approval timed out — check the TV's screen and try again.",
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    pairingState = PairingUiState.Failed(deviceName, e.message ?: "Couldn't connect to $ip")
                }
            }
        }
    }

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
            if (discoveredDevices.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Searching your network for devices…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(text = "Found on your network", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                discoveredDevices.forEach { device ->
                    DiscoveredDeviceRow(
                        device = device,
                        onClick = { startPairing(device.ipAddress, device.name, device.deviceTypeHint) },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            (windowsScanResult as? UdpScanResult.Failed)?.let { failure ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Windows PC scan couldn't run: ${failure.step} failed " +
                        "(errno ${failure.errnoValue}: ${failure.errnoMessage})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "Or add by IP address", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "For devices that don't show up above — Fire TV in particular never " +
                    "advertises itself on the network, so this is the only way to add one.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))

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
                    startPairing(trimmedIp, deviceName, deviceType)
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
private fun DiscoveredDeviceRow(device: DiscoveredDevice, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = device.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${deviceTypeLabel(device.deviceTypeHint)} · ${device.ipAddress}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onClick) { Text("Pair") }
        }
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

private fun deviceTypeLabel(deviceType: DeviceType): String = when (deviceType) {
    DeviceType.GOOGLE_TV -> "Google TV"
    DeviceType.UNKNOWN -> "Unknown device"
    else -> MANUAL_DEVICE_TYPE_OPTIONS.firstOrNull { it.second == deviceType }?.first ?: deviceType.name
}
