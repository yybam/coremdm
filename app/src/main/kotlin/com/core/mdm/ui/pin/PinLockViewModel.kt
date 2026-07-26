package com.core.mdm.ui.pin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.core.mdm.security.AppLockState
import com.core.mdm.security.PinManager
import com.core.mdm.security.VerifyResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── Screen modes ──────────────────────────────────────────────────────────────────

enum class PinScreenMode {
    VERIFY,           // authenticate to unlock (shown over dashboard)
    SETUP,            // first-time PIN entry
    SETUP_CONFIRM,    // confirm new PIN
    CHANGE_VERIFY,    // verify old PIN before changing
    CHANGE_NEW,       // enter new PIN
    CHANGE_CONFIRM,   // confirm new PIN
    REMOVE_VERIFY,    // verify current PIN before removing it entirely
}

// ── UI state ──────────────────────────────────────────────────────────────────────

data class PinLockUiState(
    val mode: PinScreenMode           = PinScreenMode.VERIFY,
    val digits: String                = "",
    val firstEntry: String            = "",   // holds first entry during 2-step setup/change
    val isPinSet: Boolean             = false,
    val isAuthenticated: Boolean      = false,
    val isSetupComplete: Boolean      = false,
    val isLockedOut: Boolean          = false,
    val lockoutSecondsRemaining: Int  = 0,
    val errorMessage: String?         = null,
    val successMessage: String?       = null,
    val showRemoveDialog: Boolean     = false,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────────

class PinLockViewModel(
    application: Application,
    private val startMode: PinScreenMode,
) : AndroidViewModel(application) {

    companion object {
        /**
         * Factory — pass [startMode] to specify the initial screen mode.
         * Defaults to VERIFY if a PIN is set, SETUP if not.
         */
        fun factory(startMode: PinScreenMode? = null) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                val pm  = PinManager.getInstance(app)
                val mode = startMode ?: if (pm.isPinSet) PinScreenMode.VERIFY else PinScreenMode.SETUP
                return PinLockViewModel(app, mode) as T
            }
        }
    }

    private val pinManager = PinManager.getInstance(application)

    private val _uiState = MutableStateFlow(
        PinLockUiState(
            mode     = startMode,
            isPinSet = pinManager.isPinSet,
            isLockedOut             = pinManager.lockoutRemainingMs() > 0,
            lockoutSecondsRemaining = (pinManager.lockoutRemainingMs() / 1000).toInt(),
        )
    )
    val uiState: StateFlow<PinLockUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    init {
        val remaining = pinManager.lockoutRemainingMs()
        if (remaining > 0) startCountdown(remaining)
    }

    // ── Input ─────────────────────────────────────────────────────────────────────

    fun onDigit(d: String) {
        val s = _uiState.value
        if (s.isLockedOut || s.digits.length >= 6) return
        val next = s.digits + d
        _uiState.update { it.copy(digits = next, errorMessage = null) }
        // Auto-submit at 4 digits (minimum PIN length)
        if (next.length == 4) {
            viewModelScope.launch {
                delay(80)   // tiny delay so the 4th dot renders before the transition
                submit()
            }
        }
    }

    fun onBackspace() = _uiState.update { it.copy(digits = it.digits.dropLast(1), errorMessage = null) }

    fun onClear() = _uiState.update { it.copy(digits = "", errorMessage = null) }

    fun submit() {
        val s = _uiState.value
        when (s.mode) {
            PinScreenMode.VERIFY         -> verify(s.digits)
            PinScreenMode.SETUP          -> beginSetup(s.digits)
            PinScreenMode.SETUP_CONFIRM  -> confirmSetup(s.firstEntry, s.digits)
            PinScreenMode.CHANGE_VERIFY  -> verifyForChange(s.digits)
            PinScreenMode.CHANGE_NEW     -> beginChange(s.digits)
            PinScreenMode.CHANGE_CONFIRM -> confirmChange(s.firstEntry, s.digits)
            PinScreenMode.REMOVE_VERIFY  -> verifyForRemove(s.digits)
        }
    }

    // ── Public control ────────────────────────────────────────────────────────────

    fun requestRemovePin() = _uiState.update { it.copy(showRemoveDialog = true) }
    fun dismissRemoveDialog() = _uiState.update { it.copy(showRemoveDialog = false) }

    fun confirmRemovePin() {
        _uiState.update { it.copy(
            showRemoveDialog = false,
            mode             = PinScreenMode.REMOVE_VERIFY,
            digits           = "",
            errorMessage     = null,
        )}
    }

    fun clearSnackbar() = _uiState.update { it.copy(errorMessage = null, successMessage = null) }

    // ── Private: VERIFY flow ──────────────────────────────────────────────────────

    private fun verify(pin: String) {
        when (val r = pinManager.verifyPin(pin)) {
            VerifyResult.Success -> {
                AppLockState.unlock()
                _uiState.update { it.copy(isAuthenticated = true, digits = "") }
            }
            is VerifyResult.Wrong -> {
                val msg = if (r.lockoutMs > 0)
                    "Too many attempts — locked for ${r.lockoutMs / 1000}s"
                else "Incorrect PIN (${r.totalAttempts} attempt${if (r.totalAttempts > 1) "s" else ""})"
                _uiState.update { it.copy(digits = "", errorMessage = msg) }
                if (r.lockoutMs > 0) startCountdown(r.lockoutMs)
            }
            is VerifyResult.LockedOut -> {
                _uiState.update { it.copy(digits = "") }
                startCountdown(r.remainingMs)
            }
            else -> _uiState.update { it.copy(digits = "", errorMessage = "Verification error") }
        }
    }

    // ── Private: SETUP flow ───────────────────────────────────────────────────────

    private fun beginSetup(pin: String) {
        if (pin.length < 4) {
            _uiState.update { it.copy(errorMessage = "PIN must be at least 4 digits") }
            return
        }
        _uiState.update { it.copy(
            mode       = PinScreenMode.SETUP_CONFIRM,
            firstEntry = pin,
            digits     = "",
            errorMessage = null,
        )}
    }

    private fun confirmSetup(first: String, confirm: String) {
        if (first != confirm) {
            _uiState.update { it.copy(
                mode       = PinScreenMode.SETUP,
                digits     = "",
                firstEntry = "",
                errorMessage = "PINs do not match — try again",
            )}
            return
        }
        pinManager.setPin(first)
        AppLockState.unlock()
        _uiState.update { it.copy(
            isPinSet       = true,
            isSetupComplete = true,
            mode           = PinScreenMode.VERIFY,
            digits         = "",
            firstEntry     = "",
            successMessage = "PIN set successfully",
        )}
    }

    // ── Private: CHANGE flow ──────────────────────────────────────────────────────

    private fun verifyForChange(pin: String) {
        when (pinManager.verifyPin(pin)) {
            VerifyResult.Success -> _uiState.update { it.copy(
                mode  = PinScreenMode.CHANGE_NEW,
                digits = "",
                errorMessage = null,
            )}
            is VerifyResult.Wrong -> _uiState.update { it.copy(
                digits       = "",
                errorMessage = "Incorrect current PIN",
            )}
            else -> _uiState.update { it.copy(
                digits       = "",
                errorMessage = "Verification failed",
            )}
        }
    }

    private fun verifyForRemove(pin: String) {
        when (pinManager.verifyPin(pin)) {
            VerifyResult.Success -> {
                pinManager.clearPin()
                _uiState.update { it.copy(
                    isPinSet        = false,
                    isSetupComplete = true,
                    digits          = "",
                    successMessage  = "PIN removed — device unlocked",
                )}
            }
            is VerifyResult.Wrong -> _uiState.update { it.copy(
                digits       = "",
                errorMessage = "Incorrect PIN — removal cancelled",
            )}
            else -> _uiState.update { it.copy(
                digits       = "",
                errorMessage = "Verification failed",
            )}
        }
    }

    private fun beginChange(pin: String) {
        if (pin.length < 4) {
            _uiState.update { it.copy(errorMessage = "PIN must be at least 4 digits") }
            return
        }
        _uiState.update { it.copy(
            mode       = PinScreenMode.CHANGE_CONFIRM,
            firstEntry = pin,
            digits     = "",
            errorMessage = null,
        )}
    }

    private fun confirmChange(newPin: String, confirm: String) {
        if (newPin != confirm) {
            _uiState.update { it.copy(
                mode       = PinScreenMode.CHANGE_NEW,
                digits     = "",
                firstEntry = "",
                errorMessage = "PINs do not match",
            )}
            return
        }
        pinManager.setPin(newPin)
        _uiState.update { it.copy(
            isSetupComplete = true,
            digits          = "",
            firstEntry      = "",
            successMessage  = "PIN changed successfully",
        )}
    }

    // ── Lockout countdown ─────────────────────────────────────────────────────────

    private fun startCountdown(durationMs: Long) {
        countdownJob?.cancel()
        _uiState.update { it.copy(
            isLockedOut             = true,
            lockoutSecondsRemaining = (durationMs / 1000).toInt()
        )}
        countdownJob = viewModelScope.launch {
            var remaining = durationMs
            while (remaining > 0) {
                delay(1_000)
                remaining -= 1_000
                _uiState.update { it.copy(
                    lockoutSecondsRemaining = maxOf(0, (remaining / 1000).toInt())
                )}
            }
            _uiState.update { it.copy(isLockedOut = false, lockoutSecondsRemaining = 0) }
        }
    }

    override fun onCleared() {
        countdownJob?.cancel()
        super.onCleared()
    }
}
