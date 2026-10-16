package com.core.mdm.policy

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.core.mdm.installer.SilentInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * "Only chosen apps can install apps": once switched on, any app that arrives through an
 * installer that isn't on the list (another app store, a file manager, a browser download…)
 * is hidden straight away and then uninstalled. CoreMDM itself (and its app updater) is always
 * allowed. Updates to apps that were already on the phone are left alone.
 *
 * Settings are cached in prefs so the check works the moment a package arrives, before
 * Firestore has synced.
 */
object InstallGuard {
    private const val TAG = "InstallGuard"
    private const val PREFS = "mdm_install_guard"
    private const val KEY_PREF_ON = "on"
    private const val KEY_PREF_SINCE = "since"
    private const val KEY_PREF_ALLOWED = "allowed"

    /** Policy keys, shared with the website (policies.js). */
    const val KEY_ON = "installerAllowlistOn"
    const val KEY_ALLOWED = "allowedInstallers"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Applies the `policies` map from Firestore (or a change made in the app). */
    fun apply(context: Context, policies: Map<String, Any>) {
        val on = policies[KEY_ON] as? Boolean ?: false
        val allowed = (policies[KEY_ALLOWED] as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty()
        val p = prefs(context)
        val wasOn = p.getBoolean(KEY_PREF_ON, false)
        p.edit()
            .putBoolean(KEY_PREF_ON, on)
            .putStringSet(KEY_PREF_ALLOWED, allowed)
            // Only apps that arrive after the switch goes on are judged — nothing already on the
            // phone gets removed by turning this on.
            .apply { if (on && !wasOn) putLong(KEY_PREF_SINCE, System.currentTimeMillis()) }
            .apply()
        if (on) scope.launch { sweep(context) }
    }

    fun isOn(context: Context) = prefs(context).getBoolean(KEY_PREF_ON, false)

    /** Removes every app installed since the switch went on by an installer not on the list. */
    suspend fun sweep(context: Context) {
        val p = prefs(context)
        if (!p.getBoolean(KEY_PREF_ON, false)) return
        val helper = DevicePolicyHelper.getInstance(context)
        if (!helper.isDeviceOwner) return
        val since = p.getLong(KEY_PREF_SINCE, System.currentTimeMillis())
        val allowed = p.getStringSet(KEY_PREF_ALLOWED, emptySet()).orEmpty() + context.packageName
        val pm = context.packageManager

        @Suppress("DEPRECATION")
        val packages = runCatching { pm.getInstalledPackages(0) }.getOrDefault(emptyList())
        for (info in packages) {
            val pkg = info.packageName
            val app = info.applicationInfo ?: continue
            if (pkg == context.packageName) continue
            if (app.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue
            if (info.firstInstallTime < since) continue
            val installer = installerOf(pm, pkg)
            if (installer != null && installer in allowed) continue
            Log.i(TAG, "Removing $pkg — installed by ${installer ?: "unknown"}, which isn't allowed")
            helper.setAppHidden(pkg, true)
            SilentInstaller(context).uninstallPackage(pkg)
        }
    }

    /**
     * The app that put [pkg] on the phone. On Android 11+ that's the app that started the
     * install (a file manager, browser, store…) rather than the system's installer screen.
     */
    fun installerOf(pm: PackageManager, pkg: String): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val src = pm.getInstallSourceInfo(pkg)
            src.initiatingPackageName ?: src.installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(pkg)
        }
    }.getOrNull()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
