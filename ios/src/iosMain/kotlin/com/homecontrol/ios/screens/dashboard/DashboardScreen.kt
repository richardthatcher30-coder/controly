package com.homecontrol.ios.screens.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.homecontrol.core.model.DeviceType
import com.homecontrol.core.model.PairedDevice
import com.homecontrol.ios.storage.PairedDeviceStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onAddDevice: () -> Unit, onAbout: () -> Unit) {
    val store = remember { PairedDeviceStore() }
    var devices by remember { mutableStateOf(store.list()) }
    var deviceToRemove by remember { mutableStateOf<PairedDevice?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Controly") },
                actions = {
                    IconButton(onClick = onAbout) {
                        Text("i", style = MaterialTheme.typography.titleMedium)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddDevice) {
                Text("+", style = MaterialTheme.typography.headlineMedium)
            }
        },
    ) { padding ->
        if (devices.isEmpty()) {
            EmptyState(padding, onAddDevice)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(devices, key = { it.id }) { device ->
                    DeviceRow(
                        device = device,
                        onClick = { /* Remote control screen — a later phase */ },
                        onLongPress = { deviceToRemove = device },
                    )
                }
            }
        }
    }

    deviceToRemove?.let { device ->
        AlertDialog(
            onDismissRequest = { deviceToRemove = null },
            title = { Text("Remove ${device.name}?") },
            text = { Text("You'll need to pair it again to control it from this app.") },
            confirmButton = {
                TextButton(onClick = {
                    store.remove(device.id)
                    devices = store.list()
                    deviceToRemove = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { deviceToRemove = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun EmptyState(padding: PaddingValues, onAddDevice: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "No devices yet", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Add a TV or PC on your local network to start controlling it from your phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        TextButton(onClick = onAddDevice) { Text("Add a device") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeviceRow(device: PairedDevice, onClick: () -> Unit, onLongPress: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = deviceTypeShortLabel(device.deviceType),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = device.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = device.ipAddress,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun deviceTypeShortLabel(deviceType: DeviceType): String = when (deviceType) {
    DeviceType.ANDROID_TV -> "TV"
    DeviceType.GOOGLE_TV -> "TV"
    DeviceType.FIRE_TV -> "Fire"
    DeviceType.SAMSUNG_TV -> "Sam"
    DeviceType.SONY_TV -> "Sony"
    DeviceType.WINDOWS_PC -> "PC"
    DeviceType.UNKNOWN -> "?"
}
