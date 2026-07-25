package com.homecontrol.ios.screens.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.homecontrol.core.model.DiscoveredDevice
import com.homecontrol.ios.discovery.MdnsScanner
import com.homecontrol.ios.discovery.UdpScanResult
import com.homecontrol.ios.discovery.scanForWindowsPcs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Auto-discovery only — [MdnsScanner] (Google Cast advertisement, for
 * Android TV/Google TV) and [scanForWindowsPcs] (UDP broadcast, for Windows
 * PCs) run in parallel and populate the list below as results come in. The
 * manual "add by IP" fallback (for devices that don't advertise themselves,
 * like Fire TV) is a separate screen reached via the button at the bottom —
 * see [AddDeviceManuallyScreen] — rather than living inline on this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDiscoveryScreen(onBack: () -> Unit, onAddManually: () -> Unit, onPaired: () -> Unit) {
    val discoveredDevices = remember { mutableStateListOf<DiscoveredDevice>() }
    var windowsScanResult by remember { mutableStateOf<UdpScanResult?>(null) }
    val scope = rememberCoroutineScope()
    val pairing = rememberPairingController(onPaired)

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
                        onClick = { pairing.start(device.ipAddress, device.name, device.deviceTypeHint) },
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

            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Don't see your device? Fire TV in particular never advertises itself on " +
                    "the network, so it can only be added manually.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onAddManually, modifier = Modifier.fillMaxWidth()) {
                Text("Add manually")
            }
        }
    }

    PairingDialogHost(pairing)
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
