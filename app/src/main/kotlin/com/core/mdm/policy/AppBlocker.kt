package com.core.mdm.policy

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.core.mdm.MdmDeviceAdmin
import com.core.mdm.vpn.BlocklistRepository

/**
 * Website-controlled app blocking: WhatsApp, social media, and any package picked from the
 * device's app list. Blocked apps are *suspended* (greyed out, can't be opened, data kept) and
 * their web domains are added to the content filter. Everything this class blocked is tracked,
 * so switching a block off restores exactly what it changed and nothing else.
 */
object AppBlocker {
    private const val TAG = "AppBlocker"
    private const val PREFS = "mdm_app_blocker"
    private const val KEY_SUSPENDED = "suspended"
    private const val KEY_DOMAINS   = "domains"

    val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")
    val WHATSAPP_DOMAINS  = listOf("whatsapp.com", "whatsapp.net", "wa.me")

    val SOCIAL_PACKAGES = listOf(
        "com.instagram.android", "com.instagram.lite", "com.instagram.barcelona",   // Instagram, Threads
        "com.facebook.katana", "com.facebook.lite", "com.facebook.orca", "com.facebook.mlite",
        "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go", // TikTok
        "com.snapchat.android",
        "com.twitter.android", "com.twitter.android.lite",                         // X
        "com.reddit.frontpage", "com.pinterest", "com.tumblr",
        "com.linkedin.android", "com.discord", "com.bereal.ft",
        "com.google.android.youtube", "com.google.android.apps.youtube.music",
        "org.telegram.messenger", "org.thunderdog.challegram", "com.viber.voip", "jp.naver.line.android",
    )
    val SOCIAL_DOMAINS = listOf(
        "instagram.com", "cdninstagram.com", "threads.net", "facebook.com", "fbcdn.net", "fb.com",
        "messenger.com", "tiktok.com", "tiktokcdn.com", "tiktokv.com", "snapchat.com", "sc-cdn.net",
        "twitter.com", "x.com", "twimg.com", "reddit.com", "redd.it", "redditmedia.com",
        "pinterest.com", "pinimg.com", "tumblr.com", "linkedin.com", "discord.com", "discord.gg",
        "discordapp.com", "bereal.com", "youtube.com", "youtu.be", "ytimg.com", "googlevideo.com",
        "telegram.org", "t.me", "viber.com", "line.me",
    )

    val AI_PACKAGES = listOf(
        "com.openai.chatgpt", "com.anthropic.claude", "com.google.android.apps.bard",
        "com.microsoft.copilot", "com.microsoft.bing", "ai.perplexity.app.android",
        "com.deepseek.chat", "xyz.grok.android", "ai.x.grok", "com.jasper.ai",
        "com.character.ai", "ai.replika.app", "com.luzia.app", "com.poe.android",
    )
    val AI_DOMAINS = listOf(
        "chatgpt.com", "chat.openai.com", "openai.com", "claude.ai", "anthropic.com",
        "gemini.google.com", "bard.google.com", "copilot.microsoft.com", "bing.com/chat",
        "perplexity.ai", "deepseek.com", "grok.com", "x.ai", "character.ai", "poe.com",
        "replika.com", "huggingface.co/chat", "you.com",
    )

    val SMS_PACKAGES = listOf(
        "com.google.android.apps.messaging", "com.android.mms", "com.samsung.android.messaging",
        "com.android.messaging", "com.textra", "com.moez.QKSMS",
    )

    /** Applies the block state described by the device's `policies` map. */
    fun apply(context: Context, policies: Map<String, Any>) {
        val whatsapp = policies["whatsappBlocked"] as? Boolean ?: false
        val social   = policies["socialMediaBlocked"] as? Boolean ?: false
        val ai       = policies["aiChatsBlocked"] as? Boolean ?: false
        val sms      = policies["smsBlocked"] as? Boolean ?: false
        // blockedPackages (this console) and suspendedApps (the older console's per-app "Blocked")
        // mean the same thing: suspended, visible but can't be opened.
        val custom   = listOf("blockedPackages", "suspendedApps")
            .flatMap { (policies[it] as? List<*>)?.filterIsInstance<String>().orEmpty() }

        val packages = buildSet {
            if (whatsapp) addAll(WHATSAPP_PACKAGES)
            if (social)   addAll(SOCIAL_PACKAGES)
            if (ai)       addAll(AI_PACKAGES)
            if (sms)      addAll(SMS_PACKAGES)
            addAll(custom)
            remove(context.packageName)   // never lock ourselves out
        }
        val domains = buildSet {
            if (whatsapp) addAll(WHATSAPP_DOMAINS)
            if (social)   addAll(SOCIAL_DOMAINS)
            if (ai)       addAll(AI_DOMAINS)
        }
        setSuspended(context, packages)
        setBlockedDomains(context, domains)
    }

    /** Re-applies the last state, e.g. after a new app was installed. */
    fun reapply(context: Context) {
        setSuspended(context, prefs(context).getStringSet(KEY_SUSPENDED, emptySet()).orEmpty(), force = true)
    }

    fun blockedPackages(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_SUSPENDED, emptySet()).orEmpty()

    private fun setSuspended(context: Context, wanted: Set<String>, force: Boolean = false) {
        val dpm   = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, MdmDeviceAdmin::class.java)
        if (!dpm.isDeviceOwnerApp(context.packageName)) return

        val prev = prefs(context).getStringSet(KEY_SUSPENDED, emptySet()).orEmpty()
        val toRelease = prev - wanted
        val toSuspend = if (force) wanted else wanted - prev
        runCatching {
            if (toRelease.isNotEmpty()) dpm.setPackagesSuspended(admin, toRelease.toTypedArray(), false)
            // Not-installed packages come back as "failed" — expected; they're caught on install.
            if (toSuspend.isNotEmpty()) dpm.setPackagesSuspended(admin, toSuspend.toTypedArray(), true)
        }.onFailure { Log.w(TAG, "setPackagesSuspended: ${it.message}") }
        prefs(context).edit().putStringSet(KEY_SUSPENDED, wanted).apply()
        Log.d(TAG, "Suspended ${wanted.size} packages (released ${toRelease.size})")
    }

    private fun setBlockedDomains(context: Context, wanted: Set<String>) {
        val repo = BlocklistRepository.getInstance(context)
        val prev = prefs(context).getStringSet(KEY_DOMAINS, emptySet()).orEmpty()
        (prev - wanted).forEach { repo.removeBlocked(it) }
        (wanted - prev).forEach { repo.addBlocked(it) }
        prefs(context).edit().putStringSet(KEY_DOMAINS, wanted).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
