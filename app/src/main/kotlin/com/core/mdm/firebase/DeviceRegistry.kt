package com.core.mdm.firebase

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

object DeviceRegistry {

    private fun devicesRef(uid: String) =
        Firebase.firestore.collection("users").document(uid).collection("devices")

    private fun uid(): String? = Firebase.auth.currentUser?.uid

    fun register(context: Context, fcmToken: String? = null) {
        val uid = uid() ?: return
        val deviceId = DeviceId.get(context)
        val data = hashMapOf<String, Any>(
            "deviceId"   to deviceId,
            "deviceName" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "osVersion"  to "Android ${Build.VERSION.RELEASE}",
            "lastSeen"   to FieldValue.serverTimestamp(),
            "ownerId"    to uid,
        )
        if (fcmToken != null) data["fcmToken"] = fcmToken
        devicesRef(uid).document(deviceId)
            .set(data, SetOptions.merge())
            .addOnFailureListener { /* network may be unavailable */ }
    }

    fun updateLastSeen(context: Context) {
        val uid = uid() ?: return
        devicesRef(uid).document(DeviceId.get(context))
            .update("lastSeen", FieldValue.serverTimestamp())
    }

    fun storeFcmToken(context: Context, token: String) {
        val uid = uid() ?: return
        devicesRef(uid).document(DeviceId.get(context))
            .update("fcmToken", token)
    }

    fun watchCommands(context: Context, onAlarmChange: (Boolean) -> Unit): ListenerRegistration? {
        val uid = uid() ?: return null
        return devicesRef(uid).document(DeviceId.get(context))
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) return@addSnapshotListener
                onAlarmChange(snap.getBoolean("alarmActive") ?: false)
            }
    }

    fun setAlarmForDevice(deviceId: String, active: Boolean) {
        val uid = uid() ?: return
        Log.d("DeviceRegistry", "setAlarmForDevice: deviceId=$deviceId active=$active")
        devicesRef(uid).document(deviceId)
            .update("alarmActive", active)
            .addOnSuccessListener { Log.d("DeviceRegistry", "alarmActive=$active written for $deviceId") }
            .addOnFailureListener { Log.e("DeviceRegistry", "Failed to write alarmActive=$active: ${it.message}") }
    }

    fun watchAllDevices(onUpdate: (List<DeviceInfo>) -> Unit): ListenerRegistration? {
        val uid = uid() ?: return null
        return devicesRef(uid)
            .addSnapshotListener { snaps, error ->
                if (error != null || snaps == null) return@addSnapshotListener
                val devices = snaps.documents.mapNotNull { doc ->
                    DeviceInfo(
                        id          = doc.id,
                        name        = doc.getString("deviceName") ?: "Unknown Device",
                        osVersion   = doc.getString("osVersion") ?: "",
                        alarmActive = doc.getBoolean("alarmActive") ?: false,
                        lastSeen    = doc.getTimestamp("lastSeen")?.toDate()?.time
                    )
                }
                onUpdate(devices)
            }
    }
}
