package com.core.mdm.vpn

import android.content.Context
import android.content.SharedPreferences

/**
 * The one question the DNS tunnel asks: block this name or not? Checked in this order:
 *
 *  1. NEVER_BLOCK (+ the upstream DNS server) — always allowed, nothing can override it.
 *  2. The existing "Always Allowed" list.
 *  3. The grouped lists from [BlocklistSource] whose group switch is on, using the match mode.
 *  4. The existing lists (built-in, custom, imported, console-pushed) via BlocklistRepository,
 *     which keep their own subdomain matching.
 */
class DnsFilter private constructor(context: Context) {

    companion object {
        @Volatile private var INSTANCE: DnsFilter? = null
        fun getInstance(context: Context): DnsFilter =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: DnsFilter(context.applicationContext).also { INSTANCE = it }
            }

        /** Hosts the app itself and the phone need. Each entry also covers its subdomains. */
        val NEVER_BLOCK = listOf(
            // Our own site
            "coremdm.vercel.app",
            // Firebase: email sign-in, Firestore, push messages
            "identitytoolkit.googleapis.com", "securetoken.googleapis.com",
            "firestore.googleapis.com", "firebaseinstallations.googleapis.com",
            "fcm.googleapis.com", "fcmregistrations.googleapis.com", "mtalk.google.com",
            // In-app updater (GitHubReleases)
            "api.github.com", "github.com", "codeload.github.com",
            "objects.githubusercontent.com", "release-assets.githubusercontent.com",
            // Android's "is there internet?" check — blocking it marks Wi-Fi as having no internet
            "connectivitycheck.gstatic.com", "connectivitycheck.android.com", "clients3.google.com",
        )

        /**
         * Bare infrastructure domains from the hosts file that stay exact-match even in
         * subdomain mode — covering their subdomains would break huge parts of the internet
         * (Amazon-hosted apps, Cloudflare's script CDN, Spotify playback, Play Store downloads…).
         */
        val EXACT_ONLY = setOf(
            "amazonaws.com", "cloudflare.com", "spotifycdn.com", "gvt1.com", "gstatic.com",
            "apple.com", "microsoft.com", "cdn.com", "googlezip.net",
        )
    }

    private val settings = FilterSettings.getInstance(context)
    private val repo     = BlocklistRepository.getInstance(context)
    private val source: BlocklistSource = AssetBlocklistSource(context)

    private val neverBlock = DomainMatcher(NEVER_BLOCK, MatchMode.SUBDOMAIN)

    // Loaded once; the switches only change which of them go into the matcher.
    private val groups: Map<String, Set<String>> by lazy { source.load() }

    @Volatile private var matcher: DomainMatcher? = null

    // Held in a field: SharedPreferences only keeps a weak reference to listeners.
    private val settingsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> matcher = null }

    init { settings.registerListener(settingsListener) }

    /** Group name → number of entries, for the UI. */
    fun groupSizes(): Map<String, Int> = groups.mapValues { it.value.size }

    fun isBlocked(domain: String): Boolean {
        val d = DomainMatcher.normalize(domain)
        if (neverBlock.matches(d) || d == repo.upstreamDns) return false
        if (isWhitelisted(d)) return false
        if (currentMatcher().matches(d)) return true
        return repo.isDomainBlocked(d)
    }

    private fun currentMatcher(): DomainMatcher =
        matcher ?: synchronized(this) {
            matcher ?: DomainMatcher(
                groups.filterKeys { settings.isGroupEnabled(it) }.values.flatten(),
                settings.matchMode,
                EXACT_ONLY,
            ).also { matcher = it }
        }

    private fun isWhitelisted(domain: String): Boolean {
        val whitelist = repo.getWhitelist()
        if (whitelist.isEmpty()) return false
        return DomainMatcher(whitelist, MatchMode.SUBDOMAIN).matches(domain)
    }
}
