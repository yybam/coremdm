package com.core.mdm.data

import android.content.Context
import android.os.UserManager
import com.core.mdm.policy.AppPolicyManager
import com.core.mdm.policy.AppStatus
import com.core.mdm.policy.DevicePolicyHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Single source of truth for all policy reads and writes.
 * All DPM calls are dispatched on [Dispatchers.IO] to avoid blocking the main thread.
 */
class PolicyRepository private constructor(context: Context) {

    companion object {
        @Volatile private var INSTANCE: PolicyRepository? = null

        fun getInstance(context: Context): PolicyRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PolicyRepository(context.applicationContext).also { INSTANCE = it }
            }
    }

    private val helper = DevicePolicyHelper.getInstance(context)
    private val appManager = AppPolicyManager(context, helper)

    // ── Status reads (fast, can be called on Main) ────────────────────────────

    val isAdminActive: Boolean get() = helper.isAdminActive
    val isDeviceOwner: Boolean get() = helper.isDeviceOwner
    val deviceModel: String get() = helper.deviceModel
    val androidVersion: String get() = helper.androidVersion

    fun hasRestriction(key: String): Boolean = helper.hasRestriction(key)
    fun isCameraDisabled(): Boolean = helper.isCameraDisabled()
    fun isScreenCaptureDisabled(): Boolean = helper.isScreenCaptureDisabled()

    // ── Policy writes (dispatch to IO) ────────────────────────────────────────

    suspend fun setRestriction(key: String, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        helper.setRestriction(key, enabled)
    }

    suspend fun setCameraDisabled(disabled: Boolean) = withContext(Dispatchers.IO) {
        helper.setCameraDisabled(disabled)
    }

    suspend fun setScreenCaptureDisabled(disabled: Boolean) = withContext(Dispatchers.IO) {
        helper.setScreenCaptureDisabled(disabled)
    }

    suspend fun setStatusBarDisabled(disabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        helper.setStatusBarDisabled(disabled)
    }

    suspend fun setPrivateDns(host: String): Boolean = withContext(Dispatchers.IO) {
        helper.setPrivateDnsHostname(host)
    }

    suspend fun clearPrivateDns() = withContext(Dispatchers.IO) {
        helper.clearPrivateDns()
    }

    suspend fun lockNow() = withContext(Dispatchers.IO) {
        helper.lockNow()
    }

    suspend fun reboot() = withContext(Dispatchers.IO) {
        helper.reboot()
    }

    suspend fun wipeDevice() = withContext(Dispatchers.IO) {
        helper.wipeDevice()
    }

    // ── App queries (heavy, always dispatch to IO) ────────────────────────────

    suspend fun getInstalledApps(
        includeSystem: Boolean = false
    ): List<AppStatus> = withContext(Dispatchers.IO) {
        appManager.getInstalledApps(includeSystem = includeSystem)
    }

    suspend fun hideApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        appManager.hidePackage(packageName)
    }

    suspend fun unhideApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        appManager.unhidePackage(packageName)
    }

    suspend fun suspendApp(packageName: String) = withContext(Dispatchers.IO) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            appManager.suspendPackages(arrayOf(packageName))
        }
    }

    suspend fun unsuspendApp(packageName: String) = withContext(Dispatchers.IO) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            appManager.unsuspendPackages(arrayOf(packageName))
        }
    }

    // ── Snapshots (read all restrictions at once for UI sync) ─────────────────

    data class RestrictionSnapshot(
        // Anti-bypass
        val safeBootBlocked: Boolean,
        val factoryResetBlocked: Boolean,
        val debuggingBlocked: Boolean,
        val addUserBlocked: Boolean,
        // App restrictions
        val installAppsBlocked: Boolean,
        val uninstallAppsBlocked: Boolean,
        val unknownSourcesBlocked: Boolean,
        // Hardware
        val cameraDisabled: Boolean,
        val screenCaptureDisabled: Boolean,
        // Settings
        val wifiConfigBlocked: Boolean,
        val mobileNetworksBlocked: Boolean,
        val bluetoothConfigBlocked: Boolean,
        val vpnBlocked: Boolean,
        val networkResetBlocked: Boolean,
        // Network
        val privateDnsRestricted: Boolean,
        // User
        val userSwitchBlocked: Boolean,
        // App visibility (Device Owner only)
        val playStoreHidden: Boolean,
        val browsersHidden: Boolean,
        // UI state (persisted in prefs, no DPM getter)
        val statusBarDisabled: Boolean,
        // Extended hardware restrictions
        val usbTransferBlocked: Boolean,
        val bluetoothDisabled: Boolean,
        val outgoingCallsBlocked: Boolean,
        val physicalMediaBlocked: Boolean,
        // MDM self-protection
        val mdmUninstallProtected: Boolean,
    )

    suspend fun loadRestrictions(): RestrictionSnapshot = withContext(Dispatchers.IO) {
        coroutineScope {
            val bundle                = helper.getRestrictionBundle()
            val cameraDeferred        = async { helper.isCameraDisabled() }
            val screenCaptureDeferred = async { helper.isScreenCaptureDisabled() }
            val playStoreDeferred     = async { helper.isPlayStoreHidden() }
            val browsersDeferred      = async { helper.areBrowsersHidden() }
            val statusBarDeferred     = async { helper.isStatusBarDisabled() }
            val mdmUninstallDeferred  = async { helper.isSelfUninstallBlocked() }

            RestrictionSnapshot(
                safeBootBlocked        = bundle.getBoolean(UserManager.DISALLOW_SAFE_BOOT),
                factoryResetBlocked    = bundle.getBoolean(UserManager.DISALLOW_FACTORY_RESET),
                debuggingBlocked       = bundle.getBoolean(UserManager.DISALLOW_DEBUGGING_FEATURES),
                addUserBlocked         = bundle.getBoolean(UserManager.DISALLOW_ADD_USER),
                installAppsBlocked     = bundle.getBoolean(UserManager.DISALLOW_INSTALL_APPS),
                uninstallAppsBlocked   = bundle.getBoolean(UserManager.DISALLOW_UNINSTALL_APPS),
                unknownSourcesBlocked  = bundle.getBoolean(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES),
                cameraDisabled         = cameraDeferred.await(),
                screenCaptureDisabled  = screenCaptureDeferred.await(),
                wifiConfigBlocked      = bundle.getBoolean(UserManager.DISALLOW_CONFIG_WIFI),
                mobileNetworksBlocked  = bundle.getBoolean(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS),
                bluetoothConfigBlocked = bundle.getBoolean(UserManager.DISALLOW_CONFIG_BLUETOOTH),
                vpnBlocked             = bundle.getBoolean(UserManager.DISALLOW_CONFIG_VPN),
                networkResetBlocked    = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
                                            bundle.getBoolean(UserManager.DISALLOW_NETWORK_RESET) else false,
                privateDnsRestricted   = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
                                            bundle.getBoolean(UserManager.DISALLOW_CONFIG_PRIVATE_DNS) else false,
                userSwitchBlocked      = bundle.getBoolean(UserManager.DISALLOW_USER_SWITCH),
                playStoreHidden        = playStoreDeferred.await(),
                browsersHidden         = browsersDeferred.await(),
                statusBarDisabled      = statusBarDeferred.await(),
                usbTransferBlocked     = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
                                             bundle.getBoolean(android.os.UserManager.DISALLOW_USB_FILE_TRANSFER) else false,
                bluetoothDisabled      = bundle.getBoolean(android.os.UserManager.DISALLOW_BLUETOOTH),
                outgoingCallsBlocked   = bundle.getBoolean(android.os.UserManager.DISALLOW_OUTGOING_CALLS),
                physicalMediaBlocked   = bundle.getBoolean(android.os.UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA),
                mdmUninstallProtected  = mdmUninstallDeferred.await(),
            )
        }
    }

    suspend fun setPlayStoreHidden(hidden: Boolean): Boolean = withContext(Dispatchers.IO) {
        helper.setPlayStoreHidden(hidden)
    }

    suspend fun setBrowsersHidden(hidden: Boolean): Boolean = withContext(Dispatchers.IO) {
        helper.setBrowsersHidden(hidden)
    }

    suspend fun setUsbTransferBlocked(blocked: Boolean): Boolean = withContext(Dispatchers.IO) {
        helper.setUsbFileTransferBlocked(blocked)
    }

    suspend fun setBluetoothDisabled(disabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        helper.setBluetoothDisabled(disabled)
    }

    suspend fun setOutgoingCallsBlocked(blocked: Boolean): Boolean = withContext(Dispatchers.IO) {
        helper.setOutgoingCallsBlocked(blocked)
    }

    suspend fun setPhysicalMediaBlocked(blocked: Boolean): Boolean = withContext(Dispatchers.IO) {
        helper.setPhysicalMediaBlocked(blocked)
    }

    suspend fun setMdmUninstallProtected(protected: Boolean): Boolean = withContext(Dispatchers.IO) {
        helper.setSelfUninstallBlocked(protected)
    }
}
