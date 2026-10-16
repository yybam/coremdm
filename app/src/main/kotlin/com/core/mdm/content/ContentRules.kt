package com.core.mdm.content

/**
 * In-app sections the screen helper can block (WhatsApp Status, Instagram Reels, …) and the
 * per-app WebView allow-list. These go beyond what Android's MDM API can do — it can only block
 * a whole app — so they are enforced by [ScreenGuardService] watching the foreground screen.
 *
 * Keys here match the Firestore `policies` map, so the website and the app show the same thing.
 */
object ContentRules {

    /**
     * One blockable section of an app.
     *
     * @param id        policy key: `policies.sections.<id>` is true when it's blocked
     * @param detector  how the section is recognised on screen
     */
    data class Section(
        val id: String,
        val app: String,
        val label: String,
        val description: String,
        val packages: List<String>,
        val detector: Detector,
    )

    /**
     * How a section is spotted. View ids are the reliable signal (they survive translation);
     * text is a fallback for screens that don't expose an id, matched only on the tab/button
     * that opens the section.
     */
    data class Detector(
        val viewIds: List<String> = emptyList(),
        val texts: List<String> = emptyList(),
        val activities: List<String> = emptyList(),
    )

    const val WHATSAPP = "com.whatsapp"
    private val WHATSAPP_PKGS = listOf(WHATSAPP, "com.whatsapp.w4b")
    private val INSTAGRAM_PKGS = listOf("com.instagram.android", "com.instagram.lite")
    private val YOUTUBE_PKGS = listOf("com.google.android.youtube")
    private val FACEBOOK_PKGS = listOf("com.facebook.katana", "com.facebook.lite")
    private val SNAPCHAT_PKGS = listOf("com.snapchat.android")
    private val TIKTOK_PKGS = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")

    val SECTIONS = listOf(
        Section(
            id = "whatsappUpdates", app = "WhatsApp", label = "Updates tab (Status + Channels)",
            description = "The whole Updates tab at once — chats and calls keep working",
            packages = WHATSAPP_PKGS,
            detector = Detector(
                // Current WhatsApp merges Status and Channels into one "Updates" tab, so this
                // matches the tab container as well as either feed inside it.
                viewIds = listOf(
                    "com.whatsapp:id/updates_fragment", "com.whatsapp:id/tab_updates",
                    "com.whatsapp:id/status_tab_layout", "com.whatsapp:id/status_list",
                    "com.whatsapp:id/status_pager", "com.whatsapp:id/statusesfragment",
                    "com.whatsapp:id/newsletter_list", "com.whatsapp:id/channels_tab",
                    "com.whatsapp:id/newsletter_directory_list",
                ),
                texts = listOf("Updates"),
                activities = listOf(
                    "com.whatsapp.status.playback.StatusPlaybackActivity",
                    "com.whatsapp.status.posting.TextStatusComposerActivity",
                    "com.whatsapp.newsletter.ui.NewsletterDirectoryActivity",
                    "com.whatsapp.newsletter.ui.NewsletterProfileActivity",
                ),
            ),
        ),
        Section(
            id = "whatsappStatus", app = "WhatsApp", label = "Status",
            description = "The Status (stories) tab — chats and calls keep working",
            packages = WHATSAPP_PKGS,
            detector = Detector(
                viewIds = listOf("com.whatsapp:id/status_tab_layout", "com.whatsapp:id/status_list",
                    "com.whatsapp:id/status_pager", "com.whatsapp:id/statusesfragment"),
                texts = listOf("Status", "Updates"),
                activities = listOf("com.whatsapp.status.playback.StatusPlaybackActivity",
                    "com.whatsapp.status.posting.TextStatusComposerActivity"),
            ),
        ),
        Section(
            id = "whatsappChannels", app = "WhatsApp", label = "Channels",
            description = "The Channels / Updates feed — chats and calls keep working",
            packages = WHATSAPP_PKGS,
            detector = Detector(
                viewIds = listOf("com.whatsapp:id/newsletter_list", "com.whatsapp:id/channels_tab",
                    "com.whatsapp:id/newsletter_directory_list"),
                texts = listOf("Channels"),
                activities = listOf("com.whatsapp.newsletter.ui.NewsletterDirectoryActivity",
                    "com.whatsapp.newsletter.ui.NewsletterProfileActivity"),
            ),
        ),
        Section(
            id = "instagramReels", app = "Instagram", label = "Reels",
            description = "The Reels video feed — messages and posts keep working",
            packages = INSTAGRAM_PKGS,
            detector = Detector(
                viewIds = listOf("com.instagram.android:id/clips_tab", "com.instagram.android:id/clips_viewer_root",
                    "com.instagram.android:id/clips_swipe_refresh_container"),
                texts = listOf("Reels"),
            ),
        ),
        Section(
            id = "instagramStories", app = "Instagram", label = "Stories",
            description = "Stories at the top of the feed and in full screen",
            packages = INSTAGRAM_PKGS,
            detector = Detector(
                viewIds = listOf("com.instagram.android:id/reel_viewer_root", "com.instagram.android:id/reel_view_pager"),
                texts = listOf("Stories"),
            ),
        ),
        Section(
            id = "instagramExplore", app = "Instagram", label = "Explore",
            description = "The Explore / search discovery feed",
            packages = INSTAGRAM_PKGS,
            detector = Detector(
                viewIds = listOf("com.instagram.android:id/explore_tab", "com.instagram.android:id/discovery_recycler_view"),
                texts = listOf("Explore"),
            ),
        ),
        Section(
            id = "youtubeShorts", app = "YouTube", label = "Shorts",
            description = "The Shorts feed — normal videos keep working",
            packages = YOUTUBE_PKGS,
            detector = Detector(
                viewIds = listOf("com.google.android.youtube:id/reel_recycler", "com.google.android.youtube:id/reel_player_page_container",
                    "com.google.android.youtube:id/shorts_container"),
                texts = listOf("Shorts"),
            ),
        ),
        Section(
            id = "facebookReels", app = "Facebook", label = "Reels & Stories",
            description = "Facebook's Reels feed and Stories viewer",
            packages = FACEBOOK_PKGS,
            detector = Detector(
                viewIds = listOf("com.facebook.katana:id/reels_viewer_root", "com.facebook.katana:id/story_viewer_root"),
                texts = listOf("Reels"),
            ),
        ),
        Section(
            id = "snapchatStories", app = "Snapchat", label = "Stories & Spotlight",
            description = "The Stories and Spotlight feeds — chats keep working",
            packages = SNAPCHAT_PKGS,
            detector = Detector(
                viewIds = listOf("com.snapchat.android:id/spotlight_view", "com.snapchat.android:id/ngs_spotlight_icon_container",
                    "com.snapchat.android:id/stories_page"),
                texts = listOf("Spotlight", "Stories"),
            ),
        ),
        Section(
            id = "tiktokFeed", app = "TikTok", label = "For You feed",
            description = "The endless video feed — messages keep working",
            packages = TIKTOK_PKGS,
            detector = Detector(
                viewIds = listOf("com.zhiliaoapp.musically:id/main_tab_host", "com.zhiliaoapp.musically:id/viewpager"),
                texts = listOf("For You"),
            ),
        ),
    )

    fun sectionsFor(pkg: String): List<Section> = SECTIONS.filter { pkg in it.packages }

    fun byApp(): Map<String, List<Section>> = SECTIONS.groupBy { it.app }

    /** Policy key holding blocked section ids, e.g. `["whatsappStatus","youtubeShorts"]`. */
    const val KEY_BLOCKED_SECTIONS = "blockedSections"

    /** Policy key holding packages allowed to show in-app web pages. */
    const val KEY_WEBVIEW_ALLOWED = "webviewAllowedPackages"

    /** Policy key: when true, in-app web pages are blocked except for the allowed list. */
    const val KEY_WEBVIEW_GUARD = "webviewGuard"
}
