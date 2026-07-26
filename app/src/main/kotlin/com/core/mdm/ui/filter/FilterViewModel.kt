package com.core.mdm.ui.filter

import android.app.Application
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.core.mdm.vpn.BlocklistRepository
import com.core.mdm.vpn.DnsVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FilterUiState(
    val isFilterRunning: Boolean     = false,
    val useDefaultBlocklist: Boolean = true,
    val customBlocked: List<String>  = emptyList(),
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
        reload()
    }

    fun reload() {
        _state.update {
            it.copy(
                useDefaultBlocklist = repo.useDefaultBlocklist,
                customBlocked       = repo.getCustomBlocked().sorted(),
                whitelist           = repo.getWhitelist().sorted(),
                blocklistUrl        = repo.blocklistUrl,
                upstreamDns         = repo.upstreamDns,
                isFilterRunning     = DnsVpnService.isRunning.value,
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
    }

    private fun stopFilter() {
        val intent = Intent(getApplication(), DnsVpnService::class.java)
            .setAction(DnsVpnService.ACTION_STOP)
        getApplication<Application>().startService(intent)
    }

    // ── Blocklist management ──────────────────────────────────────────────────

    fun setUseDefaultBlocklist(v: Boolean) {
        repo.useDefaultBlocklist = v
        _state.update { it.copy(useDefaultBlocklist = v) }
    }

    fun addBlocked(domain: String) {
        if (domain.isBlank()) return
        repo.addBlocked(domain)
        _state.update { it.copy(customBlocked = repo.getCustomBlocked().sorted()) }
        toast("$domain added to blocklist")
    }

    fun removeBlocked(domain: String) {
        repo.removeBlocked(domain)
        _state.update { it.copy(customBlocked = repo.getCustomBlocked().sorted()) }
    }

    fun addWhitelisted(domain: String) {
        if (domain.isBlank()) return
        repo.addWhitelisted(domain)
        _state.update { it.copy(whitelist = repo.getWhitelist().sorted()) }
        toast("$domain added to whitelist")
    }

    fun removeWhitelisted(domain: String) {
        repo.removeWhitelisted(domain)
        _state.update { it.copy(whitelist = repo.getWhitelist().sorted()) }
    }

    fun setUpstreamDns(dns: String) {
        repo.upstreamDns = dns
        _state.update { it.copy(upstreamDns = dns) }
    }

    // ── Remote blocklist fetch ────────────────────────────────────────────────

    fun setBlocklistUrl(url: String) {
        repo.blocklistUrl = url
        _state.update { it.copy(blocklistUrl = url) }
    }

    fun fetchBlocklist() {
        val url = _state.value.blocklistUrl
        if (url.isBlank()) { toast("Enter a blocklist URL first"); return }
        _state.update { it.copy(isFetching = true) }
        viewModelScope.launch {
            repo.fetchFromUrl(url).fold(
                onSuccess = { count ->
                    _state.update {
                        it.copy(
                            isFetching    = false,
                            customBlocked = repo.getCustomBlocked().sorted()
                        )
                    }
                    toast("Imported $count domains")
                },
                onFailure = {
                    _state.update { s -> s.copy(isFetching = false) }
                    toast("Fetch failed: ${it.message}")
                }
            )
        }
    }

    fun clearSnackbar() = _state.update { it.copy(snackbar = null) }

    private fun toast(msg: String) = _state.update { it.copy(snackbar = msg) }
}
