package com.core.mdm.vpn

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class BlocklistRepository private constructor(context: Context) {

    companion object {
        @Volatile private var INSTANCE: BlocklistRepository? = null
        fun getInstance(context: Context): BlocklistRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: BlocklistRepository(context.applicationContext).also { INSTANCE = it }
            }

        private const val KEY_CUSTOM_BLOCKED = "custom_blocked"
        private const val KEY_WHITELIST      = "whitelist"
        private const val KEY_USE_DEFAULT    = "use_default"
        private const val KEY_URL            = "blocklist_url"
        private const val KEY_UPSTREAM       = "upstream_dns"

        val DEFAULT_BLOCKED = setOf(
            // Social media
            "instagram.com", "facebook.com", "fb.com", "tiktok.com",
            "twitter.com", "x.com", "snapchat.com", "reddit.com",
            "tumblr.com", "pinterest.com", "discord.com", "telegram.org",
            // Video streaming
            "youtube.com", "youtu.be", "netflix.com", "hulu.com",
            "twitch.tv", "vimeo.com", "dailymotion.com",
            // Adult — major tubes
            "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com",
            "redtube.com", "youporn.com", "tube8.com", "spankbang.com",
            "beeg.com", "eporner.com", "drtuber.com", "fuq.com",
            "4tube.com", "porntube.com", "nuvid.com", "tnaflix.com",
            "empflix.com", "sunporno.com", "hclips.com", "hdzog.com",
            "gotporn.com", "txxx.com", "sexvid.xxx", "porndig.com",
            "pornrabbit.com", "porntrex.com", "porndoe.com", "pornjam.com",
            "pornhat.com", "lobstertube.com", "slutload.com", "youjizz.com",
            "ah-me.com", "vjav.com", "pornone.com", "sexu.com",
            "videosection.com", "analdin.com", "tubepornclassic.com",
            // Adult — studios / subscription
            "brazzers.com", "naughtyamerica.com", "bangbros.com",
            "realitykings.com", "mofos.com", "digitalplayground.com",
            "onlyfans.com", "fansly.com", "manyvids.com", "clips4sale.com",
            "adultime.com", "pornportal.com",
            // Adult — live cams
            "chaturbate.com", "myfreecams.com", "cam4.com", "stripchat.com",
            "bongacams.com", "livejasmin.com", "camsoda.com", "flirt4free.com",
            "streamate.com", "imlive.com", "jerkmate.com",
            // Adult — dating / hookup
            "ashleymadison.com", "adultfriendfinder.com", "fling.com",
            "benaughty.com", "uberhorny.com", "instabang.com",
            // Adult — image boards
            "nhentai.net", "hentaihaven.xxx", "hanime.tv", "rule34.xxx",
            "gelbooru.com", "danbooru.donmai.us", "e-hentai.org",
        )
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("mdm_blocklist", Context.MODE_PRIVATE)

    // ── Custom lists ──────────────────────────────────────────────────────────

    fun getCustomBlocked(): Set<String> =
        prefs.getStringSet(KEY_CUSTOM_BLOCKED, emptySet()) ?: emptySet()

    fun getWhitelist(): Set<String> =
        prefs.getStringSet(KEY_WHITELIST, emptySet()) ?: emptySet()

    fun addBlocked(domain: String) = updateSet(KEY_CUSTOM_BLOCKED) { it + clean(domain) }
    fun removeBlocked(domain: String) = updateSet(KEY_CUSTOM_BLOCKED) { it - clean(domain) }
    fun addWhitelisted(domain: String) = updateSet(KEY_WHITELIST) { it + clean(domain) }
    fun removeWhitelisted(domain: String) = updateSet(KEY_WHITELIST) { it - clean(domain) }

    // ── Settings ──────────────────────────────────────────────────────────────

    var useDefaultBlocklist: Boolean
        get() = prefs.getBoolean(KEY_USE_DEFAULT, true)
        set(v) { prefs.edit().putBoolean(KEY_USE_DEFAULT, v).apply() }

    var blocklistUrl: String
        get() = prefs.getString(KEY_URL, "") ?: ""
        set(v) { prefs.edit().putString(KEY_URL, v).apply() }

    var upstreamDns: String
        get() = prefs.getString(KEY_UPSTREAM, "1.1.1.1") ?: "1.1.1.1"
        set(v) { prefs.edit().putString(KEY_UPSTREAM, v).apply() }

    // ── Core check ────────────────────────────────────────────────────────────

    fun isDomainBlocked(domain: String): Boolean {
        val lower = domain.lowercase().trimEnd('.')
        val whitelist = getWhitelist()
        val allBlocked = getCustomBlocked() +
                if (useDefaultBlocklist) DEFAULT_BLOCKED else emptySet()

        // Walk up the domain hierarchy: sub.example.com → example.com → com
        val parts = lower.split(".")
        for (i in parts.indices) {
            val candidate = parts.drop(i).joinToString(".")
            if (candidate in whitelist) return false
            if (candidate in allBlocked) return true
        }
        return false
    }

    // ── Remote fetch ──────────────────────────────────────────────────────────

    suspend fun fetchFromUrl(url: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout    = 15_000
                setRequestProperty("User-Agent", "CoreMDM/1.0")
            }
            val domains = conn.inputStream.bufferedReader().readLines()
                .map { it.substringBefore('#').trim().lowercase() }
                .filter { it.isNotEmpty() && it.contains('.') && !it.contains(' ') }
                .toSet()
            updateSet(KEY_CUSTOM_BLOCKED) { it + domains }
            conn.disconnect()
            domains.size
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun clean(domain: String) =
        domain.lowercase().trim().removePrefix("www.").trimEnd('.')

    private fun updateSet(key: String, transform: (Set<String>) -> Set<String>) {
        val current = prefs.getStringSet(key, emptySet()) ?: emptySet()
        prefs.edit().putStringSet(key, transform(current)).apply()
    }

}
