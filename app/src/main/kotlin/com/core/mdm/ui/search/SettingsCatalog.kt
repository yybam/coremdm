package com.core.mdm.ui.search

import com.core.mdm.ui.dashboard.DashboardUiState

/**
 * Every setting in the app in one list, so the search screen can find anything without each
 * card having to register itself. [keywords] carries the words people actually search for —
 * the old Android constant names, and plain synonyms like "wifi" for "Wi-Fi".
 */
object SettingsCatalog {

    enum class Kind {
        /** A restriction held by Android, toggled through DashboardViewModel. */
        LOCAL,
        /** A policy shared with the website, toggled through RemotePolicies. */
        REMOTE,
        /** Opens another screen rather than flipping a switch. */
        SCREEN,
    }

    data class Entry(
        val key: String,
        val title: String,
        val subtitle: String,
        val category: String,
        val kind: Kind,
        val keywords: String = "",
        val route: String? = null,
    )

    val ENTRIES: List<Entry> = listOf(
        // ── Blocking & access (website-synced) ────────────────────────────────
        e("whatsappBlocked", "Block WhatsApp", "Pauses WhatsApp and blocks whatsapp.com", "Blocking & Access", Kind.REMOTE, "whatsapp chat messaging"),
        e("socialMediaBlocked", "Block Social Media", "Instagram, Facebook, TikTok, Snapchat, X, YouTube, Telegram…", "Blocking & Access", Kind.REMOTE, "instagram facebook tiktok snapchat twitter youtube telegram discord reddit social"),
        e("aiChatsBlocked", "Block AI Chats", "ChatGPT, Claude, Gemini, Copilot, Perplexity, Grok…", "Blocking & Access", Kind.REMOTE, "chatgpt claude gemini copilot perplexity grok bard ai chatbot"),
        e("webviewDisabled", "Block all in-app browsers", "Stops web pages inside every app", "Blocking & Access", Kind.REMOTE, "webview web browser in-app internet"),
        e("remoteOnly", "Remote-Only Mode", "Settings can only be changed from the website", "Blocking & Access", Kind.REMOTE, "remote lock website only kiosk"),

        // ── Phone & communication ─────────────────────────────────────────────
        e("appsControlBlocked", "Lock App Settings", "Stops uninstalling, force-stopping or clearing app data", "Phone & communication", Kind.REMOTE, "uninstall force stop clear data cache apps control settings"),
        e("allCallsBlocked", "Block All Calls", "No calls can be made and the dialler is hidden", "Phone & communication", Kind.REMOTE, "call phone dialler dialer telephone"),
        e("smsBlocked", "Block SMS & MMS", "No texts can be sent and messaging apps are paused", "Phone & communication", Kind.REMOTE, "sms mms text message texting"),
        e("hotspotBlocked", "Block Hotspot", "Stops sharing this phone's internet", "Phone & communication", Kind.REMOTE, "hotspot tether tethering share internet"),

        // ── Installing apps ───────────────────────────────────────────────────
        e("installAppsBlocked", "Block App Installation", "Nothing new can be installed from the Play Store", "Installing apps", Kind.LOCAL, "install play store DISALLOW_INSTALL_APPS"),
        e("uninstallAppsBlocked", "Block App Uninstallation", "Apps already on the phone can't be removed", "Installing apps", Kind.LOCAL, "uninstall remove delete DISALLOW_UNINSTALL_APPS"),
        e("unknownSourcesBlocked", "Block Sideloading", "Blocks installing apps from files or websites", "Installing apps", Kind.LOCAL, "sideload apk unknown sources DISALLOW_INSTALL_UNKNOWN_SOURCES"),
        e("playStoreHidden", "Hide Play Store", "Takes the Play Store off the home screen", "Installing apps", Kind.LOCAL, "play store google hide"),
        e("browsersHidden", "Hide Browsers", "Takes Chrome, Firefox and other browsers off the home screen", "Installing apps", Kind.LOCAL, "chrome firefox browser hide internet"),

        // ── Anti-bypass ───────────────────────────────────────────────────────
        e("safeBootBlocked", "Prevent Safe Boot", "Stops restarting into safe mode to get around the rules", "Stop the rules being bypassed", Kind.LOCAL, "safe boot recovery DISALLOW_SAFE_BOOT"),
        e("factoryResetBlocked", "Prevent Factory Reset", "Stops a reset from Settings wiping the phone", "Stop the rules being bypassed", Kind.LOCAL, "factory reset wipe erase DISALLOW_FACTORY_RESET"),
        e("debuggingBlocked", "Disable Developer Options & ADB", "Stops the phone being controlled over a USB cable", "Stop the rules being bypassed", Kind.LOCAL, "developer adb usb debugging DISALLOW_DEBUGGING_FEATURES"),
        e("addUserBlocked", "Prevent Multi-User Profiles", "Only one user account on this phone", "Stop the rules being bypassed", Kind.LOCAL, "user profile multi account DISALLOW_ADD_USER"),
        e("userSwitchBlocked", "Block User Switching", "Stops switching to another user account", "Stop the rules being bypassed", Kind.LOCAL, "switch user account DISALLOW_USER_SWITCH"),
        e("screenCaptureDisabled", "Disable Screen Capture", "No screenshots and no screen recording", "Stop the rules being bypassed", Kind.LOCAL, "screenshot screen record capture"),
        e("cameraDisabled", "Disable Camera", "Turns the camera off for every app", "Stop the rules being bypassed", Kind.LOCAL, "camera photo video picture"),

        // ── Phone settings ────────────────────────────────────────────────────
        e("wifiConfigBlocked", "Lock Wi-Fi Configuration", "Wi-Fi networks can't be added or changed", "Phone settings", Kind.LOCAL, "wifi wi-fi network DISALLOW_CONFIG_WIFI"),
        e("mobileNetworksBlocked", "Lock Cellular Data Settings", "Mobile data settings can't be changed", "Phone settings", Kind.LOCAL, "mobile data cellular apn carrier sim"),
        e("bluetoothConfigBlocked", "Lock Bluetooth Settings", "No new Bluetooth devices can be paired", "Phone settings", Kind.LOCAL, "bluetooth pair pairing"),
        e("vpnBlocked", "Block VPN Configuration", "No VPN can be set up on the phone", "Phone settings", Kind.LOCAL, "vpn proxy tunnel"),
        e("statusBarDisabled", "Lock Status Bar", "The bar you pull down from the top stops working", "Phone settings", Kind.LOCAL, "status bar notification shade quick settings pull down"),
        e("networkResetBlocked", "Block Network Reset", "Stops resetting all network settings at once", "Phone settings", Kind.LOCAL, "network reset DISALLOW_NETWORK_RESET"),

        // ── Hardware ──────────────────────────────────────────────────────────
        e("usbTransferBlocked", "Block USB File Transfer", "No files can be copied over a USB cable", "Hardware", Kind.LOCAL, "usb file transfer mtp cable"),
        e("bluetoothDisabled", "Disable Bluetooth", "Turns Bluetooth off completely", "Hardware", Kind.LOCAL, "bluetooth off disable"),
        e("outgoingCallsBlocked", "Block Outgoing Calls", "No calls can be made from this phone", "Hardware", Kind.LOCAL, "call outgoing phone dial"),
        e("physicalMediaBlocked", "Block Physical Media", "SD cards and USB sticks won't work", "Hardware", Kind.LOCAL, "sd card storage usb stick media"),
        e("mdmUninstallProtected", "Protect MDM from Uninstall", "CORE MDM itself can't be uninstalled", "Hardware", Kind.LOCAL, "protect uninstall coremdm mdm self"),

        // ── Screens ───────────────────────────────────────────────────────────
        e("screen_content", "Block parts of apps", "WhatsApp Updates, Status, Channels, Reels, Shorts, Stories", "Screens", Kind.SCREEN, "whatsapp status channels updates reels shorts stories explore spotlight section webview per-app web", route = "content_rules"),
        e("screen_limits", "Time Limits", "How long an app may be used each day", "Screens", Kind.SCREEN, "time limit daily minutes usage screen time allowance", route = "time_limits"),
        e("screen_apps", "Managed Apps", "See every app and hide, pause or block it", "Screens", Kind.SCREEN, "apps manage hide pause block suspend list", route = "apps"),
        e("screen_filter", "Content Filter", "Block websites across all apps", "Screens", Kind.SCREEN, "filter dns website block content adult", route = "filter"),
        e("screen_org", "Organization", "Join an organization with a code", "Screens", Kind.SCREEN, "organization org join code charity school company group", route = "org"),
        e("screen_kiosk", "Single-app mode", "Lock the phone to one or a few apps", "Screens", Kind.SCREEN, "kiosk lock task single app mode", route = "kiosk"),
        e("screen_telemetry", "Phone details", "Battery, storage and network", "Screens", Kind.SCREEN, "battery storage network details diagnostics info", route = "telemetry"),
        e("screen_policies", "Device Policies", "Blocking, restrictions and anti-bypass controls", "Screens", Kind.SCREEN, "policies restrictions block whatsapp social ai calls sms hotspot install", route = "policies"),
        e("screen_parental", "Parental Controls", "Screen time window, email and SMS", "Screens", Kind.SCREEN, "parental screen time schedule email sms kids", route = "parental"),
        e("screen_location", "Location & Tracking", "GPS tracking, geofence and usage stats", "Screens", Kind.SCREEN, "location gps tracking geofence map usage", route = "location"),
        e("screen_remote", "Remote Control", "Alarm and remote commands", "Screens", Kind.SCREEN, "remote alarm ring find command", route = "remote"),
        e("screen_pin", "App PIN Lock", "Change the PIN that protects this app", "Screens", Kind.SCREEN, "pin passcode password lock security", route = "pin_setup"),
    )

    private fun e(key: String, title: String, subtitle: String, category: String, kind: Kind,
                  keywords: String = "", route: String? = null) =
        Entry(key, title, subtitle, category, kind, keywords, route)

    fun search(query: String): List<Entry> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return emptyList()
        // Title matches first, then subtitle/keyword matches
        return ENTRIES
            .filter { "${it.title} ${it.subtitle} ${it.keywords} ${it.category}".lowercase().contains(q) }
            .sortedBy { if (it.title.lowercase().contains(q)) 0 else 1 }
    }

    /** Current value of a LOCAL entry, read from the dashboard's state. */
    fun localValue(state: DashboardUiState, key: String): Boolean = when (key) {
        "installAppsBlocked"    -> state.installAppsBlocked
        "uninstallAppsBlocked"  -> state.uninstallAppsBlocked
        "unknownSourcesBlocked" -> state.unknownSourcesBlocked
        "playStoreHidden"       -> state.playStoreHidden
        "browsersHidden"        -> state.browsersHidden
        "safeBootBlocked"       -> state.safeBootBlocked
        "factoryResetBlocked"   -> state.factoryResetBlocked
        "debuggingBlocked"      -> state.debuggingBlocked
        "addUserBlocked"        -> state.addUserBlocked
        "userSwitchBlocked"     -> state.userSwitchBlocked
        "screenCaptureDisabled" -> state.screenCaptureDisabled
        "cameraDisabled"        -> state.cameraDisabled
        "wifiConfigBlocked"     -> state.wifiConfigBlocked
        "mobileNetworksBlocked" -> state.mobileNetworksBlocked
        "bluetoothConfigBlocked"-> state.bluetoothConfigBlocked
        "vpnBlocked"            -> state.vpnBlocked
        "statusBarDisabled"     -> state.statusBarDisabled
        "networkResetBlocked"   -> state.networkResetBlocked
        "usbTransferBlocked"    -> state.usbTransferBlocked
        "bluetoothDisabled"     -> state.bluetoothDisabled
        "outgoingCallsBlocked"  -> state.outgoingCallsBlocked
        "physicalMediaBlocked"  -> state.physicalMediaBlocked
        "mdmUninstallProtected" -> state.mdmUninstallProtected
        else -> false
    }
}
