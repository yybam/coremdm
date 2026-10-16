package com.core.mdm.firebase

import android.content.Context
import android.util.Log
import com.core.mdm.policy.AppBlocker
import com.core.mdm.policy.DevicePolicyHelper
import com.core.mdm.content.ContentRules
import com.core.mdm.content.GuardPolicy
import com.core.mdm.policy.AppTimeLimits
import com.core.mdm.policy.InstallGuard
import com.core.mdm.policy.RemoteModeState
import com.core.mdm.org.OrgRules
import com.core.mdm.org.OrgState
import android.widget.Toast
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * One source of truth for the settings that both the website and the app can change
 * (remote-only mode, WhatsApp/social/WebView blocks, blocked apps, device name).
 *
 * Changes made in the app are applied on the phone immediately *and* written to the device's
 * Firestore document, so the website shows the same state; changes from the website arrive
 * through the service's snapshot listener via [onSnapshot]. Cached so the UI has values at boot.
 */
object RemotePolicies {
    private const val TAG = "RemotePolicies"
    private const val PREFS = "mdm_remote_policies"

    private val _policies = MutableStateFlow<Map<String, Any>>(emptyMap())
    val policies: StateFlow<Map<String, Any>> = _policies

    private val _deviceName = MutableStateFlow("")
    val deviceName: StateFlow<String> = _deviceName

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _deviceName.value = prefs.getString("name", "") ?: ""
        _policies.value = runCatching { fromJson(JSONObject(prefs.getString("policies", "{}") ?: "{}")) }
            .getOrDefault(emptyMap())
    }

    /** Called by the command listener with the latest server state. */
    fun onSnapshot(context: Context, policies: Map<String, Any>, name: String) {
        if (policies != _policies.value || name != _deviceName.value) {
            _policies.value = policies
            _deviceName.value = name
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("policies", JSONObject(policies).toString())
                .putString("name", name)
                .apply()
        }
    }

    fun isOn(key: String): Boolean = _policies.value[key] as? Boolean ?: false

    /** Toggle a website-synced policy from the app. */
    fun set(context: Context, key: String, value: Boolean) {
        if (refuseLocked(context, key)) return
        val merged = _policies.value + (key to value)
        applyLocally(context, merged, key)
        write(context, mapOf("policies.$key" to value))
    }

    /**
     * Saves a setting the dashboard has already applied on the phone itself (Android
     * restrictions, Play Store / browsers hidden, private DNS…), so the website shows it too —
     * and so the next update from the website doesn't put the old value back.
     */
    fun record(context: Context, key: String, value: Any) {
        _policies.value = _policies.value + (key to value)
        write(context, mapOf("policies.$key" to value))
    }

    /** Block/unblock one app from the app's Apps screen; mirrors the website's per-app switch. */
    fun setAppBlocked(context: Context, pkg: String, blocked: Boolean) {
        val current = (_policies.value["blockedPackages"] as? List<*>)?.filterIsInstance<String>().orEmpty()
        val next = if (blocked) (current + pkg).distinct() else current - pkg
        applyLocally(context, _policies.value + ("blockedPackages" to next), "blockedPackages")
        write(context, mapOf("policies.blockedPackages" to
            if (blocked) FieldValue.arrayUnion(pkg) else FieldValue.arrayRemove(pkg)))
    }

    /** Adds/removes one entry in a list policy (blocked sections, WebView allow-list). */
    fun setListItem(context: Context, key: String, item: String, present: Boolean) {
        val isSection = key == OrgRules.KEY_BLOCKED_SECTIONS
        if (isSection && refuseLocked(context, OrgRules.sectionKey(item))) return
        val current = (_policies.value[key] as? List<*>)?.filterIsInstance<String>().orEmpty()
        val next = if (present) (current + item).distinct() else current - item
        applyLocally(context, _policies.value + (key to next), key)
        val fields = mutableMapOf<String, Any>("policies.$key" to
            if (present) FieldValue.arrayUnion(item) else FieldValue.arrayRemove(item))
        // Same as the website: "turned off on purpose", so an org's default block stops applying.
        if (isSection) fields["policies.${OrgRules.KEY_SECTIONS_ALLOWED}"] =
            if (present) FieldValue.arrayRemove(item) else FieldValue.arrayUnion(item)
        write(context, fields)
    }

    /** True (and tells the user) when the phone's organization has locked [key]. */
    private fun refuseLocked(context: Context, key: String): Boolean {
        if (!OrgState.isLocked(key)) return false
        Log.i(TAG, "$key: ${OrgState.lockedMessage()}")
        Toast.makeText(context, OrgState.lockedMessage(), Toast.LENGTH_SHORT).show()
        _policies.value = _policies.value.toMap()   // re-emit so the switch springs back
        return true
    }

    /** Daily allowance for one app, in minutes; 0 removes the limit. */
    fun setTimeLimit(context: Context, pkg: String, minutes: Int) {
        val current = (_policies.value[AppTimeLimits.KEY_LIMITS] as? Map<*, *>)
            ?.mapNotNull { (k, v) -> (k as? String)?.let { it to ((v as? Number)?.toInt() ?: 0) } }
            ?.toMap().orEmpty()
        val next = if (minutes > 0) current + (pkg to minutes) else current - pkg
        applyLocally(context, _policies.value + (AppTimeLimits.KEY_LIMITS to next), AppTimeLimits.KEY_LIMITS)
        write(context, mapOf("policies.${AppTimeLimits.KEY_LIMITS}.$pkg" to minutes))
    }

    fun clearBlockedApps(context: Context) {
        applyLocally(context, _policies.value + ("blockedPackages" to emptyList<String>()), "blockedPackages")
        write(context, mapOf("policies.blockedPackages" to emptyList<String>()))
    }

    fun rename(context: Context, name: String) {
        val clean = name.trim().take(60)
        _deviceName.value = clean
        write(context, mapOf("name" to clean))
    }

    /** Deletes this device from the account, then signs out (same as removing it on the website). */
    fun removeFromAccount(context: Context, onError: (String) -> Unit) {
        Firebase.firestore.collection("devices").document(EnrollmentManager.getHardwareId(context))
            .delete()
            .addOnSuccessListener { DeviceRemoval.handle(context) }
            .addOnFailureListener { onError(it.message ?: "Couldn't remove device") }
    }

    private fun applyLocally(context: Context, merged: Map<String, Any>, changedKey: String) {
        _policies.value = merged
        when (changedKey) {
            "remoteOnly"      -> RemoteModeState.set(context, merged["remoteOnly"] as? Boolean ?: false)
            "webviewDisabled" -> DevicePolicyHelper.getInstance(context)
                .setWebViewDisabled(merged["webviewDisabled"] as? Boolean ?: false)
            // Screen-guard rules: sections and the WebView allow-list
            ContentRules.KEY_BLOCKED_SECTIONS,
            ContentRules.KEY_WEBVIEW_GUARD,
            ContentRules.KEY_WEBVIEW_ALLOWED -> GuardPolicy.apply(context, merged)
            AppTimeLimits.KEY_LIMITS -> AppTimeLimits.enforce(context, merged)
            // "Only chosen apps can install apps"
            InstallGuard.KEY_ON, InstallGuard.KEY_ALLOWED -> InstallGuard.apply(context, merged)
            in LocalSettingsSync.KEYS -> LocalSettingsSync.apply(context, merged)
            else              -> AppBlocker.apply(context, merged)
        }
    }

    private fun write(context: Context, fields: Map<String, Any>) {
        if (Firebase.auth.currentUser == null) return
        // update() queues offline and syncs later, so app-side changes reach the website eventually.
        Firebase.firestore.collection("devices").document(EnrollmentManager.getHardwareId(context))
            .update(fields)
            .addOnFailureListener { Log.w(TAG, "Sync to website failed: ${it.message}") }
    }

    private fun fromJson(o: JSONObject): Map<String, Any> = o.keys().asSequence().associateWith { k ->
        when (val v = o.get(k)) {
            is JSONArray -> (0 until v.length()).map { v.get(it) }
            else -> v
        }
    }
}
