package com.homecontrol.feature.cameras

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * A configurable multi-camera grid: pick how many tiles (1/2/4/6), tap a
 * tile to assign one of the added cameras to it, double-tap a tile to view
 * it fullscreen (double-tap again, or the back button, returns to the grid).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraGridScreen(
    onBack: () -> Unit,
    viewModel: CameraGridViewModel = hiltViewModel(),
) {
    val cameras by viewModel.cameras.collectAsState()
    val grid by viewModel.grid.collectAsState()
    var fullscreenTile by remember { mutableStateOf<Int?>(null) }
    var assigningTile by remember { mutableStateOf<Int?>(null) }

    BackHandler(enabled = fullscreenTile != null) { fullscreenTile = null }

    Scaffold(
        topBar = {
            if (fullscreenTile == null) {
                TopAppBar(
                    title = { Text("Camera grid") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            if (fullscreenTile == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GRID_TILE_COUNTS.forEach { count ->
                        FilterChip(
                            selected = grid.tileCount == count,
                            onClick = { viewModel.setTileCount(count) },
                            label = { Text("$count") },
                        )
                    }
                }
            }

            CameraGridLayout(
                tileCount = grid.tileCount,
                fullscreenTile = fullscreenTile,
                cameraIds = grid.cameraIds,
                cameraFor = viewModel::cameraFor,
                onTileTap = { index -> assigningTile = index },
                onTileDoubleTap = { index -> fullscreenTile = if (fullscreenTile == index) null else index },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
            )
        }
    }

    assigningTile?.let { index ->
        AssignCameraDialog(
            cameras = cameras,
            currentCameraId = grid.cameraIds.getOrNull(index),
            onDismiss = { assigningTile = null },
            onSelect = { cameraId ->
                viewModel.assignCamera(index, cameraId)
                assigningTile = null
            },
        )
    }
}

/**
 * Every tile from 0 until [tileCount] is composed permanently at the same
 * call site the whole time (via `key(index)`) — only its size/offset within
 * this single flat [BoxWithConstraints] changes between its normal grid-cell
 * rect and the fullscreen rect. That's deliberate: an earlier version
 * switched between rendering either the grid or a lone fullscreen tile,
 * which tore down and recreated the RTSP connection on every double-tap.
 * This cheap camera couldn't open a second session before the first one
 * finished closing, so fullscreen just hung on a black screen. Keeping the
 * same composable (and therefore the same ExoPlayer instance) mounted the
 * whole time makes the fullscreen toggle instant, with no reconnect.
 */
@Composable
private fun CameraGridLayout(
    tileCount: Int,
    fullscreenTile: Int?,
    cameraIds: List<String?>,
    cameraFor: (String?) -> CameraConfig?,
    onTileTap: (Int) -> Unit,
    onTileDoubleTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (columns, rows) = gridDimensions(tileCount)
    BoxWithConstraints(modifier = modifier) {
        val cellWidth = maxWidth / columns
        val cellHeight = maxHeight / rows
        for (index in 0 until tileCount) {
            key(index) {
                val isFullscreen = fullscreenTile == index
                val isHiddenBehindFullscreen = fullscreenTile != null && !isFullscreen
                val tileModifier = when {
                    isHiddenBehindFullscreen -> Modifier.size(0.dp)
                    isFullscreen -> Modifier.size(maxWidth, maxHeight)
                    else -> Modifier
                        .offset(x = cellWidth * (index % columns), y = cellHeight * (index / columns))
                        .size(cellWidth, cellHeight)
                        .padding(1.dp)
                }
                CameraTile(
                    camera = cameraFor(cameraIds.getOrNull(index)),
                    modifier = tileModifier,
                    onTap = { onTileTap(index) },
                    onDoubleTap = { onTileDoubleTap(index) },
                )
            }
        }
    }
}

@Composable
private fun CameraTile(
    camera: CameraConfig?,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(camera?.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { if (camera != null) onDoubleTap() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (camera == null) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Assign a camera",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            CameraFeed(camera = camera, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun CameraFeed(camera: CameraConfig, modifier: Modifier = Modifier) {
    var state by remember(camera.id) { mutableStateOf<StreamUiState>(StreamUiState.Loading) }

    LaunchedEffect(camera.id) {
        state = StreamUiState.Loading
        state = resolveCameraStreamUri(camera).fold(
            onSuccess = { StreamUiState.Ready(it) },
            onFailure = { StreamUiState.Failed(it.message ?: "Couldn't connect") },
        )
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (val s = state) {
            StreamUiState.Loading -> CircularProgressIndicator()
            is StreamUiState.Ready -> RtspPlayer(rtspUrl = s.rtspUrl, modifier = Modifier.fillMaxSize())
            is StreamUiState.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = camera.name,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
                )
                Text(
                    text = s.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(4.dp),
                )
            }
        }
    }
}

@Composable
private fun AssignCameraDialog(
    cameras: List<CameraConfig>,
    currentCameraId: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a camera") },
        text = {
            if (cameras.isEmpty()) {
                Text("No cameras added yet — add one from the Cameras screen first.")
            } else {
                Column {
                    cameras.forEach { camera ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(camera.id) }
                                .padding(vertical = 12.dp),
                        ) {
                            Text(text = camera.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(null) }, enabled = currentCameraId != null) { Text("Clear") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun gridDimensions(tileCount: Int): Pair<Int, Int> = when (tileCount) {
    1 -> 1 to 1
    2 -> 1 to 2
    4 -> 2 to 2
    6 -> 2 to 3
    else -> 1 to 1
}
