package com.core.mdm.firebase

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentReference
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

    /**
     * Syncs the list of installed applications up to the device doc so the admin can see
     * what is on the phone from the web console. Light payload — package name, label, and
     * version only (no icons; those would blow past the 1 MB document limit). Launchable and
     * user-installed apps are included; pure system packages without a launcher are skipped.
     */
    fun updateInstalledApps(context: Context) {
        uid() ?: return
        val pm = context.packageManager
        val launchable = runCatching {
            pm.getInstalledApplications(0)
                .asSequence()
                .filter { ai ->
                    (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                        pm.getLaunchIntentForPackage(ai.packageName) != null
                }
                .map { ai ->
                    val version = runCatching {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(ai.packageName, 0).versionName
                    }.getOrNull() ?: ""
                    mapOf(
                        "pkg"     to ai.packageName,
                        "label"   to runCatching { pm.getApplicationLabel(ai).toString() }
                                        .getOrDefault(ai.packageName),
                        "version" to version,
                        "system"  to ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0),
                    )
                }
                .sortedBy { (it["label"] as String).lowercase() }
                .take(400)   // keep the doc well under the 1 MB Firestore limit
                .toList()
        }.getOrElse {
            Log.e(TAG, "updateInstalledApps query failed: ${it.message}")
            return
        }
        val id = EnrollmentManager.getHardwareId(context)
        devices().document(id)
            .update("installedApps", launchable, "installedAppsUpdatedAt", FieldValue.serverTimestamp())
            .addOnSuccessListener { Log.d(TAG, "installedApps synced (${launchable.size}) for $id") }
            .addOnFailureListener { Log.e(TAG, "updateInstalledApps FAILED: ${it.message}") }
    }

    /**
     * Listens to the per-device command queue (devices/{id}/commands) for commands still in
     * the `pending` state. Each pending command is handed to [onCommand] together with its
     * document reference; the caller claims it with [claimCommand] before executing so a
     * command cancelled from the web console is never run.
     */
    fun watchCommandQueue(
        context: Context,
        onCommand: (type: String, ref: DocumentReference, data: Map<String, Any>) -> Unit,
        onError: (() -> Unit)? = null,
    ): ListenerRegistration? {
        uid() ?: return null
        return devices().document(EnrollmentManager.getHardwareId(context))
            .collection("commands")
            .whereEqualTo("state", "pending")
            .addSnapshotListener { snaps, error ->
                if (error != null) {
                    Log.e(TAG, "watchCommandQueue error: ${error.message}")
                    onError?.invoke()
                    return@addSnapshotListener
                }
                snaps?.documents?.forEach { doc ->
                    val type = doc.getString("type") ?: return@forEach
                    onCommand(type, doc.reference, doc.data ?: emptyMap())
                }
            }
    }

    /**
     * Atomically moves a queued command from `pending` to `executed`. Runs inside a transaction
     * that re-checks the state, so a command cancelled in the brief window before execution is
     * skipped. [onClaimed] is invoked with true only if this call is the one that claimed it.
     */
    fun claimCommand(ref: DocumentReference, onClaimed: (Boolean) -> Unit) {
        Firebase.firestore.runTransaction { txn ->
            val snap = txn.get(ref)
            if (snap.getString("state") != "pending") return@runTransaction false
            txn.update(ref, mapOf(
                "state"      to "executed",
                "executedAt" to FieldValue.serverTimestamp(),
            ))
            true
        }.addOnSuccessListener { claimed -> onClaimed(claimed == true) }
            .addOnFailureListener {
                Log.e(TAG, "claimCommand failed: ${it.message}")
                onClaimed(false)
            }
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
        onError: (() -> Unit)? = null,
    ): ListenerRegistration? {
        uid() ?: return null
        return devices().document(EnrollmentManager.getHardwareId(context))
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.e(TAG, "watchCommands listener error: ${error.message}")
                    onError?.invoke()
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
                    // Clear the flag and wait for Firestore to CONFIRM the write before
                    // rebooting. dpm.reboot() restarts the device instantly, so if we
                    // cleared afterwards the write would never reach the server and the
                    // device would re-read rebootCommand==true on every boot — an endless
                    // reboot loop. Only reboot once the server has acked the clear.
                    snap.reference.update("rebootCommand", false)
                        .addOnSuccessListener { onRebootCommand?.invoke() }
                        .addOnFailureListener {
                            Log.e(TAG, "clear rebootCommand failed, not rebooting: ${it.message}")
                        }
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
