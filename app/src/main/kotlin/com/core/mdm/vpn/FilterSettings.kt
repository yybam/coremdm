package com.core.mdm.vpn

import android.content.Context
import android.content.SharedPreferences

/** How a plain list entry such as `example.com` matches. `*.example.com` entries always cover subdomains. */
enum class MatchMode {
    /** `example.com` blocks example.com and every subdomain (www., m., api., …). */
    SUBDOMAIN,
    /** `example.com` blocks only example.com itself, like a hosts file. */
    EXACT,
}

/**
 * The DNS filter's on/off settings in one place: the master switch, the match mode, and one
 * switch per blocklist group. Every group is ON by default; the filter itself is OFF until it's
 * switched on in the app (or from the console).
 */
class FilterSettings private constructor(context: Context) {

    companion object {
        @Volatile private var INSTANCE: FilterSettings? = null
        fun getInstance(context: Context): FilterSettings =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: FilterSettings(context.applicationContext).also { INSTANCE = it }
            }

        private const val KEY_ENABLED    = "enabled"
        private const val KEY_MATCH_MODE = "match_mode"
        private const val GROUP_PREFIX   = "group_"

        /** Opt-in groups: off until switched on (the website lists them in filterGroupsOn). */
        val DEFAULT_OFF_GROUPS = setOf("google-maps-images")
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("mdm_dns_filter", Context.MODE_PRIVATE)

    /** Whether the filter should be running — survives reboots so BootReceiver can restart it. */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(v) { prefs.edit().putBoolean(KEY_ENABLED, v).apply() }

    var matchMode: MatchMode
        get() = runCatching { MatchMode.valueOf(prefs.getString(KEY_MATCH_MODE, null)!!) }
            .getOrDefault(MatchMode.SUBDOMAIN)
        set(v) { prefs.edit().putString(KEY_MATCH_MODE, v.name).apply() }

    /** Every group is on by default except these opt-in ones, which block more than their name suggests. */
    fun isGroupEnabled(group: String): Boolean =
        prefs.getBoolean(GROUP_PREFIX + group, group !in DEFAULT_OFF_GROUPS)

    fun setGroupEnabled(group: String, on: Boolean) {
        prefs.edit().putBoolean(GROUP_PREFIX + group, on).apply()
    }

    fun registerListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(l)
}
