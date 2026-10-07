package com.core.mdm.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-scoped in-memory authentication state.
 * Initialized by MdmApplication before any UI is inflated.
 * Survives configuration changes; resets to `true` (locked) on process death,
 * which correctly re-prompts for the PIN on the next cold start.
 */
object AppLockState {
    // Default true — the PIN screen is always the first thing shown on process start.
    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    // Incremented on every lock() call. Used as the ViewModel key in MdmRoot so each
    // lock cycle gets a brand-new ViewModel with clean state — prevents the stale
    // isAuthenticated=true from a prior unlock from instantly unlocking the next cycle.
    private val _lockEpoch = MutableStateFlow(0)
    val lockEpoch: StateFlow<Int> = _lockEpoch.asStateFlow()

    fun lock() {
        _isLocked.value = true
        _lockEpoch.value += 1
    }
    fun unlock() { _isLocked.value = false }
}
