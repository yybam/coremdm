package com.core.mdm.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

object DeviceRegistry {

    private const val TAG = "DeviceRegistry"

    private fun devices() = Firebase.firestore.collection("devices")
    private fun uid(): String? = Firebase.auth.currentUser?.uid

    // Cache last-seen policies so we don't re-apply on every 60-second heartbeat snapshot.
    private var lastPolicies: Map<String, Any>? = null

    fun updateLastSeen(context: Context) {
        uid() ?: return
        val id = EnrollmentManager.getHardwareId(context)
        devices().document(id)
            .update("lastSeen", FieldValue.serverTimestamp(), "status", "online")
            .addOnSuccessListener { Log.d(TAG, "lastSeen updated: $id") }
            .addOnFailureListener { Log.e(TAG, "updateLastSeen FAILED for $id: ${it.message}") }
    }

    fun storeFcmToken(context: Context, token: String) {
        uid() ?: return
        // Use set-merge so this succeeds even before the device doc is created by enroll().
        devices().document(EnrollmentManager.getHardwareId(context))
            .set(mapOf("fcmToken" to token), SetOptions.merge())
            .addOnFailureListener { Log.e(TAG, "storeFcmToken failed: ${it.message}") }
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
                if (error != null) {
                    Log.e(TAG, "watchCommands listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snap == null || !snap.exists()) return@addSnapshotListener
                onAlarmChange(snap.getBoolean("alarmActive") ?: false)
                if (snap.getBoolean("lockCommand") == true) {
                    onLockCommand?.invoke()
                    snap.reference.update("lockCommand", false)
                }
                if (snap.getBoolean("wipeCommand") == true) {
                    // ACK before invoking: device may not survive the wipe to clear it afterward.
                    snap.reference.update("wipeCommand", false)
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
                val policies = snap.get("policies") as? Map<String, Any>
                if (policies != null && policies != lastPolicies) {
                    lastPolicies = policies
                    onPoliciesChange?.invoke(policies)
                }
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
