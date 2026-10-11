package com.core.mdm.ui.parental

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.core.mdm.data.PolicyRepository
import com.core.mdm.policy.ScreenTimeScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ParentalUiState(
    val screenTimeEnabled: Boolean  = false,
    val screenTimeStart: String     = "07:00",
    val screenTimeEnd: String       = "21:00",
    val emailBlocked: Boolean       = false,
    val smsBlocked: Boolean         = false,
    val isLoading: Boolean          = true,
    val snackbar: String?           = null,
)

class ParentalViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = PolicyRepository.getInstance(application)

    private val _state = MutableStateFlow(ParentalUiState())
    val state: StateFlow<ParentalUiState> = _state.asStateFlow()

    init { reload() }

    fun reload() {
        val app = getApplication<Application>()
        val (on, start, end) = ScreenTimeScheduler.load(app)
        viewModelScope.launch {
            _state.update {
                it.copy(
                    screenTimeEnabled = on,
                    screenTimeStart   = start,
                    screenTimeEnd     = end,
                    emailBlocked      = repo.isEmailBlocked(),
                    smsBlocked        = repo.isSmsBlocked(),
                    isLoading         = false,
                )
            }
        }
    }

    // ── Screen time ───────────────────────────────────────────────────────────

    fun setScreenTimeEnabled(v: Boolean) {
        _state.update { it.copy(screenTimeEnabled = v) }
        applyScreenTime(v, _state.value.screenTimeStart, _state.value.screenTimeEnd)
    }

    fun setScreenTimeStart(hhmm: String) {
        _state.update { it.copy(screenTimeStart = hhmm) }
        if (_state.value.screenTimeEnabled) applyScreenTime(true, hhmm, _state.value.screenTimeEnd)
        else ScreenTimeScheduler.save(getApplication(), false, hhmm, _state.value.screenTimeEnd)
    }

    fun setScreenTimeEnd(hhmm: String) {
        _state.update { it.copy(screenTimeEnd = hhmm) }
        if (_state.value.screenTimeEnabled) applyScreenTime(true, _state.value.screenTimeStart, hhmm)
        else ScreenTimeScheduler.save(getApplication(), false, _state.value.screenTimeStart, hhmm)
    }

    private fun applyScreenTime(enabled: Boolean, start: String, end: String) {
        val app = getApplication<Application>()
        ScreenTimeScheduler.save(app, enabled, start, end)
        if (enabled) ScreenTimeScheduler.schedule(app, start, end)
        else         ScreenTimeScheduler.cancel(app)
        toast(if (enabled) "Screen time active: $start – $end" else "Screen time disabled")
    }

    // ── Email / SMS ───────────────────────────────────────────────────────────

    fun setEmailBlocked(v: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(emailBlocked = v) }
            repo.setEmailBlocked(v)
            toast(if (v) "Email apps hidden" else "Email apps restored")
        }
    }

    fun setSmsBlocked(v: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(smsBlocked = v) }
            repo.setSmsBlocked(v)
            toast(if (v) "SMS blocked" else "SMS restored")
        }
    }

    fun clearSnackbar() = _state.update { it.copy(snackbar = null) }
    private fun toast(msg: String) = _state.update { it.copy(snackbar = msg) }
}
