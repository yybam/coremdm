package com.core.mdm.ui.apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.core.mdm.data.PolicyRepository
import com.core.mdm.firebase.RemotePolicies
import com.core.mdm.policy.AppBlocker
import com.core.mdm.policy.AppStatus
import com.core.mdm.policy.DevicePolicyHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppsTab { MANAGED, ALL }

/** A setting that is what keeps an app blocked — undoing the app alone wouldn't stick. */
data class BlockRule(val policyKey: String, val label: String)

data class AppsUiState(
    val isLoading: Boolean = false,
    val apps: List<AppStatus> = emptyList(),
    val tab: AppsTab = AppsTab.MANAGED,
    val searchQuery: String = "",
    val showSystemApps: Boolean = false,
    val rules: Map<String, BlockRule> = emptyMap(),
    val snackbarMessage: String? = null,
) {
    val managedApps: List<AppStatus> get() = apps.filter { it.isHidden || it.isSuspended }

    val visibleApps: List<AppStatus> get() {
        val base = if (tab == AppsTab.MANAGED) managedApps
                   else apps.filter { showSystemApps || !it.isSystem || it.isHidden || it.isSuspended }
        if (searchQuery.isBlank()) return base
        val q = searchQuery.trim().lowercase()
        return base.filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
    }
}

class AppsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = PolicyRepository.getInstance(application)

    private val _uiState = MutableStateFlow(AppsUiState())
    val uiState: StateFlow<AppsUiState> = _uiState.asStateFlow()

    private var firstLoad = true

    init { loadApps() }

    fun loadApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // Always load system apps too: they're filtered in the UI, and managed system apps
            // (a hidden Play Store, blocked browsers) must be listed so they can be restored.
            val apps = repo.getInstalledApps(includeSystem = true)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    apps      = apps,
                    rules     = computeRules(),
                    // Open on "Managed" only when something is managed
                    tab       = if (firstLoad && apps.none { a -> a.isHidden || a.isSuspended }) AppsTab.ALL else it.tab,
                )
            }
            firstLoad = false
        }
    }

    fun setTab(tab: AppsTab) = _uiState.update { it.copy(tab = tab) }
    fun setSearchQuery(query: String) = _uiState.update { it.copy(searchQuery = query) }
    fun setShowSystemApps(show: Boolean) = _uiState.update { it.copy(showSystemApps = show) }

    // ── App actions ───────────────────────────────────────────────────────────

    fun setHidden(app: AppStatus, hidden: Boolean) {
        viewModelScope.launch {
            if (hidden) repo.hideApp(app.packageName) else repo.unhideApp(app.packageName)
            // Keep the website's hidden-apps list in step
            RemotePolicies.setListItem(getApplication(), com.core.mdm.firebase.LocalSettingsSync.HIDDEN_PACKAGES, app.packageName, hidden)
            refreshApp(app.packageName, hidden = hidden, suspended = app.isSuspended)
            toast(if (hidden) "${app.label} hidden from the home screen" else "${app.label} is visible again")
        }
    }

    fun setPaused(app: AppStatus, paused: Boolean) {
        viewModelScope.launch {
            if (paused) repo.suspendApp(app.packageName) else repo.unsuspendApp(app.packageName)
            // Keep the website's per-app block list in step with what was done here
            RemotePolicies.setAppBlocked(getApplication(), app.packageName, paused)
            refreshApp(app.packageName, hidden = app.isHidden, suspended = paused)
            toast(if (paused) "${app.label} paused — it can't be opened" else "${app.label} can be used again")
        }
    }

    /** Undo everything on one app: unhide + unpause. */
    fun restore(app: AppStatus) {
        viewModelScope.launch {
            if (app.isHidden) {
                repo.unhideApp(app.packageName)
                RemotePolicies.setListItem(getApplication(), com.core.mdm.firebase.LocalSettingsSync.HIDDEN_PACKAGES, app.packageName, false)
            }
            if (app.isSuspended) {
                repo.unsuspendApp(app.packageName)
                RemotePolicies.setAppBlocked(getApplication(), app.packageName, false)
            }
            refreshApp(app.packageName, hidden = false, suspended = false)
            toast("${app.label} restored")
        }
    }

    /** Turns off the setting that keeps an app blocked (e.g. "Block WhatsApp"). */
    fun turnOffRule(rule: BlockRule) {
        RemotePolicies.set(getApplication(), rule.policyKey, false)
        toast("${rule.label} turned off")
        loadApps()
    }

    fun clearAllEnforcements() {
        viewModelScope.launch {
            _uiState.value.managedApps
                .filter { it.packageName !in _uiState.value.rules }
                .forEach { app ->
                    if (app.isHidden)    repo.unhideApp(app.packageName)
                    if (app.isSuspended) repo.unsuspendApp(app.packageName)
                }
            RemotePolicies.clearBlockedApps(getApplication())
            RemotePolicies.record(getApplication(), com.core.mdm.firebase.LocalSettingsSync.HIDDEN_PACKAGES, emptyList<String>())
            loadApps()
            toast("All apps restored")
        }
    }

    fun clearSnackbar() = _uiState.update { it.copy(snackbarMessage = null) }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun computeRules(): Map<String, BlockRule> {
        val p = RemotePolicies.policies.value
        fun on(k: String) = p[k] as? Boolean ?: false
        val rules = mutableMapOf<String, BlockRule>()
        if (on("whatsappBlocked"))    AppBlocker.WHATSAPP_PACKAGES.forEach { rules[it] = BlockRule("whatsappBlocked", "Block WhatsApp") }
        if (on("socialMediaBlocked")) AppBlocker.SOCIAL_PACKAGES.forEach { rules[it] = BlockRule("socialMediaBlocked", "Block Social Media") }
        if (on("webviewDisabled"))    DevicePolicyHelper.WEBVIEW_PACKAGES.forEach { rules[it] = BlockRule("webviewDisabled", "Block WebView") }
        return rules
    }

    private fun refreshApp(packageName: String, hidden: Boolean, suspended: Boolean) {
        _uiState.update { state ->
            state.copy(apps = state.apps.map { app ->
                if (app.packageName == packageName) app.copy(isHidden = hidden, isSuspended = suspended) else app
            })
        }
    }

    private fun toast(msg: String) = _uiState.update { it.copy(snackbarMessage = msg) }
}
