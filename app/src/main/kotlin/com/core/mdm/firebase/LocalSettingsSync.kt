package com.core.mdm.firebase

import android.content.Context
import android.os.UserManager
import android.util.Log
import com.core.mdm.org.OrgState
import com.core.mdm.policy.DevicePolicyHelper
import com.core.mdm.policy.KioskModeManager
import com.core.mdm.vpn.BlocklistRepository
import com.core.mdm.vpn.FilterSettings
import com.core.mdm.vpn.MatchMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Settings that used to live only on the phone — the content filter's details and hidden apps —
 * now also live in devices/{id}.policies so the website can show and change them.
 *
 * A key the website has never set is left alone on the phone (absence never wipes anything).
 * [backfillOnce] uploads the phone's current values for every key the database doesn't have
 * yet, so the website starts out showing what the phone really does.
 */
object LocalSettingsSync {
    private const val TAG = "LocalSettingsSync"
    private const val PREFS = "mdm_local_sync"
    private const val KEY_BACKFILLED = "backfilled_v1"
    private const val KEY_HIDDEN_BY_POLICY = "hidden_by_policy"

    // Policy keys (shared with the website, policies.js)
    const val FILTER_SUBDOMAINS = "filterSubdomains"
    const val FILTER_GROUPS_OFF = "filterGroupsOff"
    const val FILTER_BLOCKED    = "filterBlocked"
    const val FILTER_ALLOWED    = "filterAllowed"
    const val FILTER_DEFAULT    = "filterDefaultList"
    const val FILTER_DNS        = "filterDns"
    const val FILTER_LIST_URL   = "filterListUrl"
    const val HIDDEN_PACKAGES   = "hiddenPackages"

    val KEYS = setOf(FILTER_SUBDOMAINS, FILTER_GROUPS_OFF, FILTER_BLOCKED, FILTER_ALLOWED,
        FILTER_DEFAULT, FILTER_DNS, FILTER_LIST_URL, HIDDEN_PACKAGES)

    /** The bundled blocklist groups (asset names) — the website lists the same ids. */
    val FILTER_GROUPS = listOf("ads", "analytics", "social", "video", "gifs-images", "spotify-music",
        "google-apple-microsoft", "app-help-pages", "cloud-infra", "other-sites")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Applies whichever of these keys are present in [policies]. */
    fun apply(context: Context, policies: Map<String, Any>) {
        val settings = FilterSettings.getInstance(context)
        val repo = BlocklistRepository.getInstance(context)

        (policies[FILTER_SUBDOMAINS] as? Boolean)?.let {
            val mode = if (it) MatchMode.SUBDOMAIN else MatchMode.EXACT
            if (settings.matchMode != mode) settings.matchMode = mode
        }
        list(policies[FILTER_GROUPS_OFF])?.let { off ->
            FILTER_GROUPS.forEach { g ->
                val on = g !in off
                if (settings.isGroupEnabled(g) != on) settings.setGroupEnabled(g, on)
            }
        }
        list(policies[FILTER_BLOCKED])?.let { want ->
            val have = repo.getCustomBlocked()
            (want - have).forEach { repo.addBlocked(it) }
            (have - want).forEach { repo.removeBlocked(it) }
        }
        list(policies[FILTER_ALLOWED])?.let { want ->
            val have = repo.getWhitelist()
            (want - have).forEach { repo.addWhitelisted(it) }
            (have - want).forEach { repo.removeWhitelisted(it) }
        }
        (policies[FILTER_DEFAULT] as? Boolean)?.let { if (repo.useDefaultBlocklist != it) repo.useDefaultBlocklist = it }
        (policies[FILTER_DNS] as? String)?.let { dns ->
            if (dns.isNotBlank() && repo.upstreamDns != dns) repo.upstreamDns = dns.trim()
        }
        (policies[FILTER_LIST_URL] as? String)?.let { url ->
            if (url != repo.blocklistUrl) {
                repo.blocklistUrl = url
                if (url.isBlank()) repo.clearImported()
                else scope.launch {
                    repo.fetchFromUrl(url).onFailure { Log.w(TAG, "Blocklist import failed: ${it.message}") }
                }
            }
        }
        list(policies[HIDDEN_PACKAGES])?.let { applyHidden(context, it) }
    }

    /**
     * Hides the listed apps and un-hides the ones this list hid before but no longer names.
     * Apps hidden by other rules (Hide Play Store / browsers) are never touched here.
     */
    private fun applyHidden(context: Context, wanted: Set<String>) {
        val helper = DevicePolicyHelper.getInstance(context)
        if (!helper.isDeviceOwner) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val before = prefs.getStringSet(KEY_HIDDEN_BY_POLICY, emptySet()).orEmpty()
        val target = wanted - context.packageName
        target.forEach { helper.setAppHidden(it, true) }
        (before - target).forEach { helper.setAppHidden(it, false) }
        prefs.edit().putStringSet(KEY_HIDDEN_BY_POLICY, target).apply()
    }

    /**
     * Once per phone, after the first fresh server snapshot: writes the phone's current value
     * for every setting the database doesn't have (and the organization hasn't locked).
     */
    fun backfillOnce(context: Context, own: Map<String, Any>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_BACKFILLED, false)) return
        prefs.edit().putBoolean(KEY_BACKFILLED, true).apply()

        val values = currentValues(context)
        val missing = values.filterKeys { it !in own && !OrgState.isLocked(it) }
        missing.forEach { (k, v) -> RemotePolicies.record(context, k, v) }
        Log.i(TAG, "Uploaded ${missing.size} phone settings the website didn't have")
    }

    /** What the phone is doing right now, keyed like the website. */
    private fun currentValues(context: Context): Map<String, Any> {
        val helper = DevicePolicyHelper.getInstance(context)
        val r = helper.getRestrictionBundle()
        val settings = FilterSettings.getInstance(context)
        val repo = BlocklistRepository.getInstance(context)
        val out = mutableMapOf<String, Any>(
            "installAppsBlocked"     to r.getBoolean(UserManager.DISALLOW_INSTALL_APPS),
            "uninstallAppsBlocked"   to r.getBoolean(UserManager.DISALLOW_UNINSTALL_APPS),
            "unknownSourcesBlocked"  to r.getBoolean(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES),
            "safeBootBlocked"        to r.getBoolean(UserManager.DISALLOW_SAFE_BOOT),
            "factoryResetBlocked"    to r.getBoolean(UserManager.DISALLOW_FACTORY_RESET),
            "debuggingBlocked"       to r.getBoolean(UserManager.DISALLOW_DEBUGGING_FEATURES),
            "addUserBlocked"         to r.getBoolean(UserManager.DISALLOW_ADD_USER),
            "userSwitchBlocked"      to r.getBoolean(UserManager.DISALLOW_USER_SWITCH),
            "wifiConfigBlocked"      to r.getBoolean(UserManager.DISALLOW_CONFIG_WIFI),
            "mobileNetworksBlocked"  to r.getBoolean(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS),
            "bluetoothConfigBlocked" to r.getBoolean(UserManager.DISALLOW_CONFIG_BLUETOOTH),
            "vpnBlocked"             to r.getBoolean(UserManager.DISALLOW_CONFIG_VPN),
            "networkResetBlocked"    to r.getBoolean(UserManager.DISALLOW_NETWORK_RESET),
            "playStoreHidden"        to helper.isPlayStoreHidden(),
            "filterRunning"          to settings.enabled,
            FILTER_SUBDOMAINS        to (settings.matchMode == MatchMode.SUBDOMAIN),
            FILTER_GROUPS_OFF        to FILTER_GROUPS.filterNot { settings.isGroupEnabled(it) },
            FILTER_BLOCKED           to repo.getCustomBlocked().sorted(),
            FILTER_ALLOWED           to repo.getWhitelist().sorted(),
            FILTER_DEFAULT           to repo.useDefaultBlocklist,
            FILTER_DNS               to repo.upstreamDns,
            FILTER_LIST_URL          to repo.blocklistUrl,
        )
        if (helper.isDeviceOwner) {
            out["kioskPackages"] = KioskModeManager(context).getAllowedPackages()
        }
        return out
    }

    private fun list(v: Any?): Set<String>? = (v as? List<*>)?.filterIsInstance<String>()?.toSet()
}
