package com.core.mdm.policy

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Calendar

object ScreenTimeScheduler {

    const val ACTION_LOCK   = "com.core.mdm.screentime.LOCK"
    const val ACTION_UNLOCK = "com.core.mdm.screentime.UNLOCK"

    private const val TAG       = "ScreenTimeScheduler"
    private const val PREFS     = "screen_time_prefs"
    private const val KEY_ON    = "enabled"
    private const val KEY_START = "start"
    private const val KEY_END   = "end"

    // ── Persistence ───────────────────────────────────────────────────────────

    fun save(ctx: Context, enabled: Boolean, start: String, end: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ON,    enabled)
            .putString(KEY_START,  start)
            .putString(KEY_END,    end)
            .apply()
    }

    /** Returns (enabled, startTime "HH:MM", endTime "HH:MM"). */
    fun load(ctx: Context): Triple<Boolean, String, String> {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Triple(
            p.getBoolean(KEY_ON, false),
            p.getString(KEY_START, "07:00") ?: "07:00",
            p.getString(KEY_END,   "21:00") ?: "21:00"
        )
    }

    // ── Alarm management ──────────────────────────────────────────────────────

    fun schedule(ctx: Context, start: String, end: String) {
        cancel(ctx)
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        scheduleDailyAt(ctx, am, ACTION_LOCK,   parseHhMm(end))
        scheduleDailyAt(ctx, am, ACTION_UNLOCK, parseHhMm(start))
        Log.i(TAG, "Scheduled: active $start – $end")
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pi(ctx, ACTION_LOCK))
        am.cancel(pi(ctx, ACTION_UNLOCK))
        Log.i(TAG, "Alarms cancelled")
    }

    private fun scheduleDailyAt(ctx: Context, am: AlarmManager, action: String, cal: Calendar) {
        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        am.setRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi(ctx, action))
    }

    fun pi(ctx: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, action.hashCode(),
            Intent(ctx, ScreenTimeReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun parseHhMm(hhmm: String): Calendar {
        val parts = hhmm.split(":")
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, parts.getOrNull(0)?.toIntOrNull() ?: 0)
            set(Calendar.MINUTE,      parts.getOrNull(1)?.toIntOrNull() ?: 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
    }

    /** Format hour/minute ints to "HH:MM". */
    fun format(hour: Int, minute: Int): String =
        "%02d:%02d".format(hour, minute)

    /** Parse "HH:MM" → Pair(hour, minute). */
    fun parse(hhmm: String): Pair<Int, Int> {
        val parts = hhmm.split(":")
        return Pair(parts.getOrNull(0)?.toIntOrNull() ?: 0, parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }
}
