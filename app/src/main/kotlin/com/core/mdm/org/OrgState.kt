package com.core.mdm.org

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.core.mdm.firebase.EnrollmentManager
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/**
 * The organization this phone belongs to (devices/{id}.orgId) and its public profile:
 * lock-screen name, message and logo, and its rules. Cached so the lock screen shows the
 * branding and locked rules hold at boot, before Firestore has synced.
 */
object OrgState {
    private const val TAG   = "OrgState"
    private const val PREFS = "mdm_org"

    data class Profile(
        val name: String = "",
        val message: String = "",
        val logo: String = "",                       // data: URL, may be empty
        val rules: Map<String, OrgRules.Rule> = emptyMap(),
    )

    private val _orgId = MutableStateFlow<String?>(null)
    val orgId: StateFlow<String?> = _orgId

    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile

    private var listener: ListenerRegistration? = null
    private var listeningTo: String? = null
    private var started = false
    private var logoCache: Pair<String, Bitmap?>? = null
    private var autoJoinTried = false

    fun init(context: Context) {
        val p = prefs(context)
        _orgId.value = p.getString("orgId", null)
        _profile.value = if (_orgId.value == null) null else Profile(
            name    = p.getString("name", "") ?: "",
            message = p.getString("message", "") ?: "",
            logo    = p.getString("logo", "") ?: "",
            rules   = runCatching { rulesFromJson(JSONObject(p.getString("rules", "{}") ?: "{}")) }.getOrDefault(emptyMap()),
        )
    }

    fun rules(): Map<String, OrgRules.Rule> = _profile.value?.rules.orEmpty()
    fun isLocked(key: String) = OrgRules.isLocked(rules(), key)
    fun name(): String = _profile.value?.name?.takeIf { it.isNotBlank() } ?: "your organization"
    fun lockedMessage() = "Locked by ${name()}"

    /** The logo as a bitmap, decoded once per logo. */
    fun logoBitmap(): Bitmap? {
        val url = _profile.value?.logo.orEmpty()
        if (url.isEmpty()) return null
        logoCache?.let { (u, b) -> if (u == url) return b }
        val bmp = runCatching {
            val bytes = Base64.decode(url.substringAfter("base64,"), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
        logoCache = url to bmp
        return bmp
    }

    /**
     * Follows the org named on the device record. [onChange] runs whenever the org or its
     * profile changes, so the caller can re-merge the rules into the phone's settings.
     */
    fun watch(context: Context, newOrgId: String?, onChange: () -> Unit) {
        if (started && newOrgId == listeningTo) return
        // The first time round, compare with the cached org: it may have changed while offline.
        val previous = if (started) listeningTo else _orgId.value
        started = true
        listener?.remove()
        listener = null
        listeningTo = newOrgId
        _orgId.value = newOrgId

        if (newOrgId == null) {
            // An org admin took this phone out — don't auto-join it straight back.
            if (previous != null) prefs(context).edit().putString("removedFrom", previous).apply()
            _profile.value = null
            save(context)
            Log.i(TAG, "Not in an organization")
            onChange()
            return
        }
        listener = Firebase.firestore.collection("organizations").document(newOrgId)
            .collection("public").document("profile")
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) { Log.w(TAG, "Org profile: ${err?.message}"); return@addSnapshotListener }
                _profile.value = Profile(
                    name    = snap.getString("name").orEmpty(),
                    message = snap.getString("message").orEmpty(),
                    logo    = snap.getString("logo").orEmpty(),
                    rules   = OrgRules.parse(snap.get("rules") as? Map<*, *>),
                )
                save(context)
                Log.i(TAG, "Organization profile updated (${rules().size} rules)")
                onChange()
            }
    }

    /**
     * Phones signed into the account that owns an organization join it automatically, once.
     * Skipped for an org this phone was removed from.
     */
    suspend fun autoJoinOwnedOrg(context: Context) {
        if (autoJoinTried || _orgId.value != null) return
        autoJoinTried = true
        val email = Firebase.auth.currentUser?.email?.lowercase() ?: return
        runCatching {
            // Rules only allow reading orgs where you're an admin, so query that way.
            val owned = Firebase.firestore.collection("organizations")
                .whereArrayContains("adminEmails", email).get().await()
                .documents.firstOrNull { it.getString("ownerEmail") == email } ?: return
            if (owned.id == prefs(context).getString("removedFrom", null)) return
            Firebase.firestore.collection("devices").document(EnrollmentManager.getHardwareId(context))
                .update("orgId", owned.id).await()
            Log.i(TAG, "Joined the organization owned by this account")
        }.onFailure { Log.w(TAG, "Auto-join skipped: ${it.message}") }
    }

    /** Looks up a join code. Returns the org id and its profile, or null if the code doesn't exist. */
    suspend fun lookUp(code: String): Pair<String, Profile>? {
        val clean = code.trim().uppercase().filter { it.isLetterOrDigit() }
        if (clean.length < 8) return null
        val joinCode = Firebase.firestore.collection("joinCodes").document(clean).get().await()
        val orgId = joinCode.getString("orgId") ?: return null
        val p = Firebase.firestore.collection("organizations").document(orgId)
            .collection("public").document("profile").get().await()
        return orgId to Profile(p.getString("name").orEmpty(), p.getString("message").orEmpty(), p.getString("logo").orEmpty())
    }

    /** Joins with a code the user confirmed. The device listener picks the org up from there. */
    suspend fun join(context: Context, orgId: String, code: String) {
        val clean = code.trim().uppercase().filter { it.isLetterOrDigit() }
        Firebase.firestore.collection("devices").document(EnrollmentManager.getHardwareId(context))
            .update(mapOf("orgId" to orgId, "joinCode" to clean)).await()
        prefs(context).edit().remove("removedFrom").apply()
    }

    private fun save(context: Context) {
        val p = _profile.value
        prefs(context).edit()
            .putString("orgId", _orgId.value)
            .putString("name", p?.name)
            .putString("message", p?.message)
            .putString("logo", p?.logo)
            .putString("rules", rulesToJson(p?.rules.orEmpty()).toString())
            .apply()
    }

    private fun rulesToJson(rules: Map<String, OrgRules.Rule>) = JSONObject().apply {
        rules.forEach { (k, r) -> put(k, JSONObject().put("mode", r.mode).put("value", r.value)) }
    }

    private fun rulesFromJson(o: JSONObject): Map<String, OrgRules.Rule> =
        o.keys().asSequence().associateWith { k ->
            val r = o.getJSONObject(k)
            OrgRules.Rule(r.getString("mode"), r.getBoolean("value"))
        }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
