package com.core.mdm.firebase

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

object EnrollmentManager {

    private const val TAG = "EnrollmentManager"

    // ── Hardware identifier extraction ────────────────────────────────────────

    @SuppressLint("MissingPermission", "HardwareIds")
    fun getImei(context: Context): String? {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val raw: String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                tm.getImei(0)
            } else {
                @Suppress("DEPRECATION") tm.deviceId
            }
            raw?.takeIf { it.isNotBlank() && it != "000000000000000" }
        } catch (e: Exception) {
            Log.w(TAG, "IMEI unavailable: ${e.message}")
            null
        }
    }

    @SuppressLint("MissingPermission", "HardwareIds")
    fun getSerial(context: Context): String? {
        return try {
            val raw: String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Build.getSerial()
            } else {
                @Suppress("DEPRECATION") Build.SERIAL
            }
            raw?.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
        } catch (e: Exception) {
            Log.w(TAG, "Serial unavailable: ${e.message}")
            null
        }
    }

    fun getAndroidId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    private const val PREFS_NAME = "mdm_enrollment"
    private const val KEY_HW_ID  = "hardware_id"
    private const val KEY_ENROLLED_UID = "enrolled_uid"

    /**
     * Returns a stable hardware identifier, cached in SharedPreferences so it
     * never changes even if permission levels change between reboots.
     * Priority on first derivation: IMEI → Serial → Android ID.
     */
    fun getHardwareId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = prefs.getString(KEY_HW_ID, null)
        if (!cached.isNullOrBlank()) return cached

        val derived = getImei(context) ?: getSerial(context) ?: getAndroidId(context)
        prefs.edit().putString(KEY_HW_ID, derived).apply()
        Log.i(TAG, "Hardware ID derived and cached: $derived")
        return derived
    }

    // ── Firestore enrollment ──────────────────────────────────────────────────

    /**
     * Registers this device under /devices/{hardwareId} with the current user as ownerId.
     * Called by MdmCommandService on every start so status and lastSeen stay fresh.
     */
    fun enroll(context: Context, fcmToken: String? = null, onSuccess: (() -> Unit)? = null) {
        val uid = Firebase.auth.currentUser?.uid ?: run {
            Log.w(TAG, "enroll() called without authenticated user — skipping")
            return
        }
        val hardwareId = getHardwareId(context)
        val docRef = Firebase.firestore.collection("devices").document(hardwareId)

        // Fields the device always writes — never includes ownerId so we don't
        // clobber an existing ownerId set by the admin in the web console.
        val metadata = hashMapOf<String, Any>(
            "hardwareId"   to hardwareId,
            "androidId"    to getAndroidId(context),
            "model"        to "${Build.MANUFACTURER} ${Build.MODEL}",
            "manufacturer" to Build.MANUFACTURER,
            "osVersion"    to "Android ${Build.VERSION.RELEASE}",
            "status"       to "online",
            "deviceUid"    to uid,
            "lastSeen"     to FieldValue.serverTimestamp(),
        )
        com.core.mdm.security.TermsAcceptance.last(context)?.let { (at, version) ->
            metadata["termsAcceptedAt"]      = com.google.firebase.Timestamp(java.util.Date(at))
            metadata["termsAcceptedVersion"] = version
        }
        getImei(context)?.let    { metadata["imei"]     = it }
        getSerial(context)?.let  { metadata["serial"]   = it }
        fcmToken?.let            { metadata["fcmToken"] = it }

        Log.i(TAG, "Enrolling device: hardwareId=$hardwareId deviceUid=$uid")
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val markEnrolled = { prefs.edit().putString(KEY_ENROLLED_UID, uid).apply() }

        // Try update first — preserves admin-assigned ownerId on existing docs.
        docRef.update(metadata)
            .addOnSuccessListener {
                Log.i(TAG, "Enroll (update) OK: $hardwareId")
                markEnrolled()
                onSuccess?.invoke()
            }
            .addOnFailureListener { err ->
                // A missing doc can surface as PERMISSION_DENIED rather than NOT_FOUND:
                // the update rule reads resource.data, which doesn't exist yet. The
                // create rule still requires ownerId == auth.uid, and set-merge on
                // someone else's existing doc is rejected by the update rule.
                val code = (err as? FirebaseFirestoreException)?.code
                if (code == FirebaseFirestoreException.Code.NOT_FOUND ||
                        code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    val create = {
                        // New device — create the doc and set ownerId to the current user.
                        val createData = HashMap(metadata)
                        createData["ownerId"] = uid
                        docRef.set(createData, SetOptions.merge())
                            .addOnSuccessListener {
                                Log.i(TAG, "Enroll (create) OK: $hardwareId")
                                markEnrolled()
                                onSuccess?.invoke()
                            }
                            .addOnFailureListener { Log.e(TAG, "Enroll create FAILED: ${it.message}") }
                    }
                    if (wasEnrolled(context)) {
                        // Already enrolled under this account: if the record is gone on the server
                        // the owner removed this device from the website — honour that instead of
                        // silently re-creating it.
                        docRef.get(Source.SERVER)
                            .addOnSuccessListener { snap ->
                                if (snap.exists()) Log.e(TAG, "Enroll update FAILED: ${err.message}")
                                else DeviceRemoval.handle(context)
                            }
                            .addOnFailureListener { Log.w(TAG, "Enrollment check deferred (offline?): ${it.message}") }
                    } else {
                        create()
                    }
                } else {
                    Log.e(TAG, "Enroll update FAILED: ${err.message}")
                }
            }
    }

    /** True once this device has been enrolled under the currently signed-in account. */
    fun wasEnrolled(context: Context): Boolean {
        val uid = Firebase.auth.currentUser?.uid ?: return false
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ENROLLED_UID, null) == uid
    }

    fun forgetEnrollment(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_ENROLLED_UID).apply()
    }

    fun setOffline(context: Context) {
        Firebase.auth.currentUser?.uid ?: return
        val hardwareId = getHardwareId(context)
        Firebase.firestore.collection("devices").document(hardwareId)
            .update("status", "offline", "lastSeen", FieldValue.serverTimestamp())
            .addOnFailureListener { Log.e(TAG, "setOffline failed: ${it.message}") }
    }
}
