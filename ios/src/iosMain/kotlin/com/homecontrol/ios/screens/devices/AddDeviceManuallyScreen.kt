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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.homecontrol.core.model.DeviceType

/**
 * Manual "add by IP" fallback, split out of [DeviceDiscoveryScreen] so that
 * screen can stay focused on auto-discovery results — reached via its "Add
 * manually" button, for devices that don't show up there (Fire TV in
 * particular never advertises itself on the network).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceManuallyScreen(onBack: () -> Unit, onPaired: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var ipAddress by remember { mutableStateOf("") }
    var deviceType by remember { mutableStateOf(DeviceType.ANDROID_TV) }
    val pairing = rememberPairingController(onPaired)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add manually") },
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
                text = "For devices that don't show up in auto-discovery — Fire TV in particular " +
                    "never advertises itself on the network, so this is the only way to add one.",
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
                    pairing.start(trimmedIp, deviceName, deviceType)
                },
                enabled = ipAddress.isNotBlank() && pairing.state !is PairingUiState.InProgress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Pair")
            }
        }
    }

    PairingDialogHost(pairing)
}
