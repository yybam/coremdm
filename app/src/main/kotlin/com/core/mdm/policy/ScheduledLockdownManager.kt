package com.core.mdm.policy

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

object ScheduledLockdownManager {
    private const val TAG       = "ScheduledLockdownMgr"
    const val ACTION_LOCK       = "com.core.mdm.scheduled.LOCK"
    const val ACTION_UNLOCK     = "com.core.mdm.scheduled.UNLOCK"
    private const val REQ_LOCK  = 3010
    private const val REQ_UNLOCK = 3011

    fun schedule(context: Context, lockTime: String, unlockTime: String) {
        cancel(context)
        setDailyAlarm(context, lockTime,   ACTION_LOCK,   REQ_LOCK)
        setDailyAlarm(context, unlockTime, ACTION_UNLOCK, REQ_UNLOCK)
        Log.i(TAG, "Scheduled lockdown: lock at $lockTime, unlock at $unlockTime")
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(buildIntent(context, ACTION_LOCK,   REQ_LOCK))
        am.cancel(buildIntent(context, ACTION_UNLOCK, REQ_UNLOCK))
        Log.i(TAG, "Scheduled lockdown cancelled")
    }

    private fun setDailyAlarm(context: Context, time: String, action: String, reqCode: Int) {
        val parts = time.split(":").mapNotNull { it.toIntOrNull() }
        if (parts.size < 2) { Log.w(TAG, "Invalid time: $time"); return }
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, parts[0])
            set(Calendar.MINUTE, parts[1])
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
        }
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, buildIntent(context, action, reqCode))
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, buildIntent(context, action, reqCode))
        }
    }

    private fun buildIntent(context: Context, action: String, reqCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, reqCode,
            Intent(action).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
