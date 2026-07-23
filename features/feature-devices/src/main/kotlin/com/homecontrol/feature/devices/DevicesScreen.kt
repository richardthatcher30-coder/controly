package com.homecontrol.feature.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.homecontrol.core.model.DiscoveredDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    onBack: () -> Unit,
    viewModel: DevicesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Devices") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
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
                onDeviceClick = viewModel::pairDevice,
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
                "No devices found yet.\nTap “Scan for devices” to search your network."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
