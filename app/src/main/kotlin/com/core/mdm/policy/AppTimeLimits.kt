package com.core.mdm.policy

import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import java.util.Calendar

/**
 * Daily time allowances per app ("Instagram: 30 minutes a day"). Usage comes from Android's own
 * usage statistics, so it counts time in the app however it was opened.
 *
 * When an app passes its allowance it is suspended for the rest of the day and released again
 * after midnight. Suspending (rather than closing repeatedly) means the phone shows Android's
 * own "app paused" dialog instead of bouncing the person out of a screen over and over.
 *
 * Needs the usage-access permission, which — like the screen guard — the admin grants once in
 * Settings; [hasUsageAccess] reports whether it's been given.
 */
object AppTimeLimits {
    private const val TAG = "AppTimeLimits"
    private const val PREFS = "mdm_time_limits"
    private const val KEY_PAUSED = "paused_today"
    private const val KEY_DAY = "day"

    /** Policy key: `{"com.instagram.android": 30}` — minutes allowed per day. */
    const val KEY_LIMITS = "appTimeLimits"

    data class Usage(val pkg: String, val usedMinutes: Int, val limitMinutes: Int) {
        val isOver: Boolean get() = usedMinutes >= limitMinutes
        val remainingMinutes: Int get() = (limitMinutes - usedMinutes).coerceAtLeast(0)
    }

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = runCatching {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), context.packageName)
        }.getOrDefault(android.app.AppOpsManager.MODE_ERRORED)
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings(context: Context) {
        runCatching {
            context.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { Log.w(TAG, "Couldn't open usage access settings: ${it.message}") }
    }

    /** Minutes each limited app has been used since midnight. */
    fun todayUsage(context: Context, limits: Map<String, Int>): List<Usage> {
        if (limits.isEmpty() || !hasUsageAccess(context)) return emptyList()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val start = startOfToday()
        val stats = runCatching {
            usm.queryAndAggregateUsageStats(start, System.currentTimeMillis())
        }.getOrElse { emptyMap() }
        return limits.map { (pkg, limit) ->
            val ms = stats[pkg]?.totalTimeInForeground ?: 0L
            Usage(pkg, (ms / 60_000L).toInt(), limit)
        }
    }

    /**
     * Suspends apps that have used up today's allowance and releases the ones that haven't.
     * Called every minute by the command service.
     */
    fun enforce(context: Context, policies: Map<String, Any>) {
        val limits = readLimits(policies)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // New day: release everything that was paused yesterday
        val today = dayStamp()
        if (prefs.getString(KEY_DAY, null) != today) {
            val stale = prefs.getStringSet(KEY_PAUSED, emptySet()).orEmpty()
            if (stale.isNotEmpty()) suspend(context, stale, false)
            prefs.edit().putString(KEY_DAY, today).putStringSet(KEY_PAUSED, emptySet()).apply()
        }
        if (limits.isEmpty() || !hasUsageAccess(context)) return

        val over = todayUsage(context, limits).filter { it.isOver }.map { it.pkg }.toSet()
        val wasPaused = prefs.getStringSet(KEY_PAUSED, emptySet()).orEmpty()
        // Don't release an app the admin blocked outright — that block is separate from the timer
        val blockedElsewhere = AppBlocker.blockedPackages(context)
        val toRelease = (wasPaused - over).filter { it !in blockedElsewhere }.toSet()
        val toPause = over - wasPaused

        if (toRelease.isNotEmpty()) suspend(context, toRelease, false)
        if (toPause.isNotEmpty()) {
            suspend(context, toPause, true)
            Log.i(TAG, "Daily limit reached: ${toPause.joinToString()}")
        }
        prefs.edit().putStringSet(KEY_PAUSED, over).apply()
    }

    fun pausedToday(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_PAUSED, emptySet()).orEmpty()

    @Suppress("UNCHECKED_CAST")
    fun readLimits(policies: Map<String, Any>): Map<String, Int> =
        (policies[KEY_LIMITS] as? Map<*, *>)?.mapNotNull { (k, v) ->
            val pkg = k as? String ?: return@mapNotNull null
            val mins = (v as? Number)?.toInt() ?: return@mapNotNull null
            if (mins > 0) pkg to mins else null
        }?.toMap().orEmpty()

    private fun suspend(context: Context, packages: Set<String>, suspended: Boolean) {
        val helper = DevicePolicyHelper.getInstance(context)
        if (!helper.isDeviceOwner) return
        runCatching {
            helper.dpm.setPackagesSuspended(helper.admin, packages.toTypedArray(), suspended)
        }.onFailure { Log.w(TAG, "setPackagesSuspended: ${it.message}") }
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun dayStamp(): String = Calendar.getInstance().let {
        "${it.get(Calendar.YEAR)}-${it.get(Calendar.DAY_OF_YEAR)}"
    }
}
