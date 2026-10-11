package com.core.mdm.policy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ScheduledLockdownReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val helper = DevicePolicyHelper.getInstance(context)
        when (intent.action) {
            ScheduledLockdownManager.ACTION_LOCK -> {
                Log.i(TAG, "Scheduled lockdown — locking device")
                helper.lockNow()
            }
            ScheduledLockdownManager.ACTION_UNLOCK -> {
                Log.i(TAG, "Scheduled lockdown ended — device unlockable")
                // Keyguard stays active; user unlocks with PIN.
                // Re-schedule the next day's lock alarm so it recurs without the service.
            }
        }
    }

    companion object { private const val TAG = "ScheduledLockdownRcvr" }
}
