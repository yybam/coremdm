package com.core.mdm.policy

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.core.mdm.MdmDeviceAdmin

class DevicePolicyHelper private constructor(private val context: Context) {

    companion object {
        private const val TAG = "DevicePolicyHelper"

        @Volatile private var INSTANCE: DevicePolicyHelper? = null

        val BROWSER_PACKAGES = listOf(
            "com.android.chrome",
            "com.sec.android.app.sbrowser",  // Samsung Internet
            "org.mozilla.firefox",
            "com.opera.browser",
            "com.brave.browser",
            "com.microsoft.emmx",            // Edge
            "com.duckduckgo.mobile.android",
            "com.UCMobile.intl",             // UC Browser
            "com.kiwibrowser.browser",
        )

        fun getInstance(context: Context): DevicePolicyHelper =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: DevicePolicyHelper(context.applicationContext).also { INSTANCE = it }
            }
    }

    val dpm: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    // Cached — avoids repeated getSystemService() calls (each is a binder lookup)
    private val userManager: UserManager =
        context.getSystemService(Context.USER_SERVICE) as UserManager

    val admin: ComponentName = ComponentName(context, MdmDeviceAdmin::class.java)

    val isAdminActive: Boolean get() = dpm.isAdminActive(admin)
    val isDeviceOwner: Boolean get() = dpm.isDeviceOwnerApp(context.packageName)

    val deviceModel: String get() = "${Build.MANUFACTURER} ${Build.MODEL}"
    val androidVersion: String get() = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    // ── User restrictions ─────────────────────────────────────────────────────

    fun restrict(key: String): Boolean {
        if (!isAdminActive) return false
        return runCatching { dpm.addUserRestriction(admin, key) }
            .onFailure { Log.w(TAG, "restrict($key): ${it.message}") }
            .isSuccess
    }

    fun unrestrict(key: String): Boolean {
        if (!isAdminActive) return false
        return runCatching { dpm.clearUserRestriction(admin, key) }
            .onFailure { Log.w(TAG, "unrestrict($key): ${it.message}") }
            .isSuccess
    }

    fun setRestriction(key: String, enabled: Boolean): Boolean =
        if (enabled) restrict(key) else unrestrict(key)

    fun hasRestriction(key: String): Boolean = userManager.hasUserRestriction(key)

    /** Returns all active user restrictions in a single IPC call. */
    fun getRestrictionBundle(): android.os.Bundle = userManager.getUserRestrictions()

    // ── Camera / Screen ───────────────────────────────────────────────────────

    fun setCameraDisabled(disabled: Boolean) {
        if (!isAdminActive) return
        runCatching { dpm.setCameraDisabled(admin, disabled) }
            .onFailure { Log.e(TAG, "setCameraDisabled: ${it.message}") }
    }

    fun isCameraDisabled(): Boolean =
        if (isAdminActive) runCatching { dpm.getCameraDisabled(admin) }.getOrDefault(false)
        else false

    fun setScreenCaptureDisabled(disabled: Boolean) {
        if (!isAdminActive) return
        runCatching { dpm.setScreenCaptureDisabled(admin, disabled) }
            .onFailure { Log.e(TAG, "setScreenCaptureDisabled: ${it.message}") }
    }

    fun isScreenCaptureDisabled(): Boolean =
        if (isAdminActive) runCatching { dpm.getScreenCaptureDisabled(admin) }.getOrDefault(false)
        else false

    // ── Status bar ────────────────────────────────────────────────────────────

    private val uiStatePrefs = context.getSharedPreferences("mdm_ui_state", Context.MODE_PRIVATE)

    fun setStatusBarDisabled(disabled: Boolean): Boolean {
        if (!isDeviceOwner || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        return runCatching {
            dpm.setStatusBarDisabled(admin, disabled)
            uiStatePrefs.edit().putBoolean("status_bar_disabled", disabled).apply()
            true
        }.getOrDefault(false)
    }

    fun isStatusBarDisabled(): Boolean = uiStatePrefs.getBoolean("status_bar_disabled", false)

    // ── Keyguard ──────────────────────────────────────────────────────────────

    fun setKeyguardDisabled(disabled: Boolean): Boolean {
        if (!isDeviceOwner || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        return runCatching { dpm.setKeyguardDisabled(admin, disabled) }.getOrDefault(false)
    }

    // ── Password ──────────────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    fun setPasswordMinimumLength(length: Int) {
        if (!isAdminActive) return
        runCatching { dpm.setPasswordMinimumLength(admin, length) }
            .onFailure { Log.e(TAG, "setPasswordMinimumLength: ${it.message}") }
    }

    fun setMaximumFailedPasswordsForWipe(count: Int) {
        if (!isAdminActive) return
        runCatching { dpm.setMaximumFailedPasswordsForWipe(admin, count) }
            .onFailure { Log.e(TAG, "setMaxFailedPasswords: ${it.message}") }
    }

    // ── Device actions ────────────────────────────────────────────────────────

    fun lockNow() { if (isAdminActive) runCatching { dpm.lockNow() } }

    fun wipeDevice(includeExternal: Boolean = false) {
        if (!isAdminActive) return
        val flags = if (includeExternal) DevicePolicyManager.WIPE_EXTERNAL_STORAGE else 0
        runCatching { dpm.wipeData(flags) }
    }

    fun reboot() {
        if (!isDeviceOwner || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        runCatching { dpm.reboot(admin) }
    }

    // ── Lock task (kiosk) ─────────────────────────────────────────────────────

    fun setLockTaskPackages(packages: Array<String>) {
        if (!isDeviceOwner) return
        runCatching { dpm.setLockTaskPackages(admin, packages) }
            .onFailure { Log.e(TAG, "setLockTaskPackages: ${it.message}") }
    }

    // ── Private DNS (via global settings, works on API 28+) ───────────────────

    fun setPrivateDnsHostname(host: String): Boolean {
        if (!isDeviceOwner) return false
        return runCatching {
            dpm.setGlobalSetting(admin, "private_dns_mode", "hostname")
            dpm.setGlobalSetting(admin, "private_dns_specifier", host)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                restrict(UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
            }
            true
        }.getOrElse {
            Log.e(TAG, "setPrivateDnsHostname: ${it.message}")
            false
        }
    }

    fun clearPrivateDns() {
        if (!isDeviceOwner) return
        runCatching {
            dpm.setGlobalSetting(admin, "private_dns_mode", "opportunistic")
            dpm.setGlobalSetting(admin, "private_dns_specifier", "")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                unrestrict(UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
            }
        }
    }

    // ── Global settings ───────────────────────────────────────────────────────

    fun setGlobalSetting(key: String, value: String) {
        if (!isDeviceOwner) return
        runCatching { dpm.setGlobalSetting(admin, key, value) }
    }

    fun applyBaselineRestrictions() {
        restrict(UserManager.DISALLOW_SAFE_BOOT)
        restrict(UserManager.DISALLOW_FACTORY_RESET)
        restrict(UserManager.DISALLOW_ADD_USER)
        Log.i(TAG, "Baseline restrictions applied")
    }

    // ── Extended hardware/system restrictions ─────────────────────────────────

    /** Requires API 28+; silently no-ops below. */
    fun setUsbFileTransferBlocked(blocked: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return setRestriction(android.os.UserManager.DISALLOW_USB_FILE_TRANSFER, blocked)
    }

    /** Completely disables Bluetooth (not just config). API 24+. */
    fun setBluetoothDisabled(disabled: Boolean): Boolean =
        setRestriction(android.os.UserManager.DISALLOW_BLUETOOTH, disabled)

    /** Prevents all outgoing phone calls. API 24+. */
    fun setOutgoingCallsBlocked(blocked: Boolean): Boolean =
        setRestriction(android.os.UserManager.DISALLOW_OUTGOING_CALLS, blocked)

    /** Prevents mounting physical media (SD card). API 24+. */
    fun setPhysicalMediaBlocked(blocked: Boolean): Boolean =
        setRestriction(android.os.UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA, blocked)

    // ── Uninstall protection ──────────────────────────────────────────────────

    fun setUninstallBlocked(pkg: String, blocked: Boolean): Boolean {
        if (!isDeviceOwner) return false
        return runCatching { dpm.setUninstallBlocked(admin, pkg, blocked); true }
            .onFailure { Log.w(TAG, "setUninstallBlocked($pkg): ${it.message}") }
            .getOrDefault(false)
    }

    fun isUninstallBlocked(pkg: String): Boolean {
        if (!isDeviceOwner) return false
        return runCatching { dpm.isUninstallBlocked(admin, pkg) }.getOrDefault(false)
    }

    fun setSelfUninstallBlocked(blocked: Boolean): Boolean = setUninstallBlocked(context.packageName, blocked)
    fun isSelfUninstallBlocked(): Boolean = isUninstallBlocked(context.packageName)

    // ── Kiosk / lock-task ─────────────────────────────────────────────────────

    fun getLockTaskPackages(): List<String> {
        if (!isDeviceOwner) return emptyList()
        return runCatching { dpm.getLockTaskPackages(admin).toList() }.getOrDefault(emptyList())
    }

    // ── App visibility (Device Owner only) ────────────────────────────────────

    fun setAppHidden(pkg: String, hidden: Boolean): Boolean {
        if (!isDeviceOwner) return false
        // setApplicationHidden() returns false if state unchanged or pkg not found — not an error.
        // We succeed if no exception thrown and we are Device Owner.
        runCatching { dpm.setApplicationHidden(admin, pkg, hidden) }
            .onFailure { Log.w(TAG, "setAppHidden($pkg): ${it.message}"); return false }
        return true
    }

    fun isAppHidden(pkg: String): Boolean {
        if (!isDeviceOwner) return false
        return runCatching { dpm.isApplicationHidden(admin, pkg) }.getOrDefault(false)
    }

    fun setPlayStoreHidden(hidden: Boolean): Boolean = setAppHidden("com.android.vending", hidden)
    fun isPlayStoreHidden(): Boolean = isAppHidden("com.android.vending")

    fun setBrowsersHidden(hidden: Boolean): Boolean {
        if (!isDeviceOwner) return false
        BROWSER_PACKAGES.forEach { pkg ->
            runCatching { dpm.setApplicationHidden(admin, pkg, hidden) }
                .onFailure { Log.w(TAG, "setBrowsersHidden($pkg): ${it.message}") }
        }
        return true
    }

    fun areBrowsersHidden(): Boolean = BROWSER_PACKAGES.any { isAppHidden(it) }

    /** Strips Device Owner and Device Admin status so the app can be uninstalled. */
    fun clearAllAdminPrivileges() {
        runCatching { dpm.clearDeviceOwnerApp(context.packageName) }
        runCatching { dpm.removeActiveAdmin(admin) }
        Log.i(TAG, "Admin privileges cleared")
    }
}
