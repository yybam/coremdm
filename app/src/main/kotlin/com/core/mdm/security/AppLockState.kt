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
    // Default true — MdmApplication.onCreate() will unlock immediately if no PIN is set.
    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    fun lock()   { _isLocked.value = true  }
    fun unlock() { _isLocked.value = false }
}
