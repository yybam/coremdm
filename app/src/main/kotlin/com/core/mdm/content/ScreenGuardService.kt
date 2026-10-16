package com.core.mdm.content

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Watches the foreground screen and backs out of sections the admin blocked (WhatsApp Status,
 * Instagram Reels, …) and of in-app web pages in apps that aren't on the WebView allow-list.
 *
 * Android has no API to block part of another app, so this reads the screen's structure —
 * view ids, visible tab labels and the current activity — and presses Back when a blocked
 * section is on top. It sees only what's needed to make that decision: nothing is recorded,
 * stored or sent anywhere. Blocking a whole app still uses the MDM API and doesn't need this.
 *
 * Detection is best-effort: an app redesign can rename the ids this matches on, and a blocked
 * screen may be visible for a moment before it's dismissed.
 */
class ScreenGuardService : AccessibilityService() {

    companion object {
        private const val TAG = "ScreenGuard"
        private const val BACK_COOLDOWN_MS = 700L

        private val _isRunning = MutableStateFlow(false)
        /** True while Android has this service enabled — the UI shows setup state from this. */
        val isRunning: StateFlow<Boolean> = _isRunning

        /** Web page containers: an app is showing in-app web content when one of these is present. */
        private val WEBVIEW_CLASSES = setOf(
            "android.webkit.WebView",
            "org.chromium.content.browser.ContentView",
            "com.google.android.apps.chrome.CustomTabActivity",
        )
    }

    private var lastBackAt = 0L
    private var lastBlocked: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isRunning.value = true
        Log.i(TAG, "Screen guard connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        _isRunning.value = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        _isRunning.value = false
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        val policy = GuardPolicy.current
        if (policy.isEmpty) return

        val root = rootInActiveWindow ?: return
        try {
            val reason = findBlockReason(pkg, root, event, policy)
            if (reason != null) block(reason) else lastBlocked = null
        } catch (e: Exception) {
            Log.w(TAG, "Check failed: ${e.message}")
        }
    }

    private fun findBlockReason(
        pkg: String,
        root: AccessibilityNodeInfo,
        event: AccessibilityEvent,
        policy: GuardPolicy.Snapshot,
    ): String? {
        // 1. Blocked in-app sections for this app
        val activity = if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
            event.className?.toString() else null
        for (section in ContentRules.sectionsFor(pkg)) {
            if (section.id !in policy.blockedSections) continue
            if (activity != null && section.detector.activities.any { activity.contains(it) })
                return "${section.app} ${section.label}"
            if (section.detector.viewIds.any { root.findAccessibilityNodeInfosByViewId(it).isNotEmpty() })
                return "${section.app} ${section.label}"
        }

        // 2. In-app web pages, unless this app is allowed to show them
        if (policy.webviewGuard && pkg !in policy.webviewAllowed && containsWebView(root, 0))
            return "Web pages in this app"

        return null
    }

    private fun containsWebView(node: AccessibilityNodeInfo?, depth: Int): Boolean {
        if (node == null || depth > 12) return false
        val cls = node.className?.toString()
        if (cls != null && cls in WEBVIEW_CLASSES) return true
        for (i in 0 until node.childCount) {
            if (containsWebView(node.getChild(i), depth + 1)) return true
        }
        return false
    }

    private fun block(reason: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBackAt < BACK_COOLDOWN_MS && reason == lastBlocked) return
        lastBackAt = now
        lastBlocked = reason
        Log.i(TAG, "Blocked: $reason")
        performGlobalAction(GLOBAL_ACTION_BACK)
        GuardPolicy.notifyBlocked(applicationContext, reason)
    }
}
