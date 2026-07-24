package com.core.mdm.telemetry

data class DeviceMetrics(
    val batteryPercent: Int,
    val isCharging: Boolean,
    val storageUsedMb: Long,
    val storageTotalMb: Long,
    val storageFreePercent: Int,
    val networkType: String,
    val wifiSsid: String?,
    val ipAddress: String?,
    val osVersion: String,
    val securityPatch: String,
    val deviceModel: String,
    val collectedAt: Long,
) {
    val storageUsedPercent: Int
        get() = if (storageTotalMb > 0) ((storageUsedMb * 100) / storageTotalMb).toInt() else 0
}
