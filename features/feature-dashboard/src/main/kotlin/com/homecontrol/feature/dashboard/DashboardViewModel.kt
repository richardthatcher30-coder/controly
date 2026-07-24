package com.homecontrol.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homecontrol.core.data.PairedDeviceRepository
import com.homecontrol.core.model.PairedDevice
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val pairedDeviceRepository: PairedDeviceRepository,
) : ViewModel() {

    val pairedDevices: StateFlow<List<PairedDevice>> = pairedDeviceRepository.observePairedDevices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun removeDevice(device: PairedDevice) {
        viewModelScope.launch { pairedDeviceRepository.removeDevice(device) }
    }

    fun renameDevice(device: PairedDevice, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == device.name) return
        viewModelScope.launch { pairedDeviceRepository.renameDevice(device, trimmed) }
    }
}
