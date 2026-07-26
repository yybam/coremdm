package com.core.mdm.ui.kiosk

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.core.mdm.policy.KioskModeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KioskUiState(
    val isDeviceOwner: Boolean = false,
    val allowedPackages: List<String> = emptyList(),
    val isEnabled: Boolean = false,
    val snackbar: String? = null,
)

class KioskViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = KioskModeManager(application)

    private val _state = MutableStateFlow(KioskUiState())
    val state: StateFlow<KioskUiState> = _state.asStateFlow()

    init { reload() }

    private fun reload() {
        val pkgs = manager.getAllowedPackages()
        _state.update {
            it.copy(
                isDeviceOwner   = manager.isDeviceOwner,
                allowedPackages = pkgs,
                isEnabled       = pkgs.isNotEmpty(),
            )
        }
    }

    fun addPackage(pkg: String) {
        val trimmed = pkg.trim()
        if (trimmed.isBlank()) return
        val current = _state.value.allowedPackages
        if (trimmed in current) { toast("Already in list"); return }
        val updated = current + trimmed
        apply(updated)
    }

    fun removePackage(pkg: String) {
        val updated = _state.value.allowedPackages - pkg
        apply(updated)
    }

    fun disableKiosk() {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = manager.disable()
            if (ok) { reload(); toast("Kiosk mode disabled") }
            else toast("Failed — requires Device Owner")
        }
    }

    fun clearSnackbar() = _state.update { it.copy(snackbar = null) }

    private fun apply(packages: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = manager.setAllowedPackages(packages)
            if (ok) { reload(); toast("Lock-task packages updated") }
            else toast("Failed — requires Device Owner")
        }
    }

    private fun toast(msg: String) = _state.update { it.copy(snackbar = msg) }
}
