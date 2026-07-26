package com.core.mdm.policy

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Carries the real-time MDM enforcement status for a single installed package.
 */
data class AppStatus(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isSystem: Boolean,
    val isHidden: Boolean,
    val isSuspended: Boolean
) {
    /** Human-readable policy label shown in the UI. */
    val policyLabel: String get() = when {
        isHidden    -> "Hidden"
        isSuspended -> "Suspended"
        else        -> "Allowed"
    }
}

class AppPolicyManager(
    private val context: Context,
    private val helper: DevicePolicyHelper
) {
    companion object {
        private const val TAG = "AppPolicyManager"
    }

    // ── Suspension ────────────────────────────────────────────────────────────

    /**
     * Suspends [packages]. Suspended apps are visible to the user but cannot
     * be launched — they show a system dialog explaining the suspension.
     * Returns the subset that could NOT be suspended (empty = all succeeded).
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun suspendPackages(packages: Array<String>): Array<String> {
        if (!helper.isDeviceOwner) {
            Log.w(TAG, "suspendPackages: not device owner")
            return packages
        }
        return runCatching {
            helper.dpm.setPackagesSuspended(helper.admin, packages, true) ?: emptyArray()
        }.getOrElse {
            Log.e(TAG, "suspendPackages: ${it.message}")
            packages
        }.also {
            if (it.isEmpty()) Log.i(TAG, "Suspended: ${packages.joinToString()}")
            else Log.w(TAG, "Failed to suspend: ${it.joinToString()}")
        }
    }

    /**
     * Removes suspension from [packages].
     * Returns the subset that could NOT be unsuspended.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun unsuspendPackages(packages: Array<String>): Array<String> {
        if (!helper.isDeviceOwner) return packages
        return runCatching {
            helper.dpm.setPackagesSuspended(helper.admin, packages, false) ?: emptyArray()
        }.getOrElse {
            Log.e(TAG, "unsuspendPackages: ${it.message}")
            packages
        }
    }

    /** Returns true if [packageName] is currently suspended by this admin. */
    @RequiresApi(Build.VERSION_CODES.N)
    fun isPackageSuspended(packageName: String): Boolean {
        if (!helper.isDeviceOwner) return false
        return runCatching {
            helper.dpm.isPackageSuspended(helper.admin, packageName)
        }.getOrDefault(false)
    }

    // ── Hiding ────────────────────────────────────────────────────────────────

    /**
     * Hides [packageName] from the launcher and prevents it from running.
     * Unlike suspension, the app does not appear in the app drawer at all.
     * Returns true on success.
     */
    fun hidePackage(packageName: String): Boolean {
        if (!helper.isDeviceOwner) return false
        return runCatching {
            helper.dpm.setApplicationHidden(helper.admin, packageName, true)
        }.getOrElse {
            Log.e(TAG, "hidePackage($packageName): ${it.message}")
            false
        }.also { if (it) Log.i(TAG, "Hidden: $packageName") }
    }

    /**
     * Makes [packageName] visible again.
     * Returns true on success.
     */
    fun unhidePackage(packageName: String): Boolean {
        if (!helper.isDeviceOwner) return false
        return runCatching {
            helper.dpm.setApplicationHidden(helper.admin, packageName, false)
        }.getOrElse {
            Log.e(TAG, "unhidePackage($packageName): ${it.message}")
            false
        }.also { if (it) Log.i(TAG, "Unhidden: $packageName") }
    }

    /** Returns true if [packageName] is currently hidden by this admin. */
    fun isPackageHidden(packageName: String): Boolean {
        if (!helper.isDeviceOwner) return false
        return runCatching {
            helper.dpm.isApplicationHidden(helper.admin, packageName)
        }.getOrDefault(false)
    }

    // ── Toggle convenience ────────────────────────────────────────────────────

    /** Toggles hidden state. Returns the NEW hidden state. */
    fun toggleHidden(packageName: String): Boolean =
        if (isPackageHidden(packageName)) { unhidePackage(packageName); false }
        else { hidePackage(packageName); true }

    /** Toggles suspended state. Returns the NEW suspended state. Requires API 24+. */
    @RequiresApi(Build.VERSION_CODES.N)
    fun toggleSuspended(packageName: String): Boolean =
        if (isPackageSuspended(packageName)) {
            unsuspendPackages(arrayOf(packageName)); false
        } else {
            suspendPackages(arrayOf(packageName)); true
        }

    // ── Package query ─────────────────────────────────────────────────────────

    /**
     * Returns all installed applications with their current MDM policy status.
     *
     * @param includeSystem  When false (default), core system apps are excluded.
     * @param sortBlocked    When true, blocked/hidden apps appear at the top.
     */
    fun getInstalledApps(
        includeSystem: Boolean = false,
        sortBlocked: Boolean = true
    ): List<AppStatus> {
        val pm = context.packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { ai ->
                includeSystem || (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0
            }
            .map { ai ->
                val hidden = isPackageHidden(ai.packageName)
                val suspended = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    isPackageSuspended(ai.packageName)
                } else false

                AppStatus(
                    packageName = ai.packageName,
                    label       = runCatching { pm.getApplicationLabel(ai).toString() }
                                    .getOrDefault(ai.packageName),
                    icon        = runCatching { pm.getApplicationIcon(ai) }.getOrNull(),
                    isSystem    = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    isHidden    = hidden,
                    isSuspended = suspended
                )
            }
            .let { list ->
                if (sortBlocked)
                    list.sortedWith(compareBy({ it.policyLabel == "Allowed" }, { it.label.lowercase() }))
                else
                    list.sortedBy { it.label.lowercase() }
            }
    }

    /** Returns all packages with an active enforcement action (hidden OR suspended). */
    fun getEnforcedPackages(): List<AppStatus> =
        getInstalledApps(includeSystem = true).filter { it.isHidden || it.isSuspended }

    /** Returns the [AppStatus] for a single package, or null if not found. */
    fun getAppStatus(packageName: String): AppStatus? {
        val pm = context.packageManager
        return runCatching {
            val ai = pm.getApplicationInfo(packageName, 0)
            val hidden = isPackageHidden(packageName)
            val suspended = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                isPackageSuspended(packageName)
            } else false
            AppStatus(
                packageName = packageName,
                label       = pm.getApplicationLabel(ai).toString(),
                icon        = runCatching { pm.getApplicationIcon(ai) }.getOrNull(),
                isSystem    = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                isHidden    = hidden,
                isSuspended = suspended
            )
        }.getOrNull()
    }
}
