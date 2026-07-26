package com.core.mdm

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.UserHandle
import android.util.Log
import com.core.mdm.policy.DevicePolicyHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MdmDeviceAdmin : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "MdmDeviceAdmin"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device Admin enabled — applying baseline restrictions")
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                DevicePolicyHelper.getInstance(context).applyBaselineRestrictions()
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        if (UninstallFlag.pending) ""
        else "This device is managed. Contact your administrator to unenroll."

    override fun onDisabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device Admin disabled")
        if (UninstallFlag.pending) {
            UninstallFlag.pending = false
            val uninstallIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(uninstallIntent)
        }
    }

    override fun onPasswordFailed(context: Context, intent: Intent, user: UserHandle) {
        val helper = DevicePolicyHelper.getInstance(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val attempts = runCatching { helper.dpm.currentFailedPasswordAttempts }.getOrDefault(0)
            Log.w(TAG, "Password failed (attempt $attempts)")
            if (attempts >= 10) {
                Log.e(TAG, "Wipe threshold reached")
                helper.wipeDevice()
            }
        }
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent, user: UserHandle) {
        Log.i(TAG, "Device password succeeded")
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        Log.i(TAG, "Provisioning complete")
        DevicePolicyHelper.getInstance(context).applyBaselineRestrictions()
    }
}
