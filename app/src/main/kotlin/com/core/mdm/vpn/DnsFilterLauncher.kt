package com.core.mdm.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import androidx.core.content.ContextCompat

/** Starts the DNS filter from places with no UI, such as BootReceiver. */
object DnsFilterLauncher {

    private const val TAG = "DnsFilterLauncher"

    /** Starts the filter if it was switched on before. Returns true if it was started. */
    fun startIfEnabled(context: Context): Boolean {
        if (!FilterSettings.getInstance(context).enabled) return false
        // Non-null means the VPN permission was never given or was taken by another VPN app;
        // only the permission dialog (in the app) can fix that.
        if (VpnService.prepare(context) != null) {
            Log.w(TAG, "Filter is on but VPN permission is missing — open the app to switch it on again")
            return false
        }
        ContextCompat.startForegroundService(
            context,
            Intent(context, DnsVpnService::class.java).setAction(DnsVpnService.ACTION_START),
        )
        Log.i(TAG, "DNS filter restarted")
        return true
    }
}
