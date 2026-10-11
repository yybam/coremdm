package com.core.mdm.service

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log

object UsageStatsCollector {
    private const val TAG = "UsageStatsCollector"

    fun hasPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun collect(context: Context): List<Map<String, Any>> {
        if (!hasPermission(context)) {
            Log.d(TAG, "PACKAGE_USAGE_STATS not granted — skipping")
            return emptyList()
        }
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now   = System.currentTimeMillis()
        val since = now - 24 * 60 * 60 * 1000L
        val stats = runCatching {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, since, now)
        }.getOrElse { e ->
            Log.e(TAG, "queryUsageStats: ${e.message}")
            return emptyList()
        }
        val pm = context.packageManager
        return stats
            .filter { it.totalTimeInForeground > 0 }
            .sortedByDescending { it.totalTimeInForeground }
            .take(20)
            .map { stat ->
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(stat.packageName, 0)).toString()
                }.getOrDefault(stat.packageName)
                mapOf(
                    "pkg"      to stat.packageName,
                    "label"    to label,
                    "usageMs"  to stat.totalTimeInForeground,
                    "lastUsed" to stat.lastTimeUsed,
                )
            }
    }
}
