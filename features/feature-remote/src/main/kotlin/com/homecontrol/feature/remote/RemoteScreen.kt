package com.homecontrol.feature.remote

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.koin.compose.viewmodel.koinViewModel
import com.homecontrol.core.model.AppInfo
import com.homecontrol.core.model.DeviceCapabilities
import com.homecontrol.core.model.MouseButton
import com.homecontrol.core.model.RemoteKey
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(
    onBack: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: RemoteViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val device = uiState.device

    // The underlying connection (a raw socket, for most device types) can
    // die silently while the screen is stopped — the phone locking, the app
    // backgrounding, the OS reclaiming the network connection, or the
    // device itself timing out. Nothing short-circuits button presses to
    // notice that on its own, so re-validate/rebuild the connection every
    // time this screen actually comes back to the foreground rather than
    // waiting for the user to notice a dead remote and hit Retry manually.
    // Skips the very first ON_RESUME (right after ON_CREATE) since the
    // ViewModel's own init{} already starts the initial connection.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var hasResumedBefore = false
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (hasResumedBefore) {
                    viewModel.retryConnect()
                }
                hasResumedBefore = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(isConnected = uiState.isConnected)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(device?.name ?: "Remote")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
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
                onSettingsClick = onSettingsClick,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun StatusDot(isConnected: Boolean) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(if (isConnected) Color(0xFF34C759) else MaterialTheme.colorScheme.outline),
    )
}

/**
 * Every section below is gated on a real [DeviceCapabilities] flag, not on
 * device family or "not a touchpad device" — showing a button that the
 * connected plugin doesn't actually map to anything (e.g. a "Voice" button
 * on a Sony TV, whose key table has no such entry) is worse than not
 * showing it, since it looks like it should work and silently does nothing.
 */
@Composable
private fun RemoteContent(
    paddingValues: PaddingValues,
    capabilities: DeviceCapabilities,
    isConnecting: Boolean,
    isConnected: Boolean,
    connectionError: String?,
    onSettingsClick: () -> Unit,
    viewModel: RemoteViewModel,
) {
    var showApps by remember { mutableStateOf(false) }
    var showQuickActions by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
    var isTouchpadFullScreen by remember { mutableStateOf(false) }
    var useDpadMode by rememberSaveable { mutableStateOf(false) }
    val hasTouchpad = capabilities.supportsTouchpad || capabilities.supportsMouse
    val hasQuickIcons = capabilities.supportsVoiceAssist || capabilities.supportsApps ||
        capabilities.supportsInputSource || capabilities.supportsSmartHub || capabilities.supportsQuickActions

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        connectionError?.let { error ->
            Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
            LabeledCircleButton(Icons.Filled.Refresh, "Retry", enabled = true, onClick = viewModel::retryConnect)
        }
        if (isConnecting) {
            CircularProgressIndicator()
        }

        TopControlsRow(
            capabilities = capabilities,
            enabled = isConnected,
            onPowerOn = viewModel::powerOn,
            onPowerOff = viewModel::powerOff,
            onSettingsClick = onSettingsClick,
        )

        if (hasQuickIcons) {
            QuickActionsRow(
                capabilities = capabilities,
                enabled = isConnected,
                onVoiceClick = { viewModel.sendKey(RemoteKey.VOICE_ASSIST) },
                onAppsClick = { showApps = true },
                onSourcesClick = { showSources = true },
                onSmartHubClick = { viewModel.sendKey(RemoteKey.SMART_HUB) },
                onQuickActionsClick = { showQuickActions = true },
            )
        }

        if (capabilities.supportsKeyboard) {
            KeyboardEntry(enabled = isConnected, onTextChanged = viewModel::onRemoteTextChanged)
        }

        if (hasTouchpad) {
            ControlModeToggle(useDpadMode = useDpadMode, onModeChange = { useDpadMode = it })
        }

        if (hasTouchpad && !useDpadMode) {
            Touchpad(
                enabled = isConnected,
                isFullScreen = isTouchpadFullScreen,
                onToggleFullScreen = { isTouchpadFullScreen = !isTouchpadFullScreen },
                onDrag = viewModel::moveMouse,
                onTap = { viewModel.clickMouse(MouseButton.LEFT) },
                onRightClick = { viewModel.clickMouse(MouseButton.RIGHT) },
            )
        } else {
            DPadWheel(enabled = isConnected, onKey = viewModel::sendKey)
            BottomNavRow(
                enabled = isConnected,
                onBack = { viewModel.sendKey(RemoteKey.BACK) },
                onHome = { viewModel.sendKey(RemoteKey.HOME) },
                onMenu = { viewModel.sendKey(RemoteKey.MENU) },
            )
        }

        if (capabilities.supportsMediaTransport) {
            MediaPillRow(
                enabled = isConnected,
                onRewind = { viewModel.sendKey(RemoteKey.REWIND) },
                onPlayPause = { viewModel.sendKey(RemoteKey.PLAY_PAUSE) },
                onFastForward = { viewModel.sendKey(RemoteKey.FAST_FORWARD) },
            )
        }

        if (capabilities.supportsVolume || capabilities.supportsChannels || capabilities.supportsSourceCycle) {
            VolumeChannelSection(capabilities = capabilities, enabled = isConnected, viewModel = viewModel)
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

/** Power (on the left, red when it means "off") and Settings (always reachable, on the right) — the same top row as the reference remote. */
@Composable
private fun TopControlsRow(
    capabilities: DeviceCapabilities,
    enabled: Boolean,
    onPowerOn: () -> Unit,
    onPowerOff: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (capabilities.supportsWakeOnLan) {
                RemoteCircleButton(
                    icon = Icons.Filled.PowerSettingsNew,
                    contentDescription = "Power on",
                    enabled = true,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = onPowerOn,
                )
            }
            if (capabilities.supportsPower) {
                RemoteCircleButton(
                    icon = Icons.Filled.PowerSettingsNew,
                    contentDescription = "Power off",
                    enabled = enabled,
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    onClick = onPowerOff,
                )
            }
        }
        RemoteCircleButton(
            icon = Icons.Filled.Settings,
            contentDescription = "Settings",
            enabled = true,
            onClick = onSettingsClick,
        )
    }
}

/** The "colored dots / 123 / •••" row from the reference — whichever of these a device actually supports, wrapping via [FlowRow] so it never squeezes on a narrow phone. */
@Composable
private fun QuickActionsRow(
    capabilities: DeviceCapabilities,
    enabled: Boolean,
    onVoiceClick: () -> Unit,
    onAppsClick: () -> Unit,
    onSourcesClick: () -> Unit,
    onSmartHubClick: () -> Unit,
    onQuickActionsClick: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (capabilities.supportsVoiceAssist) {
            LabeledCircleButton(Icons.Filled.Mic, "Voice", enabled, onClick = onVoiceClick)
        }
        if (capabilities.supportsApps) {
            LabeledCircleButton(Icons.Filled.Apps, "Apps", enabled, onClick = onAppsClick)
        }
        if (capabilities.supportsInputSource) {
            LabeledCircleButton(Icons.AutoMirrored.Filled.Input, "Sources", enabled, onClick = onSourcesClick)
        }
        if (capabilities.supportsSmartHub) {
            LabeledCircleButton(Icons.Filled.Dashboard, "Smart Hub", enabled, onClick = onSmartHubClick)
        }
        if (capabilities.supportsQuickActions) {
            LabeledCircleButton(Icons.Filled.Bolt, "Quick Actions", enabled, onClick = onQuickActionsClick)
        }
    }
}

/** A plain circular icon button — the shared building block for nearly every button on this screen. */
@Composable
private fun RemoteCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    enabled: Boolean,
    size: Dp = 64.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(size),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.4f),
            disabledContentColor = contentColor.copy(alpha = 0.4f),
        ),
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(size * 0.4f))
    }
}

/** A [RemoteCircleButton] with a caption underneath — used wherever the icon alone wouldn't be self-explanatory (Ch +, Smart Hub, Sources...). */
@Composable
private fun LabeledCircleButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    size: Dp = 64.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RemoteCircleButton(
            icon = icon,
            contentDescription = label,
            enabled = enabled,
            size = size,
            containerColor = containerColor,
            contentColor = contentColor,
            onClick = onClick,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ControlModeToggle(useDpadMode: Boolean, onModeChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !useDpadMode, onClick = { onModeChange(false) }, label = { Text("Trackpad") })
        FilterChip(selected = useDpadMode, onClick = { onModeChange(true) }, label = { Text("D-pad") })
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
            FilledTonalButton(
                enabled = enabled,
                onClick = onRightClick,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Mouse, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Right click")
            }
            FilledTonalButton(
                onClick = onToggleFullScreen,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    if (isFullScreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isFullScreen) "Exit full screen" else "Full screen")
            }
        }
    }
}

/**
 * A four-petal "wheel" D-pad, modeled on the reference remote: each
 * direction is a rounded petal shape rotated into place around a center hub,
 * rather than four separate square buttons. Built from plain rotated
 * rounded-rect boxes (no custom path math needed) — each petal box spans the
 * full wheel and is rotated 0/90/180/270 degrees with content aligned to its
 * top-center, so the visible wedge always lands in the right compass
 * position; the icon inside is counter-rotated so it stays upright.
 */
@Composable
private fun DPadWheel(enabled: Boolean, onKey: (RemoteKey) -> Unit) {
    val wheelSize = 260.dp
    val hubSize = 76.dp
    val petalColor = MaterialTheme.colorScheme.surfaceVariant
    val iconColor = MaterialTheme.colorScheme.onSurfaceVariant
    val hubColor = MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.size(wheelSize), contentAlignment = Alignment.Center) {
        DPadPetal(rotation = 0f, wheelSize = wheelSize, icon = Icons.Filled.KeyboardArrowUp, enabled = enabled, color = petalColor, iconColor = iconColor) { onKey(RemoteKey.DPAD_UP) }
        DPadPetal(rotation = 90f, wheelSize = wheelSize, icon = Icons.AutoMirrored.Filled.KeyboardArrowRight, enabled = enabled, color = petalColor, iconColor = iconColor) { onKey(RemoteKey.DPAD_RIGHT) }
        DPadPetal(rotation = 180f, wheelSize = wheelSize, icon = Icons.Filled.KeyboardArrowDown, enabled = enabled, color = petalColor, iconColor = iconColor) { onKey(RemoteKey.DPAD_DOWN) }
        DPadPetal(rotation = 270f, wheelSize = wheelSize, icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft, enabled = enabled, color = petalColor, iconColor = iconColor) { onKey(RemoteKey.DPAD_LEFT) }
        DPadCenterHub(size = hubSize, enabled = enabled, color = hubColor) { onKey(RemoteKey.DPAD_CENTER) }
    }
}

@Composable
private fun DPadPetal(
    rotation: Float,
    wheelSize: Dp,
    icon: ImageVector,
    enabled: Boolean,
    color: Color,
    iconColor: Color,
    onClick: () -> Unit,
) {
    val petalWidth = wheelSize * 0.42f
    val petalHeight = wheelSize * 0.5f

    Box(
        modifier = Modifier
            .size(wheelSize)
            .rotate(rotation),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 3.dp)
                .size(width = petalWidth, height = petalHeight)
                .clip(RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomStartPercent = 20, bottomEndPercent = 20))
                .background(color)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(modifier = Modifier.padding(top = 18.dp).rotate(-rotation)) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
private fun DPadCenterHub(size: Dp, enabled: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size * 0.18f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onPrimary),
        )
    }
}

/** Back / Home / Menu, directly under the wheel — same trio as the reference remote's bottom row. */
@Composable
private fun BottomNavRow(enabled: Boolean, onBack: () -> Unit, onHome: () -> Unit, onMenu: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        RemoteCircleButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", enabled, onClick = onBack)
        RemoteCircleButton(Icons.Filled.Home, "Home", enabled, onClick = onHome)
        RemoteCircleButton(Icons.Filled.Menu, "Menu", enabled, onClick = onMenu)
    }
}

/** The wide play/pause pill flanked by smaller rewind/fast-forward circles. */
@Composable
private fun MediaPillRow(enabled: Boolean, onRewind: () -> Unit, onPlayPause: () -> Unit, onFastForward: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteCircleButton(Icons.Filled.FastRewind, "Rewind", enabled, size = 52.dp, onClick = onRewind)
        FilledTonalButton(
            enabled = enabled,
            onClick = onPlayPause,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            shape = RoundedCornerShape(50),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Play/Pause", modifier = Modifier.size(28.dp))
        }
        RemoteCircleButton(Icons.Filled.FastForward, "Fast forward", enabled, size = 52.dp, onClick = onFastForward)
    }
}

@Composable
private fun VolumeChannelSection(capabilities: DeviceCapabilities, enabled: Boolean, viewModel: RemoteViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (capabilities.supportsVolume) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                LabeledCircleButton(Icons.Filled.VolumeDown, "Vol −", enabled, onClick = viewModel::volumeDown)
                LabeledCircleButton(Icons.Filled.VolumeOff, "Mute", enabled, onClick = viewModel::mute)
                LabeledCircleButton(Icons.Filled.VolumeUp, "Vol +", enabled, onClick = viewModel::volumeUp)
            }
        }
        if (capabilities.supportsChannels) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                LabeledCircleButton(Icons.Filled.KeyboardArrowDown, "Ch −", enabled) { viewModel.sendKey(RemoteKey.CHANNEL_DOWN) }
                LabeledCircleButton(Icons.Filled.KeyboardArrowUp, "Ch +", enabled) { viewModel.sendKey(RemoteKey.CHANNEL_UP) }
            }
        }
        // No per-input list/select API on this device — each press just
        // steps to the next input, same as the physical remote's Source
        // button. Watch the TV screen and keep pressing until it lands on
        // the input you want (e.g. HDMI 2).
        if (capabilities.supportsSourceCycle) {
            LabeledCircleButton(Icons.AutoMirrored.Filled.Input, "Source", enabled) { viewModel.sendKey(RemoteKey.INPUT_SOURCE) }
        }
    }
}

/**
 * Tapping "Keyboard" immediately requests focus, which brings up the
 * phone's own on-screen keyboard. Every keystroke streams to the PC live
 * via [onTextChanged] rather than waiting for a Send button. The mic button
 * (see [VoiceDictationButton]) feeds into the exact same live-diff path —
 * from the PC's point of view, dictated words arrive no differently than typed ones.
 */
@Composable
private fun KeyboardEntry(enabled: Boolean, onTextChanged: (old: String, new: String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    if (!expanded) {
        LabeledCircleButton(Icons.Filled.Keyboard, "Keyboard", enabled) { expanded = true }
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
            label = { Text("Typing or voice sends live to the PC") },
            leadingIcon = {
                VoiceDictationButton(
                    enabled = enabled,
                    currentText = text,
                    onTextChanged = { old, new ->
                        onTextChanged(old, new)
                        text = new
                    },
                )
            },
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
 * Live speech-to-text dictation: tap to start listening, tap again (or pause
 * speaking) to stop. Partial results stream in as the user talks, each one
 * replacing the previous partial via the same old/new diffing [onTextChanged]
 * already used for typed input, so a word being "corrected" as recognition
 * refines it (e.g. "won" -> "one") sends the right backspace-then-retype
 * rather than just appending. Requires RECORD_AUDIO, requested on first tap.
 */
@Composable
private fun VoiceDictationButton(
    enabled: Boolean,
    currentText: String,
    onTextChanged: (old: String, new: String) -> Unit,
) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var hasPermission by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    }

    DisposableEffect(Unit) {
        onDispose { recognizer?.destroy() }
    }

    fun startListening() {
        val activeRecognizer = recognizer ?: return
        val baseText = currentText
        var lastPartial = ""

        activeRecognizer.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit

                override fun onError(error: Int) {
                    isListening = false
                }

                override fun onResults(results: Bundle?) {
                    // The final transcript can differ from (and is usually more
                    // accurate than) the last partial — e.g. a word recognized
                    // as "won" mid-utterance settling on "one" once the whole
                    // phrase is heard. Reconcile against it rather than trusting
                    // the last partial as final.
                    val final = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (final != null && final != lastPartial) {
                        onTextChanged(baseText + lastPartial, baseText + final)
                    }
                    isListening = false
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (partial != lastPartial) {
                        onTextChanged(baseText + lastPartial, baseText + partial)
                        lastPartial = partial
                    }
                }
            },
        )

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        activeRecognizer.startListening(intent)
        isListening = true
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) startListening()
    }

    IconButton(
        enabled = enabled && recognizer != null,
        onClick = {
            when {
                isListening -> {
                    recognizer?.stopListening()
                    isListening = false
                }
                !hasPermission -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                else -> startListening()
            }
        },
    ) {
        Icon(
            if (isListening) Icons.Filled.MicOff else Icons.Filled.Mic,
            contentDescription = if (isListening) "Stop voice dictation" else "Start voice dictation",
            tint = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Decodes the PC-supplied `data:image/png;base64,…` icon. Falls back to a
 * hand-drawn globe glyph for URL buttons (a real icon can't be extracted
 * from a web address the way it can from a .exe) and a generic list glyph
 * for everything else with no icon (Quick Actions, any app whose icon
 * couldn't be resolved).
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
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            GlobeIcon(size = size * 0.7f, tint = MaterialTheme.colorScheme.primary)
        }
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.List,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
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
