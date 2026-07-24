package com.homecontrol.feature.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.homecontrol.core.designsystem.R
import com.homecontrol.core.model.DeviceType
import com.homecontrol.core.model.PairedDevice

/**
 * Shows paired devices once there are any; an empty-state before that.
 * Tapping a device tile opens `feature-remote` for it; swiping one left
 * reveals a delete action, swiping one right opens a rename dialog (e.g.
 * "Bedroom TV", "Living Room TV" — purely a local label, never sent to the
 * device itself).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAddDeviceClick: () -> Unit,
    onAddCameraClick: () -> Unit,
    onDeviceClick: (PairedDevice) -> Unit,
    onCamerasClick: () -> Unit,
    onAboutClick: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    var showAddPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Controly") },
                actions = {
                    TextButton(onClick = onCamerasClick) {
                        Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cameras")
                    }
                    TextButton(onClick = onAboutClick) {
                        Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("About")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Add device") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = { showAddPicker = true },
            )
        },
    ) { paddingValues ->
        if (pairedDevices.isEmpty()) {
            DashboardEmptyState(paddingValues)
        } else {
            PairedDevicesList(
                devices = pairedDevices,
                paddingValues = paddingValues,
                onDeviceClick = onDeviceClick,
                onDeviceDelete = viewModel::removeDevice,
                onDeviceRename = viewModel::renameDevice,
            )
        }
    }

    if (showAddPicker) {
        AddPickerDialog(
            onDismiss = { showAddPicker = false },
            onPickRemote = {
                showAddPicker = false
                onAddDeviceClick()
            },
            onPickCamera = {
                showAddPicker = false
                onAddCameraClick()
            },
        )
    }
}

/** "Add device" covers two very different flows (TVs/PCs vs. cameras) — this is just a fork in the road, not a destination of its own. */
@Composable
private fun AddPickerDialog(
    onDismiss: () -> Unit,
    onPickRemote: () -> Unit,
    onPickCamera: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What do you want to add?") },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AddPickerOption(
                    icon = Icons.Filled.Tv,
                    label = "Remote",
                    onClick = onPickRemote,
                    modifier = Modifier.weight(1f),
                )
                AddPickerOption(
                    icon = Icons.Filled.Videocam,
                    label = "Camera",
                    onClick = onPickCamera,
                    modifier = Modifier.weight(1f),
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun AddPickerOption(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun DashboardEmptyState(paddingValues: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_home_control_logo),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "No devices yet",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap “Add device” to search your network.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PairedDevicesList(
    devices: List<PairedDevice>,
    paddingValues: PaddingValues,
    onDeviceClick: (PairedDevice) -> Unit,
    onDeviceDelete: (PairedDevice) -> Unit,
    onDeviceRename: (PairedDevice, String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = devices, key = { it.id }) { device ->
            SwipeableDeviceRow(
                device = device,
                onClick = { onDeviceClick(device) },
                onDelete = { onDeviceDelete(device) },
                onRename = { newName -> onDeviceRename(device, newName) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableDeviceRow(
    device: PairedDevice,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
) {
    var showRenameDialog by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    true
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    // Reject the dismiss (card snaps back) — swiping right
                    // opens the rename dialog rather than removing the card.
                    showRenameDialog = true
                    false
                }
                SwipeToDismissBoxValue.Settled -> true
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val isRename = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isRename) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                        RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 24.dp),
                contentAlignment = if (isRename) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Text(
                    text = if (isRename) "Rename" else "Delete",
                    color = if (isRename) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        PairedDeviceCard(device = device, onClick = onClick)
    }

    if (showRenameDialog) {
        RenameDeviceDialog(
            currentName = device.name,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                onRename(newName)
                showRenameDialog = false
            },
        )
    }
}

@Composable
private fun RenameDeviceDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename device") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name, e.g. Bedroom TV") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** TVs of every brand share the teal brand color; Windows PCs get the amber accent — a simple, deliberate two-way split rather than a color per exact [DeviceType]. */
@Composable
private fun accentColorFor(deviceType: DeviceType): Color =
    if (deviceType == DeviceType.WINDOWS_PC) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary

private fun iconFor(deviceType: DeviceType) = if (deviceType == DeviceType.WINDOWS_PC) Icons.Filled.Computer else Icons.Filled.Tv

@Composable
private fun PairedDeviceCard(device: PairedDevice, onClick: () -> Unit) {
    val accent = accentColorFor(device.deviceType)

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color = accent.copy(alpha = 0.14f), shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(iconFor(device.deviceType), contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = device.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "${device.manufacturer} · ${device.ipAddress}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "›",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
