package com.core.mdm.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.core.mdm.policy.DevicePolicyHelper

/**
 * Private DNS set to a specific hostname makes Android send every lookup straight to that
 * server, encrypted, around the VPN — so the filter would see nothing. While the filter runs,
 * this switches Private DNS back to Automatic (Device Owner only).
 */
object PrivateDnsGuard {

    private const val TAG = "PrivateDnsGuard"

    /** True when Private DNS is pinned to a hostname, i.e. DNS bypasses the filter. */
    fun isBypassing(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val mode = runCatching {
            Settings.Global.getString(context.contentResolver, "private_dns_mode")
        }.getOrNull()
        if (mode == "hostname") return true
        // The setting can be unreadable on newer Android; the networks themselves report it too.
        return runCatching {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            @Suppress("DEPRECATION")
            cm.allNetworks.any { cm.getLinkProperties(it)?.privateDnsServerName != null }
        }.getOrDefault(false)
    }

    /** Switches hostname-mode Private DNS off. Returns false if it's still bypassing the filter. */
    fun turnOffIfBypassing(context: Context): Boolean {
        if (!isBypassing(context)) return true
        val helper = DevicePolicyHelper.getInstance(context)
        if (!helper.isDeviceOwner) {
            Log.w(TAG, "Private DNS is set to a hostname and we're not Device Owner — lookups bypass the filter")
            return false
        }
        helper.clearPrivateDns()
        Log.i(TAG, "Private DNS set back to Automatic so lookups go through the filter")
        return true
    }
}
