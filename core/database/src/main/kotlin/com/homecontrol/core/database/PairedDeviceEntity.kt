package com.homecontrol.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted form of [com.homecontrol.core.model.PairedDevice]. Capability
 * flags are flattened to columns rather than a nested/serialized blob so
 * they stay queryable — this table is small, that tradeoff is cheap.
 */
@Entity(tableName = "paired_devices")
data class PairedDeviceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val manufacturer: String,
    val model: String,
    val ipAddress: String,
    val macAddress: String?,
    val deviceType: String,
    val firmwareVersion: String?,
    val pluginId: String,
    val supportsPower: Boolean,
    val supportsVolume: Boolean,
    val supportsKeyboard: Boolean,
    val supportsApps: Boolean,
    val supportsTouchpad: Boolean,
    val supportsMouse: Boolean,
    val supportsPTZ: Boolean,
    val supportsWakeOnLan: Boolean,
    val supportsScreenMirror: Boolean,
    val supportsChannels: Boolean,
    val supportsInputSource: Boolean,
    val supportsQuickActions: Boolean,
    val supportsSourceCycle: Boolean,
    val supportsSmartHub: Boolean,
    val supportsMediaTransport: Boolean,
    val supportsVoiceAssist: Boolean,
)
