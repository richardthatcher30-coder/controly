package com.homecontrol.feature.cameras

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Quick experimental screen: cameras are added manually by IP/username/
 * password (there's no discovery — ONVIF cameras don't broadcast the same
 * way the TV/PC plugins' devices do) and stored locally, not in the shared
 * paired-devices database, since this isn't a real `IDevicePlugin` — just a
 * live RTSP viewer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CamerasScreen(
    onBack: () -> Unit,
    onCameraClick: (CameraConfig) -> Unit,
    viewModel: CamerasViewModel = hiltViewModel(),
) {
    val cameras by viewModel.cameras.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cameras") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Add camera") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = { showAddDialog = true },
            )
        },
    ) { paddingValues ->
        if (cameras.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No cameras yet. Tap “Add camera” to add one by its IP address.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = cameras, key = { it.id }) { camera ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCameraClick(camera) },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = camera.name, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    text = camera.ipAddress,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { viewModel.removeCamera(camera) }) { Text("Remove") }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddCameraDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, ip, port, user, pass ->
                viewModel.addCamera(name, ip, port, user, pass)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun AddCameraDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, ipAddress: String, onvifPort: Int, username: String, password: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var ipAddress by remember { mutableStateOf("") }
    // 80 is the ONVIF spec default, but budget cameras routinely run it on
    // something else entirely (this exact SV3C unit turned out to be 8000,
    // not the 80/8080/1018 its own documentation claims) — better to let
    // this be found out per-camera than to hardcode a guess.
    var onvifPort by remember { mutableStateOf("8000") }
    var username by remember { mutableStateOf("admin") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add camera") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name, e.g. Front Door") }, singleLine = true)
                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { ipAddress = it },
                    label = { Text("IP address") },
                    singleLine = true,
                    // Uri keyboard type + no autocorrect — a plain text field let
                    // autocorrect silently turn dots into spaces on a real device,
                    // which crashed the app instead of just failing to connect.
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                )
                OutlinedTextField(
                    value = onvifPort,
                    onValueChange = { onvifPort = it.filter(Char::isDigit) },
                    label = { Text("ONVIF port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, singleLine = true)
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && ipAddress.isNotBlank() && onvifPort.toIntOrNull() != null,
                onClick = { onConfirm(name.trim(), ipAddress.trim(), onvifPort.toInt(), username.trim(), password) },
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
