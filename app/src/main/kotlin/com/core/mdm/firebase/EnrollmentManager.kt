package com.core.mdm.firebase

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
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

    /**
     * Returns the best available hardware identifier in priority order:
     * IMEI → Serial → Android ID
     */
    fun getHardwareId(context: Context): String =
        getImei(context) ?: getSerial(context) ?: getAndroidId(context)

    // ── Firestore enrollment ──────────────────────────────────────────────────

    /**
     * Registers this device under /devices/{hardwareId} with the current user as ownerId.
     * Called by MdmCommandService on every start so status and lastSeen stay fresh.
     */
    fun enroll(context: Context, fcmToken: String? = null) {
        val uid = Firebase.auth.currentUser?.uid ?: run {
            Log.w(TAG, "enroll() called without authenticated user — skipping")
            return
        }
        val hardwareId = getHardwareId(context)

        val data = hashMapOf<String, Any>(
            "hardwareId"   to hardwareId,
            "androidId"    to getAndroidId(context),
            "model"        to "${Build.MANUFACTURER} ${Build.MODEL}",
            "manufacturer" to Build.MANUFACTURER,
            "osVersion"    to "Android ${Build.VERSION.RELEASE}",
            "status"       to "online",
            "ownerId"      to uid,
            "lastSeen"     to FieldValue.serverTimestamp(),
        )
        getImei(context)?.let    { data["imei"]   = it }
        getSerial(context)?.let  { data["serial"] = it }
        fcmToken?.let            { data["fcmToken"] = it }

        Firebase.firestore.collection("devices").document(hardwareId)
            .set(data, SetOptions.merge())
            .addOnSuccessListener { Log.d(TAG, "Enrolled $hardwareId (ownerId=$uid)") }
            .addOnFailureListener { Log.e(TAG, "Enrollment failed: ${it.message}") }
    }

    fun setOffline(context: Context) {
        Firebase.auth.currentUser?.uid ?: return
        val hardwareId = getHardwareId(context)
        Firebase.firestore.collection("devices").document(hardwareId)
            .update("status", "offline", "lastSeen", FieldValue.serverTimestamp())
            .addOnFailureListener { Log.e(TAG, "setOffline failed: ${it.message}") }
    }
}
