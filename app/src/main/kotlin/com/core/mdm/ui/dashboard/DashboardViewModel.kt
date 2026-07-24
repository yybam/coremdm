package com.core.mdm.ui.dashboard

import android.app.Application
import android.os.UserManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.core.mdm.UninstallFlag
import com.core.mdm.data.PolicyRepository
import com.core.mdm.policy.DevicePolicyHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,      // user-triggered refresh (icon spinning, content stays)
    val isAdminActive: Boolean = false,
    val isDeviceOwner: Boolean = false,
    val deviceModel: String = "",
    val androidVersion: String = "",
    // Anti-bypass
    val safeBootBlocked: Boolean = false,
    val factoryResetBlocked: Boolean = false,
    val debuggingBlocked: Boolean = false,
    val addUserBlocked: Boolean = false,
    val userSwitchBlocked: Boolean = false,
    // App restrictions
    val installAppsBlocked: Boolean = false,
    val uninstallAppsBlocked: Boolean = false,
    val unknownSourcesBlocked: Boolean = false,
    val playStoreHidden: Boolean = false,
    val browsersHidden: Boolean = false,
    val statusBarDisabled: Boolean = false,
    // Extended hardware
    val usbTransferBlocked: Boolean = false,
    val bluetoothDisabled: Boolean = false,
    val outgoingCallsBlocked: Boolean = false,
    val physicalMediaBlocked: Boolean = false,
    // MDM self-protection
    val mdmUninstallProtected: Boolean = false,
    // Hardware
    val cameraDisabled: Boolean = false,
    val screenCaptureDisabled: Boolean = false,
    // Settings / Hardware control
    val wifiConfigBlocked: Boolean = false,
    val mobileNetworksBlocked: Boolean = false,
    val bluetoothConfigBlocked: Boolean = false,
    val vpnBlocked: Boolean = false,
    val networkResetBlocked: Boolean = false,
    // Network / DNS
    val privateDnsRestricted: Boolean = false,
    val privateDnsHost: String = "",
    val snackbarMessage: String? = null,
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = PolicyRepository.getInstance(application)

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadState()
    }

    // ── State loading ─────────────────────────────────────────────────────────────

    /** Full-screen initial load (shows spinner, replaces content). */
    fun loadState() = loadStateInternal(fullScreenLoader = true)

    /**
     * User-triggered refresh: keeps content visible, spins the refresh icon.
     * No-ops if a refresh is already in flight.
     */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        loadStateInternal(fullScreenLoader = false)
    }

    private fun loadStateInternal(fullScreenLoader: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                if (fullScreenLoader) it.copy(isLoading = true)
                else it.copy(isRefreshing = true)
            }
            val r = repo.loadRestrictions()
            _uiState.update {
                it.copy(
                    isLoading              = false,
                    isRefreshing           = false,
                    isAdminActive          = repo.isAdminActive,
                    isDeviceOwner          = repo.isDeviceOwner,
                    deviceModel            = repo.deviceModel,
                    androidVersion         = repo.androidVersion,
                    safeBootBlocked        = r.safeBootBlocked,
                    factoryResetBlocked    = r.factoryResetBlocked,
                    debuggingBlocked       = r.debuggingBlocked,
                    addUserBlocked         = r.addUserBlocked,
                    userSwitchBlocked      = r.userSwitchBlocked,
                    installAppsBlocked     = r.installAppsBlocked,
                    uninstallAppsBlocked   = r.uninstallAppsBlocked,
                    unknownSourcesBlocked  = r.unknownSourcesBlocked,
                    playStoreHidden        = r.playStoreHidden,
                    browsersHidden         = r.browsersHidden,
                    statusBarDisabled      = r.statusBarDisabled,
                    usbTransferBlocked     = r.usbTransferBlocked,
                    bluetoothDisabled      = r.bluetoothDisabled,
                    outgoingCallsBlocked   = r.outgoingCallsBlocked,
                    physicalMediaBlocked   = r.physicalMediaBlocked,
                    mdmUninstallProtected  = r.mdmUninstallProtected,
                    cameraDisabled         = r.cameraDisabled,
                    screenCaptureDisabled  = r.screenCaptureDisabled,
                    wifiConfigBlocked      = r.wifiConfigBlocked,
                    mobileNetworksBlocked  = r.mobileNetworksBlocked,
                    bluetoothConfigBlocked = r.bluetoothConfigBlocked,
                    vpnBlocked             = r.vpnBlocked,
                    networkResetBlocked    = r.networkResetBlocked,
                    privateDnsRestricted   = r.privateDnsRestricted,
                )
            }
            if (!fullScreenLoader) toast("Policy state refreshed")
        }
    }

    // ── Anti-bypass ───────────────────────────────────────────────────────────

    fun setSafeBootBlocked(v: Boolean) = toggleRestriction(
        UserManager.DISALLOW_SAFE_BOOT, v,
        { copy(safeBootBlocked = v) }, { copy(safeBootBlocked = !v) })

    fun setFactoryResetBlocked(v: Boolean) = toggleRestriction(
        UserManager.DISALLOW_FACTORY_RESET, v,
        { copy(factoryResetBlocked = v) }, { copy(factoryResetBlocked = !v) })

    fun setDebuggingBlocked(v: Boolean) = toggleRestriction(
        UserManager.DISALLOW_DEBUGGING_FEATURES, v,
        { copy(debuggingBlocked = v) }, { copy(debuggingBlocked = !v) })

    fun setAddUserBlocked(v: Boolean) = toggleRestriction(
        UserManager.DISALLOW_ADD_USER, v,
        { copy(addUserBlocked = v) }, { copy(addUserBlocked = !v) })

    fun setUserSwitchBlocked(v: Boolean) = toggleRestriction(
        UserManager.DISALLOW_USER_SWITCH, v,
        { copy(userSwitchBlocked = v) }, { copy(userSwitchBlocked = !v) })

    // ── App restrictions ──────────────────────────────────────────────────────

    fun setInstallAppsBlocked(v: Boolean) = toggleRestriction(
        UserManager.DISALLOW_INSTALL_APPS, v,
        { copy(installAppsBlocked = v) }, { copy(installAppsBlocked = !v) })

    fun setUninstallAppsBlocked(v: Boolean) = toggleRestriction(
        UserManager.DISALLOW_UNINSTALL_APPS, v,
        { copy(uninstallAppsBlocked = v) }, { copy(uninstallAppsBlocked = !v) })

    fun setUnknownSourcesBlocked(v: Boolean) = toggleRestriction(
        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES, v,
        { copy(unknownSourcesBlocked = v) }, { copy(unknownSourcesBlocked = !v) })

    fun setPlayStoreHidden(v: Boolean) {
        _uiState.update { it.copy(playStoreHidden = v) }
        viewModelScope.launch {
            val ok = repo.setPlayStoreHidden(v)
            if (ok) toast(if (v) "Play Store hidden" else "Play Store visible")
            else { _uiState.update { it.copy(playStoreHidden = !v) }; toast("Failed — requires Device Owner") }
        }
    }

    // ── Extended hardware restrictions ────────────────────────────────────────

    fun setUsbTransferBlocked(v: Boolean) {
        _uiState.update { it.copy(usbTransferBlocked = v) }
        viewModelScope.launch {
            val ok = repo.setUsbTransferBlocked(v)
            if (ok) toast(if (v) "USB file transfer blocked" else "USB file transfer allowed")
            else { _uiState.update { it.copy(usbTransferBlocked = !v) }; toast("Failed — requires Device Admin on Android 9+") }
        }
    }

    fun setBluetoothDisabled(v: Boolean) {
        _uiState.update { it.copy(bluetoothDisabled = v) }
        viewModelScope.launch {
            val ok = repo.setBluetoothDisabled(v)
            if (ok) toast(if (v) "Bluetooth disabled" else "Bluetooth enabled")
            else { _uiState.update { it.copy(bluetoothDisabled = !v) }; toast("Failed — ensure Device Admin is active") }
        }
    }

    fun setOutgoingCallsBlocked(v: Boolean) {
        _uiState.update { it.copy(outgoingCallsBlocked = v) }
        viewModelScope.launch {
            val ok = repo.setOutgoingCallsBlocked(v)
            if (ok) toast(if (v) "Outgoing calls blocked" else "Outgoing calls allowed")
            else { _uiState.update { it.copy(outgoingCallsBlocked = !v) }; toast("Failed — ensure Device Admin is active") }
        }
    }

    fun setPhysicalMediaBlocked(v: Boolean) {
        _uiState.update { it.copy(physicalMediaBlocked = v) }
        viewModelScope.launch {
            val ok = repo.setPhysicalMediaBlocked(v)
            if (ok) toast(if (v) "Physical media blocked" else "Physical media allowed")
            else { _uiState.update { it.copy(physicalMediaBlocked = !v) }; toast("Failed — ensure Device Admin is active") }
        }
    }

    fun setMdmUninstallProtected(v: Boolean) {
        _uiState.update { it.copy(mdmUninstallProtected = v) }
        viewModelScope.launch {
            val ok = repo.setMdmUninstallProtected(v)
            if (ok) toast(if (v) "MDM uninstall blocked" else "MDM uninstall allowed")
            else { _uiState.update { it.copy(mdmUninstallProtected = !v) }; toast("Failed — requires Device Owner") }
        }
    }

    fun setBrowsersHidden(v: Boolean) {
        _uiState.update { it.copy(browsersHidden = v) }
        viewModelScope.launch {
            val ok = repo.setBrowsersHidden(v)
            if (ok) toast(if (v) "Browsers hidden" else "Browsers visible")
            else { _uiState.update { it.copy(browsersHidden = !v) }; toast("Failed — requires Device Owner") }
        }
    }

    // ── Hardware ──────────────────────────────────────────────────────────────

    fun setCameraDisabled(v: Boolean) {
        _uiState.update { it.copy(cameraDisabled = v) }
        viewModelScope.launch {
            repo.setCameraDisabled(v)
            toast(if (v) "Camera disabled" else "Camera enabled")
        }
    }

    fun setScreenCaptureDisabled(v: Boolean) {
        _uiState.update { it.copy(screenCaptureDisabled = v) }
        viewModelScope.launch {
            repo.setScreenCaptureDisabled(v)
            toast(if (v) "Screen capture disabled" else "Screen capture enabled")
        }
    }

    // ── Settings / hardware control ───────────────────────────────────────────

    fun setWifiConfigBlocked(v: Boolean) = toggleRestriction(
        UserManager.DISALLOW_CONFIG_WIFI, v,
        { copy(wifiConfigBlocked = v) }, { copy(wifiConfigBlocked = !v) })

    fun setMobileNetworksBlocked(v: Boolean) = toggleRestriction(
        UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS, v,
        { copy(mobileNetworksBlocked = v) }, { copy(mobileNetworksBlocked = !v) })

    fun setBluetoothConfigBlocked(v: Boolean) = toggleRestriction(
        UserManager.DISALLOW_CONFIG_BLUETOOTH, v,
        { copy(bluetoothConfigBlocked = v) }, { copy(bluetoothConfigBlocked = !v) })

    fun setVpnBlocked(v: Boolean) = toggleRestriction(
        UserManager.DISALLOW_CONFIG_VPN, v,
        { copy(vpnBlocked = v) }, { copy(vpnBlocked = !v) })

    fun setNetworkResetBlocked(v: Boolean) = toggleRestriction(
        UserManager.DISALLOW_NETWORK_RESET, v,
        { copy(networkResetBlocked = v) }, { copy(networkResetBlocked = !v) })

    fun setStatusBarDisabled(v: Boolean) {
        _uiState.update { it.copy(statusBarDisabled = v) }
        viewModelScope.launch {
            val ok = repo.setStatusBarDisabled(v)
            if (ok) toast(if (v) "Status bar locked" else "Status bar unlocked")
            else { _uiState.update { it.copy(statusBarDisabled = !v) }; toast("Failed — requires Device Owner on Android 6+") }
        }
    }

    // ── Network / DNS ─────────────────────────────────────────────────────────

    fun enforcePrivateDns(host: String) {
        viewModelScope.launch {
            val ok = repo.setPrivateDns(host)
            _uiState.update { it.copy(privateDnsRestricted = ok, privateDnsHost = if (ok) host else it.privateDnsHost) }
            toast(if (ok) "DNS locked to $host" else "DNS enforcement failed — requires Android 10+ Device Owner")
        }
    }

    fun clearPrivateDns() {
        viewModelScope.launch {
            repo.clearPrivateDns()
            _uiState.update { it.copy(privateDnsRestricted = false, privateDnsHost = "") }
            toast("Private DNS reset to automatic")
        }
    }

    // ── Device actions ────────────────────────────────────────────────────────

    fun lockDevice() { viewModelScope.launch { repo.lockNow() } }

    fun reboot() { viewModelScope.launch { repo.reboot() } }

    fun removeSelf() {
        viewModelScope.launch(Dispatchers.IO) {
            UninstallFlag.pending = true
            // clearDeviceOwnerApp() → then removeActiveAdmin() sends DEVICE_ADMIN_DISABLED
            // broadcast → MdmDeviceAdmin.onDisabled() launches the uninstall intent once
            // admin removal is fully confirmed by the system.
            DevicePolicyHelper.getInstance(getApplication()).clearAllAdminPrivileges()
        }
    }

    fun applyFullLockdown() {
        viewModelScope.launch {
            listOf(
                UserManager.DISALLOW_SAFE_BOOT,
                UserManager.DISALLOW_FACTORY_RESET,
                UserManager.DISALLOW_DEBUGGING_FEATURES,
                UserManager.DISALLOW_ADD_USER,
                UserManager.DISALLOW_INSTALL_APPS,
                UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
                UserManager.DISALLOW_UNINSTALL_APPS,
                UserManager.DISALLOW_CONFIG_WIFI,
                UserManager.DISALLOW_CONFIG_BLUETOOTH,
                UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS,
            ).forEach { repo.setRestriction(it, true) }
            repo.setStatusBarDisabled(true)
            repo.setScreenCaptureDisabled(true)
            loadState()
            toast("Full lockdown applied")
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun toggleRestriction(
        key: String,
        enabled: Boolean,
        optimisticUpdate: DashboardUiState.() -> DashboardUiState,
        rollback: DashboardUiState.() -> DashboardUiState
    ) {
        _uiState.update { it.optimisticUpdate() }
        viewModelScope.launch {
            if (repo.setRestriction(key, enabled)) {
                toast(formatRestrictionToast(key, enabled))
            } else {
                _uiState.update { it.rollback() }
                toast("Failed — ensure Device Admin is active")
            }
        }
    }

    private fun toast(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    private fun formatRestrictionToast(key: String, enabled: Boolean): String {
        val action = if (enabled) "enforced" else "removed"
        return "${key.substringAfterLast('_').lowercase().replaceFirstChar { it.uppercase() }} $action"
    }
}
