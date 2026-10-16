package com.core.mdm.ui.filter

import android.app.Application
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.core.mdm.vpn.BlocklistRepository
import com.core.mdm.vpn.DnsVpnService
import com.core.mdm.vpn.DummyBrowserVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FilterUiState(
    val isFilterRunning: Boolean     = false,
    val isDummyBrowserEnabled: Boolean = false,
    val useDefaultBlocklist: Boolean = true,
    val customBlocked: List<String>  = emptyList(),
    val importedCount: Int           = 0,
    val whitelist: List<String>      = emptyList(),
    val blocklistUrl: String         = "",
    val upstreamDns: String          = "1.1.1.1",
    val isFetching: Boolean          = false,
    val snackbar: String?            = null,
    val vpnPermissionIntent: Intent? = null,  // non-null triggers permission dialog
)

class FilterViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = BlocklistRepository.getInstance(application)

    private val _state = MutableStateFlow(FilterUiState())
    val state: StateFlow<FilterUiState> = _state.asStateFlow()

    init {
        // Mirror live VPN state into UI state
        viewModelScope.launch {
            DnsVpnService.isRunning.collect { running ->
                _state.update { it.copy(isFilterRunning = running) }
            }
        }
        viewModelScope.launch {
            DummyBrowserVpnService.isRunning.collect { enabled ->
                _state.update { it.copy(isDummyBrowserEnabled = enabled) }
            }
        }
        reload()
    }

    fun reload() {
        _state.update {
            it.copy(
                useDefaultBlocklist = repo.useDefaultBlocklist,
                customBlocked       = repo.getCustomBlocked().sorted(),
                importedCount       = repo.importedCount,
                whitelist           = repo.getWhitelist().sorted(),
                blocklistUrl        = repo.blocklistUrl,
                upstreamDns         = repo.upstreamDns,
                isFilterRunning     = DnsVpnService.isRunning.value,
                isDummyBrowserEnabled = DummyBrowserVpnService.isRunning.value,
            )
        }
    }

    // ── VPN toggle ────────────────────────────────────────────────────────────

    fun requestToggleFilter() {
        val app = getApplication<Application>()
        if (DnsVpnService.isRunning.value) {
            stopFilter()
        } else {
            val permIntent = VpnService.prepare(app)
            if (permIntent == null) {
                startFilter()
            } else {
                _state.update { it.copy(vpnPermissionIntent = permIntent) }
            }
        }
    }

    fun onVpnPermissionResult(granted: Boolean) {
        _state.update { it.copy(vpnPermissionIntent = null) }
        if (granted) startFilter()
        else toast("VPN permission denied — filter not started")
    }

    private fun startFilter() {
        val intent = Intent(getApplication(), DnsVpnService::class.java)
            .setAction(DnsVpnService.ACTION_START)
        getApplication<Application>().startService(intent)
        sync("filterRunning", true)
    }

    private fun stopFilter() {
        val intent = Intent(getApplication(), DnsVpnService::class.java)
            .setAction(DnsVpnService.ACTION_STOP)
        getApplication<Application>().startService(intent)
        sync("filterRunning", false)
    }

    // ── Blocklist management ──────────────────────────────────────────────────

    fun setUseDefaultBlocklist(v: Boolean) {
        repo.useDefaultBlocklist = v
        sync(com.core.mdm.firebase.LocalSettingsSync.FILTER_DEFAULT, v)
        _state.update { it.copy(useDefaultBlocklist = v) }
    }

    fun addBlocked(domain: String) {
        if (domain.isBlank()) return
        repo.addBlocked(domain)
        sync(com.core.mdm.firebase.LocalSettingsSync.FILTER_BLOCKED, repo.getCustomBlocked().sorted())
        _state.update { it.copy(customBlocked = repo.getCustomBlocked().sorted()) }
        toast("$domain added to blocklist")
    }

    fun removeBlocked(domain: String) {
        repo.removeBlocked(domain)
        sync(com.core.mdm.firebase.LocalSettingsSync.FILTER_BLOCKED, repo.getCustomBlocked().sorted())
        _state.update { it.copy(customBlocked = repo.getCustomBlocked().sorted()) }
    }

    fun addWhitelisted(domain: String) {
        if (domain.isBlank()) return
        repo.addWhitelisted(domain)
        sync(com.core.mdm.firebase.LocalSettingsSync.FILTER_ALLOWED, repo.getWhitelist().sorted())
        _state.update { it.copy(whitelist = repo.getWhitelist().sorted()) }
        toast("$domain added to whitelist")
    }

    fun removeWhitelisted(domain: String) {
        repo.removeWhitelisted(domain)
        sync(com.core.mdm.firebase.LocalSettingsSync.FILTER_ALLOWED, repo.getWhitelist().sorted())
        _state.update { it.copy(whitelist = repo.getWhitelist().sorted()) }
    }

    fun setUpstreamDns(dns: String) {
        repo.upstreamDns = dns
        sync(com.core.mdm.firebase.LocalSettingsSync.FILTER_DNS, dns)
        _state.update { it.copy(upstreamDns = dns) }
    }

    // ── Remote blocklist fetch ────────────────────────────────────────────────

    fun setBlocklistUrl(url: String) {
        repo.blocklistUrl = url
        sync(com.core.mdm.firebase.LocalSettingsSync.FILTER_LIST_URL, url)
        _state.update { it.copy(blocklistUrl = url) }
    }

    fun fetchBlocklist() {
        val url = _state.value.blocklistUrl
        if (url.isBlank()) { toast("Enter a blocklist URL first"); return }
        _state.update { it.copy(isFetching = true) }
        viewModelScope.launch {
            repo.fetchFromUrl(url).fold(
                onSuccess = { count ->
                    _state.update { it.copy(isFetching = false, importedCount = count) }
                    toast("Imported %,d domains".format(count))
                },
                onFailure = {
                    _state.update { s -> s.copy(isFetching = false) }
                    toast("Fetch failed: ${it.message}")
                }
            )
        }
    }

    fun clearImported() {
        repo.clearImported()
        _state.update { it.copy(importedCount = 0) }
        toast("Imported domains cleared")
    }

    // ── Dummy browser toggle ──────────────────────────────────────────────────

    fun requestToggleDummyBrowser() {
        val app = getApplication<Application>()
        if (DummyBrowserVpnService.isRunning.value) {
            app.startService(
                Intent(app, DummyBrowserVpnService::class.java)
                    .setAction(DummyBrowserVpnService.ACTION_STOP)
            )
        } else {
            val permIntent = VpnService.prepare(app)
            if (permIntent == null) {
                app.startService(
                    Intent(app, DummyBrowserVpnService::class.java)
                        .setAction(DummyBrowserVpnService.ACTION_START)
                )
            } else {
                _state.update { it.copy(vpnPermissionIntent = permIntent) }
            }
        }
    }

    /** Saves a change made here so the website shows it too. */
    private fun sync(key: String, value: Any) =
        com.core.mdm.firebase.RemotePolicies.record(getApplication(), key, value)

    fun clearSnackbar() = _state.update { it.copy(snackbar = null) }

    private fun toast(msg: String) = _state.update { it.copy(snackbar = msg) }
}
