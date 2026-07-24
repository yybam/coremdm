package com.core.mdm.telemetry

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TelemetryCollector(private val context: Context) {

    suspend fun collect(): DeviceMetrics = withContext(Dispatchers.IO) {
        val batteryIntent = context.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level  = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale  = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val batteryPct = if (scale > 0) level * 100 / scale else -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                       status == BatteryManager.BATTERY_STATUS_FULL

        val dataStat   = StatFs(Environment.getDataDirectory().path)
        val totalMb    = dataStat.totalBytes / 1_048_576
        val freeMb     = dataStat.freeBytes  / 1_048_576
        val usedMb     = totalMb - freeMb
        val freePct    = if (totalMb > 0) ((freeMb * 100) / totalMb).toInt() else 0

        val cm   = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val netType = when {
            caps == null -> "None"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)     -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Other"
        }

        val wm   = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ssid = runCatching {
            wm.connectionInfo.ssid?.trim('"')
                ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
        }.getOrNull()
        val ipRaw = wm.connectionInfo.ipAddress
        val ip = if (ipRaw != 0)
            "${ipRaw and 0xff}.${(ipRaw shr 8) and 0xff}.${(ipRaw shr 16) and 0xff}.${(ipRaw shr 24) and 0xff}"
        else null

        val patch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Build.VERSION.SECURITY_PATCH else "N/A"

        DeviceMetrics(
            batteryPercent    = batteryPct,
            isCharging        = charging,
            storageUsedMb     = usedMb,
            storageTotalMb    = totalMb,
            storageFreePercent = freePct,
            networkType       = netType,
            wifiSsid          = ssid,
            ipAddress         = ip,
            osVersion         = Build.VERSION.RELEASE,
            securityPatch     = patch,
            deviceModel       = "${Build.MANUFACTURER} ${Build.MODEL}",
            collectedAt       = System.currentTimeMillis(),
        )
    }
}
