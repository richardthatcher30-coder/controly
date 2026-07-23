package com.homecontrol.feature.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homecontrol.core.data.PairedDeviceRepository
import com.homecontrol.core.discovery.DeviceDiscoveryScanner
import com.homecontrol.core.model.DeviceType
import com.homecontrol.core.model.DiscoveredDevice
import com.homecontrol.core.model.DiscoveryProtocol
import com.homecontrol.core.model.PairingInput
import com.homecontrol.core.model.PairingResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DevicesUiState(
    val isScanning: Boolean = false,
    val discoveredDevices: List<DiscoveredDevice> = emptyList(),
    val pairingState: PairingUiState? = null,
)

/** Device types a user can pick when adding a device manually by IP — see [DevicesViewModel.addManualDevice]. */
val MANUAL_DEVICE_TYPE_OPTIONS: List<Pair<String, DeviceType>> = listOf(
    "Fire TV" to DeviceType.FIRE_TV,
    "Android TV / Google TV" to DeviceType.ANDROID_TV,
    "Sony TV" to DeviceType.SONY_TV,
    "Samsung TV" to DeviceType.SAMSUNG_TV,
    "Windows PC" to DeviceType.WINDOWS_PC,
)

sealed interface PairingUiState {
    data class InProgress(val deviceName: String) : PairingUiState
    data class AwaitingCode(val deviceName: String, val message: String) : PairingUiState
    data class Success(val deviceName: String) : PairingUiState
    data class Failed(val deviceName: String, val reason: String) : PairingUiState
}

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val scanner: DeviceDiscoveryScanner,
    private val pairedDeviceRepository: PairedDeviceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private var pendingDevice: DiscoveredDevice? = null
    private val mergedByIp = mutableMapOf<String, DiscoveredDevice>()

    fun startScan() {
        if (_uiState.value.isScanning) return

        mergedByIp.clear()
        _uiState.update { it.copy(isScanning = true, discoveredDevices = emptyList()) }

        scanJob = viewModelScope.launch {
            scanner.scan().collect { device ->
                val merged = mergeByIp(device)

                // Only surface devices a plugin actually claims — SSDP in
                // particular picks up routers, printers, and Windows' own
                // built-in UPnP host, none of which we can do anything with.
                if (!pairedDeviceRepository.isPairable(merged)) return@collect

                _uiState.update { state ->
                    state.copy(discoveredDevices = state.discoveredDevices.filterNot { it.ipAddress == merged.ipAddress } + merged)
                }
            }
            _uiState.update { it.copy(isScanning = false) }
        }
    }

    /**
     * The same physical device is routinely reported more than once — a TV
     * can answer mDNS under one friendly name and SSDP under a totally
     * different generic "Linux/…, UPnP/…" server string. Left unmerged, that
     * shows up as several confusing rows for one device, and worse, a
     * generic protocol string like "…Chromecast/1.6.18" can get matched by
     * the wrong plugin (a Sony TV mistaken for a real Chromecast/Android TV
     * target) even though another record for the same IP clearly identifies
     * it as Sony. Folding every signal seen for an IP into one record before
     * asking a plugin to claim it keeps that decision consistent regardless
     * of which protocol happened to report first.
     */
    private fun mergeByIp(device: DiscoveredDevice): DiscoveredDevice {
        val existing = mergedByIp[device.ipAddress]
        val merged = if (existing == null) {
            device.copy(discoveryId = "device:${device.ipAddress}")
        } else {
            val nameWinnerIsCandidate = preferredName(existing.name, device.name) == device.name
            existing.copy(
                name = if (nameWinnerIsCandidate) device.name else existing.name,
                // The protocol tag shown to the user should describe whichever
                // record's name actually won, not just whichever arrived first.
                discoveryProtocol = if (nameWinnerIsCandidate) device.discoveryProtocol else existing.discoveryProtocol,
                model = listOfNotNull(existing.model, existing.name, device.name, device.model)
                    .distinct()
                    .joinToString(" "),
                macAddress = existing.macAddress ?: device.macAddress,
                manufacturer = existing.manufacturer ?: device.manufacturer,
                serviceRecords = existing.serviceRecords + device.serviceRecords,
            )
        }
        mergedByIp[device.ipAddress] = merged
        return merged
    }

    /** Generic SSDP "SERVER:" boilerplate (e.g. "Linux/4.9.125, UPnP/1.0, Chromecast/1.6.18") is never as useful to show as an actual friendly/model name. */
    private fun preferredName(current: String, candidate: String): String {
        val currentIsGeneric = current.contains("UPnP/")
        val candidateIsGeneric = candidate.contains("UPnP/")
        return if (currentIsGeneric && !candidateIsGeneric) candidate else current
    }

    /**
     * Fire TV (and any other plain-ADB device) has no discovery broadcast of
     * its own — see [com.homecontrol.core.discovery.NetworkDeviceDiscoveryScanner],
     * whose mDNS/SSDP/UDP probes only ever pick up devices that advertise
     * themselves. This bypasses discovery entirely: the user already knows
     * the IP and device type, so there's nothing left to detect.
     */
    fun addManualDevice(name: String, ipAddress: String, deviceType: DeviceType) {
        pairDevice(
            DiscoveredDevice(
                discoveryId = "manual:$ipAddress",
                name = name.ifBlank { deviceType.name },
                ipAddress = ipAddress,
                deviceTypeHint = deviceType,
                discoveryProtocol = DiscoveryProtocol.MANUAL,
            ),
        )
    }

    fun pairDevice(device: DiscoveredDevice) {
        pendingDevice = device
        _uiState.update { it.copy(pairingState = PairingUiState.InProgress(device.name)) }

        viewModelScope.launch {
            applyPairingResult(device, pairedDeviceRepository.pair(device, PairingInput.None))
        }
    }

    fun submitPairingCode(code: String) {
        val device = pendingDevice ?: return
        _uiState.update { it.copy(pairingState = PairingUiState.InProgress(device.name)) }

        viewModelScope.launch {
            applyPairingResult(device, pairedDeviceRepository.pair(device, PairingInput.Code(code)))
        }
    }

    private fun applyPairingResult(device: DiscoveredDevice, result: PairingResult) {
        val newState = when (result) {
            is PairingResult.Success -> {
                pendingDevice = null
                PairingUiState.Success(device.name)
            }
            is PairingResult.AwaitingApproval -> PairingUiState.AwaitingCode(device.name, result.message)
            is PairingResult.Failed -> {
                pendingDevice = null
                PairingUiState.Failed(device.name, result.detail ?: result.reason.name)
            }
        }
        _uiState.update { it.copy(pairingState = newState) }
    }

    fun dismissPairingResult() {
        pendingDevice = null
        _uiState.update { it.copy(pairingState = null) }
    }
}
