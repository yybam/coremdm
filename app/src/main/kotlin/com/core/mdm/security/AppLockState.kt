package com.core.mdm.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-scoped authentication state.
 *
 * Both [Snapshot.isLocked] and [Snapshot.epoch] update in a single atomic emit so
 * Compose always reads a consistent pair. The previous design used two separate
 * StateFlows; Compose could recompose with isLocked=true before lockEpoch incremented,
 * letting the old PinLockViewModel (isAuthenticated=true) immediately re-unlock the gate.
 */
object AppLockState {

    data class Snapshot(val isLocked: Boolean, val epoch: Int)

    private val _snapshot = MutableStateFlow(Snapshot(isLocked = true, epoch = 0))

    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    fun lock() = _snapshot.update { Snapshot(isLocked = true, epoch = it.epoch + 1) }

    fun unlock() = _snapshot.update { it.copy(isLocked = false) }
}
