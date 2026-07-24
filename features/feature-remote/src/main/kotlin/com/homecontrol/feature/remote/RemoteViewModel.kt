package com.homecontrol.feature.remote

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homecontrol.core.data.PairedDeviceRepository
import com.homecontrol.core.data.RemoteControlRepository
import com.homecontrol.core.model.AppInfo
import com.homecontrol.core.model.ConnectionResult
import com.homecontrol.core.model.MouseButton
import com.homecontrol.core.model.PairedDevice
import com.homecontrol.core.model.RemoteKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val CONNECT_TIMEOUT_MS = 20_000L

data class RemoteUiState(
    val device: PairedDevice? = null,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val connectionError: String? = null,
)

class RemoteViewModel(
    savedStateHandle: SavedStateHandle,
    private val pairedDeviceRepository: PairedDeviceRepository,
    private val remoteControlRepository: RemoteControlRepository,
) : ViewModel() {

    private val deviceId: String = checkNotNull(savedStateHandle["deviceId"])

    private val _uiState = MutableStateFlow(RemoteUiState())
    val uiState: StateFlow<RemoteUiState> = _uiState.asStateFlow()

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    /** Whatever app buttons were configured on the PC — the phone only ever displays and launches these, never defines them. */
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _quickActions = MutableStateFlow<List<AppInfo>>(emptyList())
    /** Windows-only system shortcuts (Windows key, Task Manager, open a folder, ...) the user enabled on the PC. */
    val quickActions: StateFlow<List<AppInfo>> = _quickActions.asStateFlow()

    private val _isLoadingQuickActions = MutableStateFlow(false)
    val isLoadingQuickActions: StateFlow<Boolean> = _isLoadingQuickActions.asStateFlow()

    private val _sources = MutableStateFlow<List<AppInfo>>(emptyList())
    /** TV input sources (HDMI 1, HDMI 2, ...) queried live from the device — currently just Sony. */
    val sources: StateFlow<List<AppInfo>> = _sources.asStateFlow()

    private val _isLoadingSources = MutableStateFlow(false)
    val isLoadingSources: StateFlow<Boolean> = _isLoadingSources.asStateFlow()

    private var hasConnected = false
    private var hasLoadedApps = false
    private var hasLoadedQuickActions = false
    private var hasLoadedSources = false

    init {
        viewModelScope.launch {
            pairedDeviceRepository.observePairedDevices()
                .map { devices -> devices.firstOrNull { it.id == deviceId } }
                .collect { device ->
                    _uiState.update { it.copy(device = device) }
                    if (device != null && !hasConnected) {
                        hasConnected = true
                        connect(device)
                    }
                }
        }
    }

    fun sendKey(key: RemoteKey) = withDevice { remoteControlRepository.sendKey(it, key) }

    fun powerOn() = withDevice { remoteControlRepository.powerOn(it) }

    fun powerOff() = withDevice { remoteControlRepository.powerOff(it) }

    fun volumeUp() = withDevice { remoteControlRepository.volumeUp(it) }

    fun volumeDown() = withDevice { remoteControlRepository.volumeDown(it) }

    fun mute() = withDevice { remoteControlRepository.mute(it) }

    fun moveMouse(deltaX: Int, deltaY: Int) = withDevice { remoteControlRepository.moveMouse(it, deltaX, deltaY) }

    fun clickMouse(button: MouseButton) = withDevice { remoteControlRepository.clickMouse(it, button) }

    fun launchApp(appId: String) = withDevice { remoteControlRepository.launchApp(it, appId) }

    /** Some devices' network stacks (e.g. an Android TV's ADB daemon right after a fresh pairing approval) briefly refuse a second connection — retrying without leaving the screen is often all it takes. */
    fun retryConnect() {
        val device = _uiState.value.device ?: return
        connect(device)
    }

    /** Fetched once per screen visit (apps rarely change mid-session) and cached — called when connected and again if the user opens the Apps grid after a failed first attempt. */
    fun loadAppsIfNeeded() {
        if (hasLoadedApps) return
        val device = _uiState.value.device ?: return
        hasLoadedApps = true
        viewModelScope.launch {
            _isLoadingApps.update { true }
            runCatching { remoteControlRepository.getInstalledApps(device) }
                .onSuccess { apps -> _apps.update { apps } }
                .onFailure { hasLoadedApps = false }
            _isLoadingApps.update { false }
        }
    }

    /** Adds a URL button on the PC side (the only kind the phone can add, since it can't browse the PC's filesystem) and refreshes the grid to pick it up. */
    fun addUrlButton(label: String, url: String) {
        val device = _uiState.value.device ?: return
        viewModelScope.launch {
            val added = runCatching { remoteControlRepository.addAppButton(device, label, url) }.getOrDefault(false)
            if (added) {
                hasLoadedApps = false
                loadAppsIfNeeded()
            }
        }
    }

    fun loadQuickActionsIfNeeded() {
        if (hasLoadedQuickActions) return
        val device = _uiState.value.device ?: return
        hasLoadedQuickActions = true
        viewModelScope.launch {
            _isLoadingQuickActions.update { true }
            runCatching { remoteControlRepository.getQuickActions(device) }
                .onSuccess { actions -> _quickActions.update { actions } }
                .onFailure { hasLoadedQuickActions = false }
            _isLoadingQuickActions.update { false }
        }
    }

    fun triggerQuickAction(actionId: String) = withDevice { remoteControlRepository.triggerQuickAction(it, actionId) }

    fun loadSourcesIfNeeded() {
        if (hasLoadedSources) return
        val device = _uiState.value.device ?: return
        hasLoadedSources = true
        viewModelScope.launch {
            _isLoadingSources.update { true }
            runCatching { remoteControlRepository.getSources(device) }
                .onSuccess { sources -> _sources.update { sources } }
                .onFailure { hasLoadedSources = false }
            _isLoadingSources.update { false }
        }
    }

    fun selectSource(sourceId: String) = withDevice { remoteControlRepository.selectSource(it, sourceId) }

    /**
     * Called on every soft-keyboard change so typing streams to the PC
     * live instead of needing a Send button. Compose's text field always
     * reports the *full* current text, not a delta, so this diffs against
     * what was there a moment ago: appended characters get typed, a
     * shorter result sends that many backspaces. Anything else (paste,
     * mid-string edits, autocomplete swaps) falls back to just retyping
     * the whole field — imperfect, but rare in practice.
     */
    fun onRemoteTextChanged(oldText: String, newText: String) = withDevice { device ->
        when {
            newText.length > oldText.length && newText.startsWith(oldText) -> {
                remoteControlRepository.sendText(device, newText.substring(oldText.length))
            }
            newText.length < oldText.length && oldText.startsWith(newText) -> {
                repeat(oldText.length - newText.length) {
                    remoteControlRepository.sendKey(device, RemoteKey.BACKSPACE)
                }
            }
            newText != oldText -> {
                remoteControlRepository.sendText(device, newText)
            }
        }
    }

    private fun connect(device: PairedDevice) {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true) }
            // A hard backstop: whatever the plugin does internally, the UI
            // must never spin forever with no way out. A real hang here
            // (rather than a clean failure) has happened in practice on
            // flaky hardware, and previously left the screen stuck with no
            // Retry button since connectionError never got set.
            val result = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { remoteControlRepository.connect(device) }
                ?: ConnectionResult.Failed("Timed out connecting to ${device.name}. Try again.")
            _uiState.update {
                it.copy(
                    isConnecting = false,
                    isConnected = result is ConnectionResult.Connected,
                    connectionError = when (result) {
                        ConnectionResult.Connected -> null
                        ConnectionResult.PairingRequired -> "Pairing expired — re-pair this device from the Devices screen."
                        is ConnectionResult.Failed -> result.reason
                    },
                )
            }
            if (result is ConnectionResult.Connected && device.capabilities.supportsApps) {
                loadAppsIfNeeded()
            }
        }
    }

    /**
     * Every control action goes through here specifically so a plugin
     * throwing (e.g. "not connected" when a session dropped between button
     * taps) surfaces as an on-screen error instead of crashing the app —
     * this is a real thing that happened during testing, not a hypothetical.
     */
    private fun withDevice(action: suspend (PairedDevice) -> Unit) {
        val device = _uiState.value.device ?: return
        viewModelScope.launch {
            runCatching { action(device) }
                .onFailure { error ->
                    _uiState.update { it.copy(isConnected = false, connectionError = error.message ?: "Command failed") }
                }
        }
    }
}
