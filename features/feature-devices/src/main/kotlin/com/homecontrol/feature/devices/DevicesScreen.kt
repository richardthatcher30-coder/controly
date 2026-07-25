package com.homecontrol.feature.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.homecontrol.core.model.DeviceType
import com.homecontrol.core.model.DiscoveredDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    onBack: () -> Unit,
    viewModel: DevicesViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showManualAddDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Devices") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { TextButton(onClick = { showManualAddDialog = true }) { Text("Add by IP") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(if (uiState.isScanning) "Scanning…" else "Scan for devices") },
                icon = {
                    if (uiState.isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    }
                },
                onClick = viewModel::startScan,
            )
        },
    ) { paddingValues ->
        if (uiState.discoveredDevices.isEmpty()) {
            DevicesEmptyState(isScanning = uiState.isScanning, paddingValues = paddingValues)
        } else {
            DevicesList(
                devices = uiState.discoveredDevices,
                paddingValues = paddingValues,
                onDeviceClick = { device -> viewModel.pairDevice(device) },
            )
        }
    }

    uiState.pairingState?.let { pairingState ->
        PairingDialog(
            state = pairingState,
            onDismiss = viewModel::dismissPairingResult,
            onSubmitCode = viewModel::submitPairingCode,
        )
    }

    if (showManualAddDialog) {
        ManualAddDeviceDialog(
            onDismiss = { showManualAddDialog = false },
            onConfirm = { name, ip, deviceType, presharedKey ->
                viewModel.addManualDevice(name, ip, deviceType, presharedKey)
                showManualAddDialog = false
            },
        )
    }
}

@Composable
private fun DevicesEmptyState(isScanning: Boolean, paddingValues: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (isScanning) {
                "Searching your network…"
            } else {
                "No devices found. Tap Scan, or Add by IP for devices like Fire TV."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun DevicesList(
    devices: List<DiscoveredDevice>,
    paddingValues: PaddingValues,
    onDeviceClick: (DiscoveredDevice) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = devices, key = { it.discoveryId }) { device ->
            DiscoveredDeviceCard(device = device, onClick = { onDeviceClick(device) })
        }
    }
}

@Composable
private fun DiscoveredDeviceCard(device: DiscoveredDevice, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = device.name, style = MaterialTheme.typography.titleLarge)
            Text(
                text = device.ipAddress,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = device.discoveryProtocol.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PairingDialog(
    state: PairingUiState,
    onDismiss: () -> Unit,
    onSubmitCode: (String) -> Unit,
) {
    var code by rememberSaveable(state) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            when (state) {
                is PairingUiState.AwaitingCode ->
                    TextButton(onClick = { onSubmitCode(code) }, enabled = code.isNotBlank()) { Text("Submit") }
                is PairingUiState.InProgress -> Unit
                else -> TextButton(onClick = onDismiss) { Text("OK") }
            }
        },
        dismissButton = {
            if (state is PairingUiState.AwaitingCode) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
        title = {
            Text(
                when (state) {
                    is PairingUiState.InProgress -> "Pairing with ${state.deviceName}"
                    is PairingUiState.AwaitingCode -> "Enter code for ${state.deviceName}"
                    is PairingUiState.Success -> "Paired"
                    is PairingUiState.Failed -> "Pairing failed"
                },
            )
        },
        text = {
            when (state) {
                is PairingUiState.InProgress -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(
                        text = "Check ${state.deviceName}'s screen and approve the connection request.",
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                is PairingUiState.AwaitingCode -> Column {
                    Text(text = state.message, modifier = Modifier.padding(bottom = 12.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("Code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is PairingUiState.Success ->
                    Text("${state.deviceName} is now paired and will appear on your dashboard.")
                is PairingUiState.Failed ->
                    Text("Couldn't pair with ${state.deviceName}: ${state.reason}")
            }
        },
    )
}

// Fire TV and Android TV/Google TV both just label the same underlying ADB
// plugin, so there's no functional difference between them — but Sony and
// Samsung genuinely speak different network protocols (Sony's PIN-paired
// ScalarWebAPI/IRCC-IP vs Samsung's own on-screen-approval TCP protocol), so
// unlike the "TV vs Windows PC" split above them, these can't be collapsed
// into one generic "TV" choice — the app has no way to guess which protocol
// to speak without being told the brand.
private val TV_BRAND_OPTIONS = MANUAL_DEVICE_TYPE_OPTIONS.filter { it.second != DeviceType.WINDOWS_PC }
private val WINDOWS_PC_OPTION = MANUAL_DEVICE_TYPE_OPTIONS.first { it.second == DeviceType.WINDOWS_PC }

@Composable
private fun ManualAddDeviceDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, ipAddress: String, deviceType: DeviceType, presharedKey: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var ipAddress by remember { mutableStateOf("") }
    var isWindowsPc by remember { mutableStateOf(false) }
    var tvBrand by remember { mutableStateOf(TV_BRAND_OPTIONS.first().second) }
    var presharedKey by remember { mutableStateOf("") }
    val deviceType = if (isWindowsPc) WINDOWS_PC_OPTION.second else tvBrand

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add device by IP") },
        text = {
            // Scrollable: the device-type rows plus two text fields don't
            // fit the available height in landscape (or on small screens) —
            // without this, Compose was cramming every radio row into zero
            // height, collapsing the whole list down to one radio button
            // with no visible label at all (confirmed on a real device).
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { ipAddress = it },
                    label = { Text("IP address") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(text = "Device type", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !isWindowsPc, onClick = { isWindowsPc = false }, label = { Text("TV") })
                    FilterChip(selected = isWindowsPc, onClick = { isWindowsPc = true }, label = { Text(WINDOWS_PC_OPTION.first) })
                }
                if (!isWindowsPc) {
                    Column {
                        TV_BRAND_OPTIONS.forEach { (label, type) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { tvBrand = type }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = tvBrand == type, onClick = { tvBrand = type })
                                Text(text = label, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        if (tvBrand == DeviceType.SONY_TV) {
                            OutlinedTextField(
                                value = presharedKey,
                                onValueChange = { presharedKey = it },
                                label = { Text("Pre-shared key (optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = "Only needed if the TV keeps asking to re-approve pairing. Set a key on the TV under " +
                                    "Settings → Network → Home Network → IP Control Authentication, then enter the same key here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = ipAddress.isNotBlank(),
                onClick = { onConfirm(name.trim(), ipAddress.trim(), deviceType, presharedKey.trim().ifBlank { null }) },
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
