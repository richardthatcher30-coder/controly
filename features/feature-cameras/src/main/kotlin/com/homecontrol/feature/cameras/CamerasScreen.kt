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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.homecontrol.feature.cameras.onvif.DiscoveredCamera

/**
 * Cameras are added either by searching the network (WS-Discovery finds the
 * camera's real IP/ONVIF port automatically, still needs a username/password
 * added by hand since discovery can't know those) or fully by hand for
 * cameras discovery doesn't find. Stored locally, not in the shared
 * paired-devices database, since this isn't a real `IDevicePlugin` — just a
 * live RTSP viewer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CamerasScreen(
    onBack: () -> Unit,
    onCameraClick: (CameraConfig) -> Unit,
    onGridViewClick: () -> Unit,
    viewModel: CamerasViewModel = koinViewModel(),
) {
    val cameras by viewModel.cameras.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val discoveredCameras by viewModel.discoveredCameras.collectAsState()
    var showManualAddDialog by remember { mutableStateOf(false) }
    var configuringDiscovered by remember { mutableStateOf<DiscoveredCamera?>(null) }
    var configuringRemoteAccess by remember { mutableStateOf<CameraConfig?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cameras") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onGridViewClick) { Text("Grid view") }
                    TextButton(onClick = { showManualAddDialog = true }) { Text("Add manually") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(if (isScanning) "Scanning…" else "Scan for cameras") },
                icon = {
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    }
                },
                onClick = viewModel::startScan,
            )
        },
    ) { paddingValues ->
        val unconfiguredDiscovered = discoveredCameras.filter { discovered ->
            cameras.none { it.ipAddress == discovered.ipAddress }
        }

        if (cameras.isEmpty() && unconfiguredDiscovered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isScanning) {
                        "Searching your network for cameras…"
                    } else {
                        "No cameras yet. Tap “Scan for cameras” to search your network, or “Add manually” if it isn't found."
                    },
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
                            IconButton(onClick = { configuringRemoteAccess = camera }) {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = "Remote access settings",
                                    tint = if (camera.remoteHost != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { viewModel.removeCamera(camera) }) { Text("Remove") }
                        }
                    }
                }

                if (unconfiguredDiscovered.isNotEmpty()) {
                    item {
                        Text(
                            text = "Found on your network",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                }
                items(items = unconfiguredDiscovered, key = { it.ipAddress }) { discovered ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { configuringDiscovered = discovered },
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = discovered.ipAddress, style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = "Tap to add — port ${discovered.port}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showManualAddDialog) {
        CameraConfigureDialog(
            title = "Add camera",
            initialIpAddress = "",
            initialPort = 8000,
            onDismiss = { showManualAddDialog = false },
            onConfirm = { name, ip, port, user, pass ->
                viewModel.addCamera(name, ip, port, user, pass)
                showManualAddDialog = false
            },
        )
    }

    configuringDiscovered?.let { discovered ->
        CameraConfigureDialog(
            title = "Configure camera",
            initialIpAddress = discovered.ipAddress,
            initialPort = discovered.port,
            onDismiss = { configuringDiscovered = null },
            onConfirm = { name, ip, port, user, pass ->
                viewModel.addCamera(name, ip, port, user, pass)
                configuringDiscovered = null
            },
        )
    }

    configuringRemoteAccess?.let { camera ->
        // Re-read the latest copy from `cameras` each recomposition so the
        // dialog reflects a just-saved remote address instead of the stale
        // snapshot captured when it was opened.
        val current = cameras.firstOrNull { it.id == camera.id } ?: camera
        RemoteAccessDialog(
            camera = current,
            remoteAccessState = viewModel.remoteAccessState.collectAsState().value,
            onAutoConfigure = { viewModel.attemptUpnpAutoConfigure(current) },
            onSaveManual = { host, port -> viewModel.setManualRemoteAddress(current, host, port) },
            onClear = { viewModel.clearRemoteAddress(current) },
            onDismiss = {
                viewModel.dismissRemoteAccessResult()
                configuringRemoteAccess = null
            },
        )
    }
}

@Composable
private fun RemoteAccessDialog(
    camera: CameraConfig,
    remoteAccessState: RemoteAccessUiState?,
    onAutoConfigure: () -> Unit,
    onSaveManual: (host: String, port: Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var host by remember(camera.id) { mutableStateOf(camera.remoteHost.orEmpty()) }
    var port by remember(camera.id) { mutableStateOf((camera.remoteRtspPort ?: camera.lastKnownRtspPort ?: 554).toString()) }

    // A successful UPnP attempt updates the camera's own remoteHost/Port —
    // reflect that back into the manual fields so they don't show stale
    // pre-attempt values underneath the result message.
    LaunchedEffect(camera.remoteHost, camera.remoteRtspPort) {
        camera.remoteHost?.let { host = it }
        camera.remoteRtspPort?.let { port = it.toString() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remote access — ${camera.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Lets you view this camera when your phone isn't on the same Wi-Fi. This works by exposing the " +
                        "camera's video stream to the internet through your router — anyone who finds the address and " +
                        "port could potentially view it too, so only do this if the camera has a strong password.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                when (remoteAccessState) {
                    RemoteAccessUiState.InProgress -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("  Asking your router to forward the port…", style = MaterialTheme.typography.bodySmall)
                    }
                    is RemoteAccessUiState.Success -> Text(
                        "Configured: ${remoteAccessState.host}:${remoteAccessState.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    is RemoteAccessUiState.Failed -> Text(
                        remoteAccessState.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    null -> Unit
                }

                TextButton(
                    onClick = onAutoConfigure,
                    enabled = remoteAccessState != RemoteAccessUiState.InProgress,
                ) { Text("Auto-configure via UPnP (must be on the camera's Wi-Fi)") }

                HorizontalDivider()
                Text("Or enter it manually (e.g. after forwarding the port yourself):", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("External host or IP") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit) },
                    label = { Text("External port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = host.isNotBlank() && port.toIntOrNull() != null,
                onClick = { onSaveManual(host.trim(), port.toInt()) },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (camera.remoteHost != null) {
                    TextButton(onClick = onClear) { Text("Clear") }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

@Composable
private fun CameraConfigureDialog(
    title: String,
    initialIpAddress: String,
    initialPort: Int,
    onDismiss: () -> Unit,
    onConfirm: (name: String, ipAddress: String, onvifPort: Int, username: String, password: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var ipAddress by remember { mutableStateOf(initialIpAddress) }
    // 80 is the ONVIF spec default, but budget cameras routinely run it on
    // something else entirely (this exact SV3C unit turned out to be 8000,
    // not the 80/8080/1018 its own documentation claims) — a camera found via
    // network scan carries its own real port already; a manually-added one
    // still has to guess, so this default remains the best-known starting point.
    var onvifPort by remember { mutableStateOf(initialPort.toString()) }
    var username by remember { mutableStateOf("admin") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name, e.g. Front Door") }, singleLine = true)
                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { ipAddress = it },
                    label = { Text("IP address") },
                    singleLine = true,
                    // Decimal keyboard type + no autocorrect — a plain text field let
                    // autocorrect silently turn dots into spaces on a real device,
                    // which crashed the app instead of just failing to connect.
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, autoCorrectEnabled = false),
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
