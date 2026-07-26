package com.core.mdm.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

object DeviceRegistry {

    private fun devices() = Firebase.firestore.collection("devices")
    private fun uid(): String? = Firebase.auth.currentUser?.uid

    fun updateLastSeen(context: Context) {
        uid() ?: return
        devices().document(EnrollmentManager.getHardwareId(context))
            .update("lastSeen", FieldValue.serverTimestamp(), "status", "online")
            .addOnFailureListener { /* network may be unavailable */ }
    }

    fun storeFcmToken(context: Context, token: String) {
        uid() ?: return
        devices().document(EnrollmentManager.getHardwareId(context))
            .update("fcmToken", token)
    }

    fun watchCommands(
        context: Context,
        onAlarmChange: (Boolean) -> Unit,
        onLockCommand: (() -> Unit)? = null,
        onWipeCommand: (() -> Unit)? = null,
        onRebootCommand: (() -> Unit)? = null,
        onFullLockdownCommand: (() -> Unit)? = null,
        onPoliciesChange: ((Map<String, Any>) -> Unit)? = null,
    ): ListenerRegistration? {
        uid() ?: return null
        return devices().document(EnrollmentManager.getHardwareId(context))
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) return@addSnapshotListener
                onAlarmChange(snap.getBoolean("alarmActive") ?: false)
                if (snap.getBoolean("lockCommand") == true) {
                    onLockCommand?.invoke()
                    snap.reference.update("lockCommand", false)
                }
                if (snap.getBoolean("wipeCommand") == true) {
                    onWipeCommand?.invoke()
                }
                if (snap.getBoolean("rebootCommand") == true) {
                    onRebootCommand?.invoke()
                    snap.reference.update("rebootCommand", false)
                }
                if (snap.getBoolean("fullLockdownCommand") == true) {
                    onFullLockdownCommand?.invoke()
                    snap.reference.update("fullLockdownCommand", false)
                }
                @Suppress("UNCHECKED_CAST")
                (snap.get("policies") as? Map<String, Any>)?.let { onPoliciesChange?.invoke(it) }
            }
    }

    fun setAlarmForDevice(deviceId: String, active: Boolean) {
        Log.d("DeviceRegistry", "setAlarmForDevice: $deviceId active=$active")
        devices().document(deviceId)
            .update("alarmActive", active)
            .addOnSuccessListener { Log.d("DeviceRegistry", "alarmActive=$active written for $deviceId") }
            .addOnFailureListener { Log.e("DeviceRegistry", "Failed: ${it.message}") }
    }

    fun sendLockCommand(deviceId: String) {
        devices().document(deviceId).update("lockCommand", true)
            .addOnFailureListener { Log.e("DeviceRegistry", "sendLockCommand failed: ${it.message}") }
    }

    fun sendWipeCommand(deviceId: String) {
        devices().document(deviceId).update("wipeCommand", true)
            .addOnFailureListener { Log.e("DeviceRegistry", "sendWipeCommand failed: ${it.message}") }
    }

    fun sendRebootCommand(deviceId: String) {
        devices().document(deviceId).update("rebootCommand", true)
            .addOnFailureListener { Log.e("DeviceRegistry", "sendRebootCommand failed: ${it.message}") }
    }

    fun sendFullLockdownCommand(deviceId: String) {
        devices().document(deviceId).update("fullLockdownCommand", true)
            .addOnFailureListener { Log.e("DeviceRegistry", "sendFullLockdownCommand failed: ${it.message}") }
    }

    fun watchAllDevices(onUpdate: (List<DeviceInfo>) -> Unit): ListenerRegistration? {
        val uid = uid() ?: return null
        return devices()
            .whereEqualTo("ownerId", uid)
            .addSnapshotListener { snaps, error ->
                if (error != null || snaps == null) return@addSnapshotListener
                val list = snaps.documents.mapNotNull { doc ->
                    DeviceInfo(
                        id          = doc.id,
                        hardwareId  = doc.getString("hardwareId") ?: doc.id,
                        name        = doc.getString("model") ?: "Unknown Device",
                        osVersion   = doc.getString("osVersion") ?: "",
                        alarmActive = doc.getBoolean("alarmActive") ?: false,
                        lastSeen    = doc.getTimestamp("lastSeen")?.toDate()?.time,
                        imei        = doc.getString("imei"),
                        serial      = doc.getString("serial"),
                        status      = doc.getString("status") ?: "offline",
                    )
                }
                onUpdate(list)
            }
    }
}
