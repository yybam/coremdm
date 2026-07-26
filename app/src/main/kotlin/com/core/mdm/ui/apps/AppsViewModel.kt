package com.core.mdm.ui.apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.core.mdm.data.PolicyRepository
import com.core.mdm.policy.AppStatus
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppsUiState(
    val isLoading: Boolean = false,
    val apps: List<AppStatus> = emptyList(),
    val filteredApps: List<AppStatus> = emptyList(),
    val searchQuery: String = "",
    val showSystemApps: Boolean = false,
    val snackbarMessage: String? = null,
)

class AppsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = PolicyRepository.getInstance(application)

    private val _uiState = MutableStateFlow(AppsUiState())
    val uiState: StateFlow<AppsUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        loadApps()
        observeSearch()
    }

    fun loadApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val apps = repo.getInstalledApps(includeSystem = _uiState.value.showSystemApps)
            _uiState.update {
                it.copy(
                    isLoading    = false,
                    apps         = apps,
                    filteredApps = filter(apps, it.searchQuery)
                )
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        _uiState.update { state ->
            state.copy(
                searchQuery  = query,
                filteredApps = filter(state.apps, query)
            )
        }
    }

    fun setShowSystemApps(show: Boolean) {
        _uiState.update { it.copy(showSystemApps = show) }
        loadApps()
    }

    // ── App actions ───────────────────────────────────────────────────────────

    fun toggleHidden(app: AppStatus) {
        viewModelScope.launch {
            val nowHidden = if (app.isHidden) {
                repo.unhideApp(app.packageName)
                false
            } else {
                repo.hideApp(app.packageName)
                true
            }
            refreshApp(app.packageName, hidden = nowHidden, suspended = app.isSuspended)
            toast("${app.label} ${if (nowHidden) "hidden" else "visible"}")
        }
    }

    fun toggleSuspended(app: AppStatus) {
        viewModelScope.launch {
            val nowSuspended = if (app.isSuspended) {
                repo.unsuspendApp(app.packageName)
                false
            } else {
                repo.suspendApp(app.packageName)
                true
            }
            refreshApp(app.packageName, hidden = app.isHidden, suspended = nowSuspended)
            toast("${app.label} ${if (nowSuspended) "suspended" else "unsuspended"}")
        }
    }

    fun clearAllEnforcements() {
        viewModelScope.launch {
            _uiState.value.apps.forEach { app ->
                if (app.isHidden)    repo.unhideApp(app.packageName)
                if (app.isSuspended) repo.unsuspendApp(app.packageName)
            }
            loadApps()
            toast("All app restrictions cleared")
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    @OptIn(FlowPreview::class)
    private fun observeSearch() {
        viewModelScope.launch {
            _searchQuery
                .debounce(200)
                .collect { query ->
                    _uiState.update { state ->
                        state.copy(filteredApps = filter(state.apps, query))
                    }
                }
        }
    }

    private fun refreshApp(packageName: String, hidden: Boolean, suspended: Boolean) {
        _uiState.update { state ->
            val updatedApps = state.apps.map { app ->
                if (app.packageName == packageName)
                    app.copy(isHidden = hidden, isSuspended = suspended)
                else app
            }
            state.copy(
                apps         = updatedApps,
                filteredApps = filter(updatedApps, state.searchQuery)
            )
        }
    }

    private fun filter(apps: List<AppStatus>, query: String): List<AppStatus> {
        if (query.isBlank()) return apps
        val q = query.trim().lowercase()
        return apps.filter {
            it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }

    private fun toast(msg: String) {
        _uiState.update { it.copy(snackbarMessage = msg) }
    }
}
