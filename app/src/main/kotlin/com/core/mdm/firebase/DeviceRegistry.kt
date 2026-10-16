package com.core.mdm.firebase

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.core.mdm.org.OrgRules
import com.core.mdm.org.OrgState
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DeviceRegistry {

    private const val TAG = "DeviceRegistry"

    private fun devices() = Firebase.firestore.collection("devices")
    private fun uid(): String? = Firebase.auth.currentUser?.uid

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
        onDeviceRemoved: (() -> Unit)? = null,
        onError: (() -> Unit)? = null,
    ): ListenerRegistration? {
        uid() ?: return null
        val handlers = mapOf(
            "lockCommand"         to onLockCommand,
            "wipeCommand"         to onWipeCommand,
            "rebootCommand"       to onRebootCommand,
            "fullLockdownCommand" to onFullLockdownCommand,
        )
        // Latest own settings + name; re-merged with the org's rules whenever either changes.
        var own: Map<String, Any> = emptyMap()
        var hasOwn = false
        var name = ""
        val deliver = {
            val rules = OrgState.rules()
            val merged = OrgRules.merge(own, rules)
            RemotePolicies.onSnapshot(context, merged, name)
            if (hasOwn || rules.isNotEmpty()) onPoliciesChange?.invoke(merged)
        }
        return devices().document(EnrollmentManager.getHardwareId(context))
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.e(TAG, "watchCommands listener error: ${error.message}")
                    onError?.invoke()
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                // Only trust server state for one-shot commands and removal: at boot the local
                // cache can still show a command that already ran (this was the reboot loop).
                val fresh = !snap.metadata.isFromCache && !snap.metadata.hasPendingWrites()
                if (!snap.exists()) {
                    if (fresh && EnrollmentManager.wasEnrolled(context)) onDeviceRemoved?.invoke()
                    return@addSnapshotListener
                }
                onAlarmChange(snap.getBoolean("alarmActive") ?: false)
                if (fresh) {
                    handlers.forEach { (field, action) ->
                        if (snap.getBoolean(field) == true) claimFlag(snap.reference, field) { action?.invoke() }
                    }
                }
                @Suppress("UNCHECKED_CAST")
                own = (snap.get("policies") as? Map<String, Any>).orEmpty()
                hasOwn = snap.contains("policies")
                // Once per phone: upload phone-side settings the website has never seen.
                if (fresh) LocalSettingsSync.backfillOnce(context, own)
                name = snap.getString("name") ?: ""
                val orgId = snap.getString("orgId")
                OrgState.watch(context, orgId, deliver)
                deliver()
                if (fresh && orgId == null) CoroutineScope(Dispatchers.IO).launch { OrgState.autoJoinOwnedOrg(context) }
            }
    }

    /**
     * Atomically flips a one-shot command flag (lockCommand, rebootCommand, …) back to false on
     * the server and only runs the command if this device won that write. A transaction only
     * commits once the server has it, so a reboot/wipe can never be replayed after restart, and
     * a flag cleared from the website in the meantime is simply skipped.
     */
    private fun claimFlag(ref: DocumentReference, field: String, run: () -> Unit) {
        Firebase.firestore.runTransaction { tx ->
            val current = tx.get(ref)
            if (current.getBoolean(field) == true) {
                tx.update(ref, field, false, "${field}DoneAt", FieldValue.serverTimestamp())
                true
            } else false
        }.addOnSuccessListener { claimed ->
            if (claimed) {
                Log.i(TAG, "Claimed $field — executing")
                run()
            }
        }.addOnFailureListener { Log.w(TAG, "Claim $field failed: ${it.message}") }
    }

    /**
     * Publishes the installed-app list, CoreMDM version and screen-guard / time-limit state so
     * the website can show them. `apps` supersedes the older `installedApps` field.
     */
    fun reportDeviceInfo(
        context: Context,
        apps: List<Map<String, Any>>,
        versionName: String,
        versionCode: Long,
        guardEnabled: Boolean,
        usageAccess: Boolean,
        timeUsage: Map<String, Int>,
    ) {
        uid() ?: return
        devices().document(EnrollmentManager.getHardwareId(context))
            .update(
                "apps", apps,
                "appsUpdatedAt", FieldValue.serverTimestamp(),
                "appVersion", versionName,
                "appVersionCode", versionCode,
                "guardEnabled", guardEnabled,
                "usageAccess", usageAccess,
                "timeUsage", timeUsage,
            )
            .addOnFailureListener { Log.w(TAG, "reportDeviceInfo failed: ${it.message}") }
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

    fun writeUpdateConfirmation(
        context: Context,
        version: String,
        success: Boolean,
        error:   String? = null,
    ) {
        uid() ?: return
        val id = EnrollmentManager.getHardwareId(context)
        val update = mutableMapOf<String, Any>(
            "lastInstalledVersion" to if (success) version else "",
            "lastInstallSuccess"   to success,
            "lastInstallAt"        to FieldValue.serverTimestamp(),
        )
        if (error != null) update["lastInstallError"] = error
        devices().document(id).update(update)
            .addOnFailureListener { Log.e(TAG, "writeUpdateConfirmation failed: ${it.message}") }
    }

    fun writePolicyAck(context: Context) {
        uid() ?: return
        val id = EnrollmentManager.getHardwareId(context)
        devices().document(id)
            .update("policyAcknowledgedAt", FieldValue.serverTimestamp())
            .addOnFailureListener { Log.e(TAG, "writePolicyAck failed: ${it.message}") }
    }

    fun writeDeviceStatus(
        context: Context,
        batteryLevel: Int,
        isCharging: Boolean,
        networkInfo: Map<String, Any>,
    ) {
        uid() ?: return
        val id = EnrollmentManager.getHardwareId(context)
        val update = mutableMapOf<String, Any>(
            "batteryLevel" to batteryLevel,
            "isCharging"   to isCharging,
        )
        update.putAll(networkInfo)
        devices().document(id).update(update)
            .addOnFailureListener { Log.e(TAG, "writeDeviceStatus failed: ${it.message}") }
    }

    fun writeLocation(context: Context, lat: Double, lng: Double, accuracy: Float) {
        uid() ?: return
        val id = EnrollmentManager.getHardwareId(context)
        devices().document(id).update(mapOf(
            "lastLat"              to lat,
            "lastLng"              to lng,
            "lastLocationAccuracy" to accuracy,
            "lastLocationAt"       to FieldValue.serverTimestamp(),
        )).addOnFailureListener { Log.e(TAG, "writeLocation failed: ${it.message}") }
    }

    fun writeUsageStats(context: Context, stats: List<Map<String, Any>>) {
        uid() ?: return
        val id = EnrollmentManager.getHardwareId(context)
        devices().document(id).update(mapOf(
            "usageStats"          to stats,
            "usageStatsUpdatedAt" to FieldValue.serverTimestamp(),
        )).addOnFailureListener { Log.e(TAG, "writeUsageStats failed: ${it.message}") }
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
