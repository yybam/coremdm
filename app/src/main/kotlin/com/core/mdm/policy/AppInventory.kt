package com.core.mdm.policy

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

/**
 * The app list shown on the website's "Apps" tab: every launchable app, with whether it's a
 * system app and whether CoreMDM currently blocks or hides it. Kept small (no icons) because
 * it's stored on the device's Firestore document.
 */
object AppInventory {

    fun collect(context: Context): List<Map<String, Any>> {
        val pm = context.packageManager
        val helper = DevicePolicyHelper.getInstance(context)
        val blocked = AppBlocker.blockedPackages(context)
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) PackageManager.MATCH_UNINSTALLED_PACKAGES else 0
        return pm.queryIntentActivities(launcher, flags)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { info ->
                mapOf(
                    "pkg"     to info.packageName,
                    "label"   to info.loadLabel(pm).toString(),
                    "system"  to ((info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                                  (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0),
                    "blocked" to (info.packageName in blocked),
                    "hidden"  to helper.isAppHidden(info.packageName),
                )
            }
            .sortedBy { (it["label"] as String).lowercase() }
    }
}
