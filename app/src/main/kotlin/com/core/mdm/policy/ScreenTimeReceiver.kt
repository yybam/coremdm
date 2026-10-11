package com.core.mdm.policy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ScreenTimeReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val (enabled, start, end) = ScreenTimeScheduler.load(ctx)
        when (intent.action) {
            ScreenTimeScheduler.ACTION_LOCK -> {
                if (enabled) {
                    Log.i(TAG, "Screen time end — locking device")
                    DevicePolicyHelper.getInstance(ctx).lockNow()
                }
            }
            ScreenTimeScheduler.ACTION_UNLOCK -> {
                Log.i(TAG, "Screen time start — device available")
                // Device is already locked; user unlocks normally with their PIN when ready
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                if (enabled) {
                    Log.i(TAG, "Boot — rescheduling screen time: $start – $end")
                    ScreenTimeScheduler.schedule(ctx, start, end)
                }
            }
        }
    }

    companion object { private const val TAG = "ScreenTimeReceiver" }
}
