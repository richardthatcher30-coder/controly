package com.homecontrol.ios.screens.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.homecontrol.ios.cameras.CameraConfigStore
import com.homecontrol.ios.cameras.onvif.DiscoveredCamera
import com.homecontrol.ios.cameras.onvif.OnvifDiscoveryScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manual "IP + credentials" add form, plus an ONVIF WS-Discovery scan that
 * pre-fills the IP/port when a camera answers -- port of the shape (not the
 * code) of `feature-cameras`' Android add-camera flow, adapted to this iOS
 * build's plain hand-rolled screens rather than a shared multiplatform
 * module (cameras aren't KMP anywhere yet, Android or iOS -- see
 * [com.homecontrol.ios.cameras.onvif.OnvifClient]'s doc comment for why
 * live view here is snapshot-polling, not RTSP video).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCameraScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val store = remember { CameraConfigStore() }

    var name by remember { mutableStateOf("") }
    var ipAddress by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("80") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var isScanning by remember { mutableStateOf(false) }
    var foundCameras by remember { mutableStateOf(listOf<DiscoveredCamera>()) }
    var hasScanned by remember { mutableStateOf(false) }

    fun startScan() {
        isScanning = true
        foundCameras = emptyList()
        scope.launch(Dispatchers.Default) {
            val results = mutableListOf<DiscoveredCamera>()
            OnvifDiscoveryScanner().scan { camera -> results.add(camera) }
            withContext(Dispatchers.Main) {
                foundCameras = results
                isScanning = false
                hasScanned = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add camera") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { startScan() }, modifier = Modifier.fillMaxWidth()) {
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp))
                    } else {
                        Text("Scan for cameras")
                    }
                }
            }

            if (hasScanned && !isScanning) {
                if (foundCameras.isEmpty()) {
                    item {
                        Text(
                            text = "No cameras found. Add one manually below.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(foundCameras) { camera ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = camera.ipAddress, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedButton(onClick = {
                                    ipAddress = camera.ipAddress
                                    port = camera.port.toString()
                                }) {
                                    Text("Use this camera")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Camera details", style = MaterialTheme.typography.titleMedium)
            }
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { ipAddress = it },
                    label = { Text("IP address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("ONVIF port") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                    Button(
                        enabled = name.isNotBlank() && ipAddress.isNotBlank() && port.toIntOrNull() != null,
                        onClick = {
                            store.add(name.trim(), ipAddress.trim(), port.trim().toInt(), username.trim(), password)
                            onBack()
                        },
                    ) {
                        Text("Add camera")
                    }
                }
            }
        }
    }
}
