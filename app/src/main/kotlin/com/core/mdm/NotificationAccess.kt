package com.core.mdm

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * CoreMDM's own notifications (alarms, the background service, blocked-app notices). Android 13+
 * needs a runtime permission for them. As Device Owner we grant it to ourselves and lock it on,
 * so the phone's user can't switch CoreMDM's notifications off; otherwise MainActivity asks.
 */
object NotificationAccess {

    fun isGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Grants (and locks) the permission when CoreMDM is Device Owner. True if it's now granted. */
    fun grantAsOwner(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isDeviceOwnerApp(context.packageName)) return isGranted(context)
        runCatching {
            dpm.setPermissionGrantState(
                ComponentName(context, MdmDeviceAdmin::class.java), context.packageName,
                Manifest.permission.POST_NOTIFICATIONS, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
            )
        }.onFailure { Log.w("NotificationAccess", "Couldn't grant notifications: ${it.message}") }
        return isGranted(context)
    }
}
