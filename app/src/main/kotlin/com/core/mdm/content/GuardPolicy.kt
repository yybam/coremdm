package com.core.mdm.content

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import com.core.mdm.MdmDeviceAdmin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The rules [ScreenGuardService] enforces, plus the setup state the UI needs.
 *
 * The service runs in its own process-lifetime and can start before Firestore has synced, so
 * rules are cached in prefs and read from there at startup.
 */
object GuardPolicy {
    private const val TAG = "GuardPolicy"
    private const val PREFS = "mdm_guard"
    private const val KEY_SECTIONS = "sections"
    private const val KEY_WEBVIEW_ALLOWED = "webview_allowed"
    private const val KEY_WEBVIEW_GUARD = "webview_guard"
    private const val KEY_LAST_BLOCK = "last_block"

    data class Snapshot(
        val blockedSections: Set<String> = emptySet(),
        val webviewGuard: Boolean = false,
        val webviewAllowed: Set<String> = emptySet(),
    ) {
        val isEmpty: Boolean get() = blockedSections.isEmpty() && !webviewGuard
    }

    @Volatile var current: Snapshot = Snapshot()
        private set

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        update(
            Snapshot(
                blockedSections = p.getStringSet(KEY_SECTIONS, emptySet()).orEmpty(),
                webviewGuard    = p.getBoolean(KEY_WEBVIEW_GUARD, false),
                webviewAllowed  = p.getStringSet(KEY_WEBVIEW_ALLOWED, emptySet()).orEmpty(),
            )
        )
    }

    /** Applies the `policies` map from Firestore (or from a local change in the app). */
    fun apply(context: Context, policies: Map<String, Any>) {
        val next = Snapshot(
            blockedSections = (policies[ContentRules.KEY_BLOCKED_SECTIONS] as? List<*>)
                ?.filterIsInstance<String>()?.toSet().orEmpty(),
            webviewGuard = policies[ContentRules.KEY_WEBVIEW_GUARD] as? Boolean ?: false,
            webviewAllowed = (policies[ContentRules.KEY_WEBVIEW_ALLOWED] as? List<*>)
                ?.filterIsInstance<String>()?.toSet().orEmpty(),
        )
        if (next == current) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY_SECTIONS, next.blockedSections)
            .putBoolean(KEY_WEBVIEW_GUARD, next.webviewGuard)
            .putStringSet(KEY_WEBVIEW_ALLOWED, next.webviewAllowed)
            .apply()
        update(next)
    }

    private fun update(s: Snapshot) {
        current = s
        _state.value = s
    }

    // ── Setup state ───────────────────────────────────────────────────────────

    /** True when Android has the screen guard enabled (set in Accessibility settings). */
    fun isEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val me = ComponentName(context, ScreenGuardService::class.java).flattenToString()
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.id.equals(me, ignoreCase = true) || it.id.startsWith(context.packageName) }
    }

    /** True when rules are set that only the screen guard can enforce. */
    fun needsGuard(): Boolean = !current.isEmpty

    /**
     * Opens Android's Accessibility settings so the admin can switch the guard on. Android
     * requires this to be a deliberate, visible choice — it can't be enabled from inside the app.
     */
    fun openSetup(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            Toast.makeText(context, "Choose \"CORE MDM screen guard\", then turn it on", Toast.LENGTH_LONG).show()
        }.onFailure { Log.w(TAG, "Couldn't open accessibility settings: ${it.message}") }
    }

    /**
     * As Device Owner, permits only CoreMDM's guard as an accessibility service, so the phone's
     * user can't switch it off (or run another one) from Settings. Passing null would allow any.
     */
    fun lockToThisService(context: Context, locked: Boolean): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, MdmDeviceAdmin::class.java)
        if (!dpm.isDeviceOwnerApp(context.packageName)) return false
        return runCatching {
            dpm.setPermittedAccessibilityServices(admin, if (locked) listOf(context.packageName) else null)
        }.getOrElse { Log.w(TAG, "setPermittedAccessibilityServices: ${it.message}"); false }
    }

    fun isLockedToThisService(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, MdmDeviceAdmin::class.java)
        if (!dpm.isDeviceOwnerApp(context.packageName)) return false
        return runCatching { dpm.getPermittedAccessibilityServices(admin) == listOf(context.packageName) }
            .getOrDefault(false)
    }

    // ── Feedback ──────────────────────────────────────────────────────────────

    /** Brief on-screen note so the person isn't confused about why the screen closed. */
    fun notifyBlocked(context: Context, reason: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        // One message per block burst, not per frame
        if (now - prefs.getLong(KEY_LAST_BLOCK, 0) < 4000) return
        prefs.edit().putLong(KEY_LAST_BLOCK, now).apply()
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            runCatching { Toast.makeText(context, "$reason is blocked", Toast.LENGTH_SHORT).show() }
        }
    }
}
