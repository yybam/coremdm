package com.core.mdm.vpn

import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.core.mdm.policy.DevicePolicyHelper

/**
 * Device Owner protections that go with the DNS filter:
 *  - always-on VPN with lockdown: Android starts the filter itself at boot, the user can't switch
 *    it off, and with lockdown there is no internet while it isn't running;
 *  - Private DNS greyed out in Settings, so DNS can't be pointed around the filter;
 *  - this app can't be uninstalled.
 *
 * [release] undoes the first two when the filter is switched off. The uninstall block is left
 * alone — it may also have been set from the console.
 */
object FilterLockdown {

    private const val TAG = "FilterLockdown"

    /**
     * Lockdown = Android blocks all traffic that doesn't go through the VPN. Off, because this
     * VPN carries only DNS: tested on the emulator (Android 14), lockdown blocked every other
     * packet even while the filter was running — no internet at all. The filter still stays
     * always-on (Android keeps it running and restarts it); there's just no kill switch.
     */
    private const val LOCKDOWN = false

    fun apply(context: Context) {
        val helper = DevicePolicyHelper.getInstance(context)
        if (!helper.isDeviceOwner) {
            Log.w(TAG, "Not Device Owner — always-on VPN, Private DNS lock and uninstall block unavailable")
            return
        }
        runCatching { helper.dpm.setAlwaysOnVpnPackage(helper.admin, context.packageName, LOCKDOWN) }
            .onSuccess { Log.i(TAG, "Always-on VPN set (lockdown=$LOCKDOWN)") }
            .onFailure { Log.e(TAG, "setAlwaysOnVpnPackage: ${it.message}") }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            helper.restrict(UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
        }
        helper.setSelfUninstallBlocked(true)
    }

    fun release(context: Context) {
        val helper = DevicePolicyHelper.getInstance(context)
        if (!helper.isDeviceOwner) return
        runCatching {
            // Only clear it if it's ours — never another VPN the admin set up.
            if (helper.dpm.getAlwaysOnVpnPackage(helper.admin) == context.packageName) {
                helper.dpm.setAlwaysOnVpnPackage(helper.admin, null, false)
                Log.i(TAG, "Always-on VPN cleared")
            }
        }.onFailure { Log.e(TAG, "clear always-on VPN: ${it.message}") }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            helper.unrestrict(UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
        }
    }
}
