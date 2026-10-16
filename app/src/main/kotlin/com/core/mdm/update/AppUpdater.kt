package com.core.mdm.update

import android.content.Context
import android.util.Log
import com.core.mdm.installer.SelfUpdateService
import com.core.mdm.policy.DevicePolicyHelper

/**
 * CORE MDM checks its own GitHub releases for a newer build. A newer release tag hands the APK
 * to [SelfUpdateService], which installs it silently (Device Owner) only after checking it is
 * this same app, a higher versionCode, and signed with the same key as the installed copy — so
 * a tampered or foreign release can never replace the MDM.
 */
object AppUpdater {
    private const val TAG  = "AppUpdater"
    // Releases published by .github/workflows/build-apk.yml on every vX.Y.Z tag.
    const val REPO         = "yybam/coremdm"
    private const val PREFS = "mdm_updater"
    private const val KEY_LAST_CHECK = "last_check"
    private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

    sealed class Result {
        object UpToDate : Result()
        data class Installing(val version: String) : Result()
        data class Failed(val reason: String) : Result()
    }

    /** Periodic check from the service; honours the 6-hour interval unless [force]. */
    suspend fun checkAndInstall(context: Context, force: Boolean = false): Result {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(KEY_LAST_CHECK, 0) < CHECK_INTERVAL_MS) return Result.UpToDate
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()

        if (!DevicePolicyHelper.getInstance(context).isDeviceOwner)
            return Result.Failed("Silent updates need Device Owner")

        return try {
            val asset = GitHubReleases.latestApk(REPO, nameHint = "release") ?: return Result.UpToDate
            val latest = asset.tag.removePrefix("v")
            @Suppress("DEPRECATION")
            val installed = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
            if (!isNewer(latest, installed)) return Result.UpToDate
            Log.i(TAG, "Release $latest is newer than $installed — updating")
            SelfUpdateService.start(context, apkUrl = asset.url, version = latest)
            Result.Installing(latest)
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed: ${e.message}")
            Result.Failed(e.message ?: "Update check failed")
        }
    }

    /** Compares dotted versions numerically ("0.10.0" > "0.9.3"); non-numeric parts count as 0. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
