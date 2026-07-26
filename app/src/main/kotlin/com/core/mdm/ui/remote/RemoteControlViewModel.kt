package com.core.mdm.ui.remote

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.google.firebase.firestore.ListenerRegistration
import com.core.mdm.firebase.DeviceInfo
import com.core.mdm.firebase.DeviceRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RemoteControlViewModel(application: Application) : AndroidViewModel(application) {

    private val _devices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val devices: StateFlow<List<DeviceInfo>> = _devices.asStateFlow()

    private var listener: ListenerRegistration? = null

    init {
        listener = DeviceRegistry.watchAllDevices { _devices.value = it }
        // Returns null when no user is signed in — devices list stays empty
    }

    fun soundAlarm(deviceId: String) = DeviceRegistry.setAlarmForDevice(deviceId, true)
    fun stopAlarm(deviceId: String)  = DeviceRegistry.setAlarmForDevice(deviceId, false)

    override fun onCleared() {
        listener?.remove()
        super.onCleared()
    }
}
