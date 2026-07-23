package com.homecontrol.feature.remote

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.homecontrol.core.model.AppInfo
import com.homecontrol.core.model.DeviceCapabilities
import com.homecontrol.core.model.MouseButton
import com.homecontrol.core.model.RemoteKey
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(
    onBack: () -> Unit,
    viewModel: RemoteViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val device = uiState.device

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(device?.name ?: "Remote") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { paddingValues ->
        if (device == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            RemoteContent(
                paddingValues = paddingValues,
                capabilities = device.capabilities,
                isConnecting = uiState.isConnecting,
                isConnected = uiState.isConnected,
                connectionError = uiState.connectionError,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun RemoteContent(
    paddingValues: PaddingValues,
    capabilities: DeviceCapabilities,
    isConnecting: Boolean,
    isConnected: Boolean,
    connectionError: String?,
    viewModel: RemoteViewModel,
) {
    var showApps by remember { mutableStateOf(false) }
    var showQuickActions by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
    var isTouchpadFullScreen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        connectionError?.let { error ->
            Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = viewModel::retryConnect) { Text("Retry") }
        }
        if (isConnecting) {
            CircularProgressIndicator()
        }

        if (capabilities.supportsPower || capabilities.supportsWakeOnLan) {
            PowerRow(
                capabilities = capabilities,
                enabled = isConnected,
                onPowerOn = viewModel::powerOn,
                onPowerOff = viewModel::powerOff,
            )
        }

        if (capabilities.supportsTouchpad || capabilities.supportsMouse) {
            Touchpad(
                enabled = isConnected,
                isFullScreen = isTouchpadFullScreen,
                onToggleFullScreen = { isTouchpadFullScreen = !isTouchpadFullScreen },
                onDrag = viewModel::moveMouse,
                onTap = { viewModel.clickMouse(MouseButton.LEFT) },
                onRightClick = { viewModel.clickMouse(MouseButton.RIGHT) },
            )
        } else {
            DPad(enabled = isConnected, onKey = viewModel::sendKey)

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(enabled = isConnected, onClick = { viewModel.sendKey(RemoteKey.BACK) }) { Text("Back") }
                OutlinedButton(enabled = isConnected, onClick = { viewModel.sendKey(RemoteKey.HOME) }) { Text("Home") }
                OutlinedButton(enabled = isConnected, onClick = { viewModel.sendKey(RemoteKey.MENU) }) { Text("Menu") }
            }
        }

        if (capabilities.supportsVolume) {
            VolumeRow(
                enabled = isConnected,
                onVolumeDown = viewModel::volumeDown,
                onMute = viewModel::mute,
                onVolumeUp = viewModel::volumeUp,
            )
        }

        if (capabilities.supportsChannels) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(enabled = isConnected, onClick = { viewModel.sendKey(RemoteKey.CHANNEL_DOWN) }) { Text("Ch −") }
                OutlinedButton(enabled = isConnected, onClick = { viewModel.sendKey(RemoteKey.CHANNEL_UP) }) { Text("Ch +") }
            }
        }

        // No per-input list/select API on this device — each press just
        // steps to the next input, same as the physical remote's Source
        // button. Watch the TV screen and keep pressing until it lands on
        // the input you want (e.g. HDMI 2).
        if (capabilities.supportsSourceCycle) {
            OutlinedButton(enabled = isConnected, onClick = { viewModel.sendKey(RemoteKey.INPUT_SOURCE) }) { Text("Source") }
        }

        if (capabilities.supportsKeyboard) {
            KeyboardEntry(enabled = isConnected, onTextChanged = viewModel::onRemoteTextChanged)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (capabilities.supportsApps) {
                OutlinedButton(enabled = isConnected, onClick = { showApps = true }) { Text("Apps") }
            }
            if (capabilities.supportsQuickActions) {
                OutlinedButton(enabled = isConnected, onClick = { showQuickActions = true }) { Text("Quick Actions") }
            }
            if (capabilities.supportsInputSource) {
                OutlinedButton(enabled = isConnected, onClick = { showSources = true }) { Text("Sources") }
            }
            if (capabilities.supportsSmartHub) {
                OutlinedButton(enabled = isConnected, onClick = { viewModel.sendKey(RemoteKey.SMART_HUB) }) { Text("Smart Hub") }
            }
        }
    }

    if (showApps) {
        AppsGridDialog(viewModel = viewModel, onDismiss = { showApps = false })
    }
    if (showQuickActions) {
        QuickActionsGridDialog(viewModel = viewModel, onDismiss = { showQuickActions = false })
    }
    if (showSources) {
        SourcesGridDialog(viewModel = viewModel, onDismiss = { showSources = false })
    }
}

@Composable
private fun PowerRow(
    capabilities: DeviceCapabilities,
    enabled: Boolean,
    onPowerOn: () -> Unit,
    onPowerOff: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (capabilities.supportsWakeOnLan) {
            Button(onClick = onPowerOn) { Text("Power on") }
        }
        if (capabilities.supportsPower) {
            Button(enabled = enabled, onClick = onPowerOff) { Text("Power off") }
        }
    }
}

/**
 * A relative-movement touchpad: drag to move the pointer, a plain tap (no
 * movement) sends a single left click, and — deliberately — a fast second
 * tap is *not* specially detected as "double-click" here. Each tap just
 * sends an ordinary click immediately; two of them close together become a
 * double-click via Windows' own native double-click detection, exactly as a
 * real physical mouse would generate one. That's simpler and more robust
 * than guessing at timing thresholds on the phone side.
 */
@Composable
private fun Touchpad(
    enabled: Boolean,
    isFullScreen: Boolean,
    onToggleFullScreen: () -> Unit,
    onDrag: (Int, Int) -> Unit,
    onTap: () -> Unit,
    onRightClick: () -> Unit,
) {
    if (isFullScreen) {
        Dialog(
            onDismissRequest = onToggleFullScreen,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            TouchpadSurface(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                enabled = enabled,
                isFullScreen = true,
                onToggleFullScreen = onToggleFullScreen,
                onDrag = onDrag,
                onTap = onTap,
                onRightClick = onRightClick,
            )
        }
    } else {
        TouchpadSurface(
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            isFullScreen = false,
            onToggleFullScreen = onToggleFullScreen,
            onDrag = onDrag,
            onTap = onTap,
            onRightClick = onRightClick,
        )
    }
}

@Composable
private fun TouchpadSurface(
    modifier: Modifier,
    enabled: Boolean,
    isFullScreen: Boolean,
    onToggleFullScreen: () -> Unit,
    onDrag: (Int, Int) -> Unit,
    onTap: () -> Unit,
    onRightClick: () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isFullScreen) Modifier.weight(1f) else Modifier.height(200.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                .then(
                    if (enabled) {
                        Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                var dragged = false
                                var pointerPressed = true
                                while (pointerPressed) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (change.positionChanged()) {
                                        val amount = change.positionChange()
                                        if (!dragged && amount.getDistance() > viewConfiguration.touchSlop) {
                                            dragged = true
                                        }
                                        if (dragged) {
                                            change.consume()
                                            onDrag(amount.x.roundToInt(), amount.y.roundToInt())
                                        }
                                    }
                                    pointerPressed = change.pressed
                                }
                                if (!dragged) {
                                    onTap()
                                }
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (!enabled) {
                Text(
                    text = "Not connected",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                enabled = enabled,
                onClick = onRightClick,
                modifier = Modifier.weight(1f),
            ) { Text("Right click") }
            OutlinedButton(
                onClick = onToggleFullScreen,
                modifier = Modifier.weight(1f),
            ) { Text(if (isFullScreen) "Exit full screen" else "Full screen") }
        }
    }
}

@Composable
private fun DPad(enabled: Boolean, onKey: (RemoteKey) -> Unit) {
    val buttonSize = 64.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DPadButton("▲", buttonSize, enabled) { onKey(RemoteKey.DPAD_UP) }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DPadButton("◀", buttonSize, enabled) { onKey(RemoteKey.DPAD_LEFT) }
            DPadButton("OK", buttonSize, enabled) { onKey(RemoteKey.DPAD_CENTER) }
            DPadButton("▶", buttonSize, enabled) { onKey(RemoteKey.DPAD_RIGHT) }
        }
        DPadButton("▼", buttonSize, enabled) { onKey(RemoteKey.DPAD_DOWN) }
    }
}

@Composable
private fun DPadButton(label: String, size: Dp, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.size(size),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(label)
    }
}

@Composable
private fun VolumeRow(enabled: Boolean, onVolumeDown: () -> Unit, onMute: () -> Unit, onVolumeUp: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedButton(enabled = enabled, onClick = onVolumeDown) { Text("Vol −") }
        OutlinedButton(enabled = enabled, onClick = onMute) { Text("Mute") }
        OutlinedButton(enabled = enabled, onClick = onVolumeUp) { Text("Vol +") }
    }
}

/**
 * Tapping "Keyboard" immediately requests focus, which brings up the
 * phone's own on-screen keyboard. Every keystroke streams to the PC live
 * via [onTextChanged] rather than waiting for a Send button.
 */
@Composable
private fun KeyboardEntry(enabled: Boolean, onTextChanged: (old: String, new: String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    if (!expanded) {
        OutlinedButton(enabled = enabled, onClick = { expanded = true }) { Text("Keyboard") }
    } else {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
        OutlinedTextField(
            value = text,
            onValueChange = { newText ->
                onTextChanged(text, newText)
                text = newText
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            singleLine = true,
            label = { Text("Typing sends live to the PC") },
            trailingIcon = {
                TextButton(
                    onClick = {
                        keyboardController?.hide()
                        expanded = false
                        text = ""
                    },
                ) { Text("Done") }
            },
        )
    }
}

/**
 * Decodes the PC-supplied `data:image/png;base64,…` icon. Falls back to a
 * hand-drawn globe glyph for URL buttons (a real icon can't be extracted
 * from a web address the way it can from a .exe) and a generic list glyph
 * for everything else with no icon (Quick Actions, any app whose icon
 * couldn't be resolved). Drawn with Canvas rather than pulling in
 * material-icons-extended for the one "Public"/"Language" glyph.
 */
@Composable
private fun AppIconImage(iconUri: String?, size: Dp, isUrl: Boolean = false) {
    val bitmap = remember(iconUri) {
        iconUri
            ?.substringAfter("base64,", missingDelimiterValue = "")
            ?.takeIf { it.isNotEmpty() }
            ?.let { base64 ->
                runCatching {
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }.getOrNull()
            }
    }

    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(size))
    } else if (isUrl) {
        Box(
            modifier = Modifier
                .size(size)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            GlobeIcon(size = size * 0.7f, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.List,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A simple globe: an outer circle, an "equator" ellipse, and a "meridian" ellipse — instantly reads as "web address" without needing a real icon library. */
@Composable
private fun GlobeIcon(size: Dp, tint: Color) {
    Canvas(modifier = Modifier.size(size)) {
        val stroke = Stroke(width = this.size.minDimension * 0.06f)
        drawCircle(color = tint, style = stroke)
        drawOval(
            color = tint,
            topLeft = Offset(0f, this.size.height * 0.3f),
            size = Size(this.size.width, this.size.height * 0.4f),
            style = stroke,
        )
        drawOval(
            color = tint,
            topLeft = Offset(this.size.width * 0.3f, 0f),
            size = Size(this.size.width * 0.4f, this.size.height),
            style = stroke,
        )
    }
}

/** Shared by the Apps / Quick Actions / Sources screens — same shape (a full-screen grid of icon+label tiles), different source list and trigger action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionGridDialog(
    title: String,
    items: List<AppInfo>,
    isLoading: Boolean,
    emptyMessage: String,
    onItemClick: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
    topBarActions: @Composable () -> Unit = {},
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = { TextButton(onClick = onDismiss) { Text("Close") } },
                    actions = { topBarActions() },
                )
            },
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                when {
                    isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    items.isEmpty() -> Text(
                        text = emptyMessage,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 88.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(items = items, key = { it.appId }) { item ->
                            Column(
                                modifier = Modifier
                                    .clickable { onItemClick(item) }
                                    .padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AppIconImage(iconUri = item.iconUri, size = 48.dp, isUrl = item.appId.startsWith("http"))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A full-screen grid of whatever app/URL buttons were configured on the PC
 * (via its own "Manage app buttons" window, with a real file picker) — the
 * phone displays and launches these and can add a URL one itself, but can
 * never browse the PC's filesystem to add a real .exe.
 */
@Composable
private fun AppsGridDialog(viewModel: RemoteViewModel, onDismiss: () -> Unit) {
    val apps by viewModel.apps.collectAsState()
    val isLoading by viewModel.isLoadingApps.collectAsState()
    var showAddUrl by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadAppsIfNeeded()
    }

    ActionGridDialog(
        title = "Apps",
        items = apps,
        isLoading = isLoading,
        emptyMessage = "No app buttons have been added on the PC yet.\nOpen the HomeControl Companion tray icon on the PC → \"Manage app buttons...\" to add some, or add a URL here.",
        onItemClick = { app -> viewModel.launchApp(app.appId) },
        onDismiss = onDismiss,
        topBarActions = {
            IconButton(onClick = { showAddUrl = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add URL")
            }
        },
    )

    if (showAddUrl) {
        AddUrlDialog(
            onDismiss = { showAddUrl = false },
            onConfirm = { label, url ->
                viewModel.addUrlButton(label, url)
                showAddUrl = false
            },
        )
    }
}

@Composable
private fun AddUrlDialog(onDismiss: () -> Unit, onConfirm: (label: String, url: String) -> Unit) {
    var label by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add URL button") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL, e.g. youtube.com") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Button label (optional)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank(),
                onClick = { onConfirm(label.trim().ifEmpty { url.trim() }, url.trim()) },
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Windows-only system shortcuts (Windows key, Task Manager, open a folder, ...) the user enabled on the PC's "Quick actions" window. */
@Composable
private fun QuickActionsGridDialog(viewModel: RemoteViewModel, onDismiss: () -> Unit) {
    val actions by viewModel.quickActions.collectAsState()
    val isLoading by viewModel.isLoadingQuickActions.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadQuickActionsIfNeeded()
    }

    ActionGridDialog(
        title = "Quick Actions",
        items = actions,
        isLoading = isLoading,
        emptyMessage = "No quick actions are enabled.\nOpen the HomeControl Companion tray icon on the PC → \"Quick actions...\" to enable some.",
        onItemClick = { action -> viewModel.triggerQuickAction(action.appId) },
        onDismiss = onDismiss,
    )
}

/** TV input sources (HDMI 1, HDMI 2, ...) queried live from the device itself — currently just Sony. */
@Composable
private fun SourcesGridDialog(viewModel: RemoteViewModel, onDismiss: () -> Unit) {
    val sources by viewModel.sources.collectAsState()
    val isLoading by viewModel.isLoadingSources.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadSourcesIfNeeded()
    }

    ActionGridDialog(
        title = "Sources",
        items = sources,
        isLoading = isLoading,
        emptyMessage = "No input sources were reported by the TV.",
        onItemClick = { source -> viewModel.selectSource(source.appId) },
        onDismiss = onDismiss,
    )
}
