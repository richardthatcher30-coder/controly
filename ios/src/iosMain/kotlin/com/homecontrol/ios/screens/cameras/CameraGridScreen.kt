package com.homecontrol.ios.screens.cameras

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.homecontrol.ios.cameras.CameraConfig
import com.homecontrol.ios.cameras.CameraConfigStore
import com.homecontrol.ios.cameras.decodeJpegToImageBitmap
import com.homecontrol.ios.cameras.onvif.OnvifClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val SNAPSHOT_REFRESH_INTERVAL_MS = 3_000L
private const val SNAPSHOT_RETRY_INTERVAL_MS = 5_000L

/**
 * Grid of paired cameras, each tile refreshed by polling a still JPEG
 * snapshot rather than playing live RTSP video -- iOS has no equivalent to
 * Android's ExoPlayer-based [feature-cameras' `RtspPlayer.kt`], and
 * AVFoundation's `AVPlayer` doesn't support RTSP at all on modern iOS. This
 * is a real, working live-ish view (updated every few seconds), not a
 * placeholder -- see [com.homecontrol.ios.cameras.onvif.OnvifClient]'s doc
 * comment for the full reasoning, and treat true low-latency RTSP video as a
 * separate, larger future undertaking (likely needing a third-party decoder
 * such as VLCKit, which needs an actual Mac to integrate/test).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraGridScreen(onBack: () -> Unit, onAddCamera: () -> Unit) {
    val store = remember { CameraConfigStore() }
    var cameras by remember { mutableStateOf(store.list()) }
    var cameraToRemove by remember { mutableStateOf<CameraConfig?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cameras") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddCamera) {
                Text("+", style = MaterialTheme.typography.headlineMedium)
            }
        },
    ) { padding ->
        if (cameras.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(text = "No cameras yet", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Add an IP camera on your local network to view it here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(cameras, key = { it.id }) { camera ->
                    CameraTile(camera = camera, onLongPress = { cameraToRemove = camera })
                }
            }
        }
    }

    cameraToRemove?.let { camera ->
        AlertDialog(
            onDismissRequest = { cameraToRemove = null },
            title = { Text("Remove ${camera.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    store.remove(camera.id)
                    cameras = store.list()
                    cameraToRemove = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { cameraToRemove = null }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CameraTile(camera: CameraConfig, onLongPress: () -> Unit) {
    var bitmap by remember(camera.id) { mutableStateOf<ImageBitmap?>(null) }
    var errorMessage by remember(camera.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(camera.id) {
        val httpClient = HttpClient(Darwin)
        val onvifClient = OnvifClient(camera.ipAddress, camera.onvifPort, camera.username, camera.password)
        try {
            var snapshotUrl: String? = null
            while (isActive) {
                if (snapshotUrl == null) {
                    val resolved = onvifClient.resolveSnapshotUri()
                    snapshotUrl = resolved.getOrNull()
                    if (snapshotUrl == null) {
                        errorMessage = resolved.exceptionOrNull()?.message ?: "Couldn't reach ${camera.name}"
                        delay(SNAPSHOT_RETRY_INTERVAL_MS)
                        continue
                    }
                }

                val fetched = runCatching { httpClient.get(snapshotUrl!!).readBytes() }
                    .mapCatching { bytes -> decodeJpegToImageBitmap(bytes) ?: error("Couldn't decode snapshot") }

                fetched.onSuccess { decoded ->
                    bitmap = decoded
                    errorMessage = null
                }.onFailure { error ->
                    errorMessage = error.message ?: "Couldn't reach ${camera.name}"
                    snapshotUrl = null // re-resolve on the next attempt in case the profile/credentials changed
                }

                delay(SNAPSHOT_REFRESH_INTERVAL_MS)
            }
        } finally {
            onvifClient.close()
            httpClient.close()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = {}, onLongClick = onLongPress),
    ) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val currentBitmap = bitmap
            when {
                currentBitmap != null -> Image(
                    bitmap = currentBitmap,
                    contentDescription = camera.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                errorMessage != null -> Text(
                    text = errorMessage ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp),
                )
                else -> CircularProgressIndicator()
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(text = camera.name, style = MaterialTheme.typography.labelLarge)
        }
    }
}
