package com.core.mdm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.core.mdm.policy.DevicePolicyHelper
import com.core.mdm.service.MdmCommandService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.LOCKED_BOOT_COMPLETED") return

        MdmCommandService.start(context)

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                Log.i("BootReceiver", "Boot complete — re-applying all active policies")
                val helper = DevicePolicyHelper.getInstance(context)

                // Core anti-bypass restrictions (safe boot, factory reset, add user)
                helper.applyBaselineRestrictions()

                // Re-apply status bar state — Android resets it on every boot; we persist the
                // intent in SharedPreferences so we can restore it here.
                if (helper.isStatusBarDisabled()) {
                    helper.setStatusBarDisabled(true)
                    Log.i("BootReceiver", "Status bar re-disabled")
                }

                // Re-apply MDM self-uninstall protection
                if (helper.isSelfUninstallBlocked()) {
                    helper.setSelfUninstallBlocked(true)
                    Log.i("BootReceiver", "MDM uninstall protection re-applied")
                }

                Log.i("BootReceiver", "Policy restoration complete")
            } catch (e: Exception) {
                Log.e("BootReceiver", "Error restoring policies: ${e.message}", e)
            } finally {
                pending.finish()
            }
        }
    }
}
