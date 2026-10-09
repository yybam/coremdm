package com.core.mdm.policy

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * In-process signal that device policy was changed by something other than the screen
 * currently showing it — today that is MdmCommandService applying policies pushed from the
 * web console via the Firestore listener. Screens that display policy state collect this
 * and re-read from DevicePolicyManager so their toggles match what was just enforced,
 * instead of showing stale values until the user taps refresh.
 */
object PolicyEvents {

    private val _remoteChanges = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow    = BufferOverflow.DROP_OLDEST,
    )

    /** Emits after a remote policy set has been applied to the device. */
    val remoteChanges: SharedFlow<Unit> = _remoteChanges.asSharedFlow()

    fun notifyRemoteChange() {
        _remoteChanges.tryEmit(Unit)
    }
}
