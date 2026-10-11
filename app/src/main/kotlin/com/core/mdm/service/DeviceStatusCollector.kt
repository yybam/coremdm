package com.core.mdm.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.telephony.TelephonyManager

object DeviceStatusCollector {

    fun getBatteryLevel(context: Context): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return -1
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    }

    fun isCharging(context: Context): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return false
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
               status == BatteryManager.BATTERY_STATUS_FULL
    }

    fun getNetworkInfo(context: Context): Map<String, Any> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return mapOf("networkType" to "none")
        val caps = cm.getNetworkCapabilities(network) ?: return mapOf("networkType" to "none")
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                @Suppress("DEPRECATION")
                val rssi = (context.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                    .connectionInfo.rssi
                mapOf("networkType" to "wifi", "wifiRssi" to rssi)
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                mapOf("networkType" to cellularType(tm))
            }
            else -> mapOf("networkType" to "other")
        }
    }

    private fun cellularType(tm: TelephonyManager): String = when (tm.dataNetworkType) {
        TelephonyManager.NETWORK_TYPE_NR                         -> "5G"
        TelephonyManager.NETWORK_TYPE_LTE                        -> "4G LTE"
        TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_HSPAP,
        TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_HSDPA                      -> "3G"
        TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_GPRS                       -> "2G"
        else                                                      -> "cellular"
    }
}
