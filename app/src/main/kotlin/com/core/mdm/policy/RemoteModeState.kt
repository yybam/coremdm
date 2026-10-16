package com.core.mdm.policy

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * "Remote-only" mode, switched on from the website: the app's own controls are locked and the
 * device can only be managed from the web console.
 * Persisted so the lock holds at boot before Firestore has synced.
 */
object RemoteModeState {
    private const val PREFS = "mdm_remote_mode"
    private const val KEY  = "remote_only"

    private val _remoteOnly = MutableStateFlow(false)
    val remoteOnly: StateFlow<Boolean> = _remoteOnly

    fun init(context: Context) {
        _remoteOnly.value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
    }

    fun set(context: Context, enabled: Boolean) {
        if (_remoteOnly.value == enabled) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply()
        _remoteOnly.value = enabled
    }
}
