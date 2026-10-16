package com.core.mdm.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.core.mdm.content.ContentRules
import com.core.mdm.content.GuardPolicy
import com.core.mdm.firebase.RemotePolicies
import com.core.mdm.policy.AppTimeLimits
import com.core.mdm.policy.DevicePolicyHelper
import com.core.mdm.ui.theme.*

/**
 * The website-synced controls, also available on the phone: remote-only mode, WhatsApp /
 * social media / WebView blocks, and a link to the per-app block list. Every switch writes to
 * the same place the website does, so both always show the same state.
 */
@Composable
fun BlockingCard(onNavigateToApps: () -> Unit, onNavigateToContent: () -> Unit = {}) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val policies by RemotePolicies.policies.collectAsState()
    var confirmRemoteOnly by remember { mutableStateOf(false) }
    var confirmWebView by remember { mutableStateOf(false) }
    var guardOn by remember { mutableStateOf(GuardPolicy.isEnabled(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { guardOn = GuardPolicy.isEnabled(context) }
    fun on(key: String) = policies[key] as? Boolean ?: false

    PolicyCard(icon = Icons.Outlined.Block, title = "Blocking & Access", iconColor = c.red) {
        // ── Parts of apps + web pages in apps (screen guard) ──────────────────
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onNavigateToContent)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Tune, null, tint = c.purple, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Block parts of apps", fontWeight = FontWeight.SemiBold, color = c.textPrimary, fontSize = 14.sp)
                val blocked = (policies[ContentRules.KEY_BLOCKED_SECTIONS] as? List<*>)?.size ?: 0
                val wvOn = on(ContentRules.KEY_WEBVIEW_GUARD)
                Text(
                    buildString {
                        append(if (blocked > 0) "$blocked section${if (blocked == 1) "" else "s"} blocked" else "WhatsApp Status, Reels, Shorts…")
                        if (wvOn) append(" · web pages limited")
                    },
                    color = c.textSecondary, fontSize = 11.sp
                )
            }
            Icon(Icons.Filled.ChevronRight, null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
        }
        PolicyDivider()
        // ── Screen guard state ────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(if (guardOn) Icons.Outlined.VerifiedUser else Icons.Outlined.GppMaybe, null,
                tint = if (guardOn) c.green else c.yellow, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Screen guard", fontWeight = FontWeight.SemiBold,
                    color = if (guardOn) c.green else c.yellow, fontSize = 14.sp)
                Text(if (guardOn) "On — app-section blocks are working"
                     else "Off — app-section blocks don't work until it's on",
                    color = c.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)
            }
            if (!guardOn) {
                Spacer(Modifier.width(8.dp))
                GradientButton(onClick = { GuardPolicy.openSetup(context) }, shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)) {
                    Text("Turn on", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
        PolicyDivider()
        PolicyToggleRow(
            icon = Icons.Outlined.SmartToy,
            title = "Block AI Chats",
            subtitle = "ChatGPT, Claude, Gemini, Copilot, Perplexity, Grok and more — apps paused, sites blocked",
            checked = on("aiChatsBlocked"),
            onCheckedChange = { RemotePolicies.set(context, "aiChatsBlocked", it) }
        )
        PolicyDivider()
        PolicyToggleRow(
            icon = Icons.Outlined.Forum,
            title = "Block WhatsApp",
            subtitle = "Pauses WhatsApp & WhatsApp Business and blocks whatsapp.com",
            checked = on("whatsappBlocked"),
            onCheckedChange = { RemotePolicies.set(context, "whatsappBlocked", it) }
        )
        PolicyDivider()
        PolicyToggleRow(
            icon = Icons.Outlined.Groups,
            title = "Block Social Media",
            subtitle = "Instagram, Facebook, TikTok, Snapchat, X, YouTube, Telegram… apps paused, sites blocked",
            checked = on("socialMediaBlocked"),
            onCheckedChange = { RemotePolicies.set(context, "socialMediaBlocked", it) }
        )
        PolicyDivider()
        PolicyToggleRow(
            icon = Icons.Outlined.WebAssetOff,
            title = "Block WebView",
            subtitle = "Stops web pages inside every app — can't be limited to some apps",
            checked = on("webviewDisabled"),
            onCheckedChange = { if (it) confirmWebView = true else RemotePolicies.set(context, "webviewDisabled", false) }
        )
        PolicyDivider()
        PolicyToggleRow(
            icon = Icons.Outlined.CloudSync,
            title = "Remote-Only Mode",
            subtitle = "Lock this app — settings can only be changed from the website",
            checked = on("remoteOnly"),
            onCheckedChange = { if (it) confirmRemoteOnly = true }
        )
        PolicyDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Apps, null, tint = c.cyan, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Managed Apps", fontWeight = FontWeight.SemiBold, color = c.textPrimary, fontSize = 14.sp)
                val count = (policies["blockedPackages"] as? List<*>)?.size ?: 0
                Text(if (count > 0) "$count app${if (count == 1) "" else "s"} blocked — see all apps" else "See every app and block any of them",
                    color = c.textSecondary, fontSize = 11.sp)
            }
            TextButton(onClick = onNavigateToApps) { Text("Open", color = c.cyan, fontWeight = FontWeight.SemiBold) }
        }
    }

    if (confirmRemoteOnly) {
        AlertDialog(
            onDismissRequest = { confirmRemoteOnly = false },
            containerColor = c.panel,
            icon = { Icon(Icons.Outlined.CloudSync, null, tint = c.cyan) },
            title = { Text("Turn on remote-only mode?", color = c.textPrimary) },
            text = {
                Text(
                    "This app will lock to a \"Managed remotely\" screen. Settings can then only be " +
                        "changed from the CORE MDM website — including turning this mode off.",
                    color = c.textSecondary
                )
            },
            confirmButton = {
                GradientButton(onClick = {
                    confirmRemoteOnly = false
                    RemotePolicies.set(context, "remoteOnly", true)
                }, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)) {
                    Text("Turn on", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemoteOnly = false }) { Text("Cancel", color = c.cyan) } }
        )
    }
    if (confirmWebView) {
        AlertDialog(
            onDismissRequest = { confirmWebView = false },
            containerColor = c.panel,
            icon = { Icon(Icons.Outlined.WebAssetOff, null, tint = c.yellow) },
            title = { Text("Block WebView for every app?", color = c.textPrimary) },
            text = {
                Text(
                    "Android can't allow WebView for only some apps, so this turns it off everywhere. " +
                        "Some apps (banking, mail, help pages) may show blank screens.",
                    color = c.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { confirmWebView = false; RemotePolicies.set(context, "webviewDisabled", true) },
                    colors = ButtonDefaults.buttonColors(containerColor = c.yellow)
                ) { Text("Block WebView", color = Color(0xFF231400), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmWebView = false }) { Text("Cancel", color = c.cyan) } }
        )
    }
}

/**
 * Restrictions that keep the phone's own Settings from being used to undo the MDM, plus the
 * communication switches (calls, SMS, hotspot).
 */
@Composable
fun PhoneControlCard(onNavigateToLimits: () -> Unit) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val policies by RemotePolicies.policies.collectAsState()
    val limitCount = (policies[AppTimeLimits.KEY_LIMITS] as? Map<*, *>)?.count { (_, v) -> ((v as? Number)?.toInt() ?: 0) > 0 } ?: 0
    fun on(key: String) = policies[key] as? Boolean ?: false
    val onCount = listOf("appsControlBlocked", "allCallsBlocked", "smsBlocked", "hotspotBlocked").count { on(it) }

    PolicyCard(
        icon = Icons.Outlined.PhonelinkLock,
        title = "Phone & communication",
        iconColor = c.orange,
        summary = countSummary(onCount, 4),
        collapsible = true
    ) {
        PolicyToggleRow(
            icon = Icons.Outlined.Lock,
            title = "Lock App Settings",
            subtitle = "Stops uninstalling, force-stopping or clearing an app's data from Settings",
            checked = on("appsControlBlocked"),
            onCheckedChange = { RemotePolicies.set(context, "appsControlBlocked", it) }
        )
        PolicyDivider()
        PolicyToggleRow(
            icon = Icons.Outlined.PhoneDisabled,
            title = "Block All Calls",
            subtitle = "No calls can be made and the dialler is hidden. Emergency calls always still work.",
            checked = on("allCallsBlocked"),
            onCheckedChange = { RemotePolicies.set(context, "allCallsBlocked", it) }
        )
        PolicyDivider()
        PolicyToggleRow(
            icon = Icons.Outlined.SpeakerNotesOff,
            title = "Block SMS & MMS",
            subtitle = "No texts can be sent and messaging apps are paused. Incoming texts still arrive.",
            checked = on("smsBlocked"),
            onCheckedChange = { RemotePolicies.set(context, "smsBlocked", it) }
        )
        PolicyDivider()
        PolicyToggleRow(
            icon = Icons.Outlined.WifiTethering,
            title = "Block Hotspot",
            subtitle = "Stops sharing this phone's internet with other devices",
            checked = on("hotspotBlocked"),
            onCheckedChange = { RemotePolicies.set(context, "hotspotBlocked", it) }
        )
        PolicyDivider()
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onNavigateToLimits)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Timer, null, tint = c.accent2, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Time Limits", fontWeight = FontWeight.SemiBold, color = c.textPrimary, fontSize = 14.sp)
                Text(if (limitCount > 0) "$limitCount app${if (limitCount == 1) "" else "s"} limited"
                     else "Set how long an app may be used each day",
                    color = c.textSecondary, fontSize = 11.sp)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

/** Device name (rename) and removal from the account — same as the website's controls. */
@Composable
fun ThisDeviceCard() {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val name by RemotePolicies.deviceName.collectAsState()
    val model = remember { DevicePolicyHelper.getInstance(context).deviceModel }
    var renaming by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    PolicyCard(icon = Icons.Outlined.PhoneAndroid, title = "This Device", iconColor = c.cyan) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Badge, null, tint = c.cyan, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name.ifBlank { model }, fontWeight = FontWeight.SemiBold, color = c.textPrimary, fontSize = 14.sp)
                Text(if (name.isBlank()) "No name set — shown as the model on the website" else model,
                    color = c.textSecondary, fontSize = 11.sp)
            }
            TextButton(onClick = { renaming = true }) { Text("Rename", color = c.cyan, fontWeight = FontWeight.SemiBold) }
        }
        PolicyDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.PersonRemove, null, tint = c.red, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Remove from Account", fontWeight = FontWeight.SemiBold, color = c.red, fontSize = 14.sp)
                Text(error ?: "Deletes this phone from your devices and signs out",
                    color = if (error != null) c.red else c.textSecondary, fontSize = 11.sp)
            }
            Button(
                onClick = { confirmRemove = true },
                colors = ButtonDefaults.buttonColors(containerColor = c.red.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Remove", color = c.red, fontWeight = FontWeight.Bold) }
        }
    }

    if (renaming) {
        var text by remember { mutableStateOf(name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            containerColor = c.panel,
            title = { Text("Rename device", color = c.textPrimary) },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(60) }, singleLine = true,
                    placeholder = { Text(model, color = c.textSecondary) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.accent, unfocusedBorderColor = c.cardBorder,
                        focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary, cursorColor = c.accent
                    )
                )
            },
            confirmButton = {
                GradientButton(onClick = { RemotePolicies.rename(context, text); renaming = false },
                    shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)) {
                    Text("Save", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel", color = c.cyan) } }
        )
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            containerColor = c.panel,
            icon = { Icon(Icons.Outlined.PersonRemove, null, tint = c.red) },
            title = { Text("Remove this device?", color = c.red) },
            text = {
                Text(
                    "This phone will be deleted from your account and signed out. Restrictions already " +
                        "on the phone stay until it's set up again. Sign in again to add it back.",
                    color = c.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmRemove = false
                        RemotePolicies.removeFromAccount(context) { error = it }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = c.red)
                ) { Text("Remove", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel", color = c.cyan) } }
        )
    }
}

/**
 * "Only chosen apps can install apps", plus the list of those apps. Synced with the website like
 * the other switches in this file.
 */
@Composable
fun AppInstallCard() {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val policies by RemotePolicies.policies.collectAsState()
    val allowlistOn = policies[com.core.mdm.policy.InstallGuard.KEY_ON] as? Boolean ?: false
    val allowed = (policies[com.core.mdm.policy.InstallGuard.KEY_ALLOWED] as? List<*>)
        ?.filterIsInstance<String>()?.toSet().orEmpty()
    var picking by remember { mutableStateOf(false) }
    var confirmAllowlist by remember { mutableStateOf(false) }

    PolicyCard(
        icon = Icons.Outlined.GppMaybe,
        title = "Who can install apps",
        iconColor = c.green,
        summary = if (allowlistOn) "Only chosen apps" else "Any app",
        collapsible = true
    ) {
        PolicyToggleRow(
            icon = Icons.Outlined.GppMaybe,
            title = "Only chosen apps can install apps",
            subtitle = "Apps installed by any other app (stores, file managers, browsers) are removed straight away. CORE MDM is always allowed.",
            checked = allowlistOn,
            onCheckedChange = { if (it) confirmAllowlist = true else RemotePolicies.set(context, com.core.mdm.policy.InstallGuard.KEY_ON, false) }
        )
        PolicyDivider()
        Row(
            modifier = Modifier.fillMaxWidth().clickable { picking = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Checklist, null, tint = c.cyan, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Apps allowed to install", fontWeight = FontWeight.SemiBold, color = c.textPrimary, fontSize = 14.sp)
                Text(if (allowed.isEmpty()) "None chosen — only CORE MDM can install"
                     else "${allowed.size} app${if (allowed.size == 1) "" else "s"} chosen",
                    color = c.textSecondary, fontSize = 11.sp)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
        }
    }

    if (confirmAllowlist) {
        AlertDialog(
            onDismissRequest = { confirmAllowlist = false },
            containerColor = c.panel,
            title = { Text("Only chosen apps can install?", color = c.textPrimary) },
            text = {
                Text(
                    "From now on, any app installed by an app that isn't on your list is removed straight away. " +
                    "Apps already on the phone stay. " +
                    (if (allowed.isEmpty()) "Your list is empty, so even the Play Store won't be able to add apps." else ""),
                    color = c.textSecondary, fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmAllowlist = false
                    RemotePolicies.set(context, com.core.mdm.policy.InstallGuard.KEY_ON, true)
                }) { Text("Turn on", color = c.cyan, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmAllowlist = false }) { Text("Cancel", color = c.textSecondary) } }
        )
    }
    if (picking) InstallerPicker(allowed = allowed, onClose = { picking = false })
}

/** Apps that can install other apps (plus any already chosen); a switch shows every app. */
@Composable
private fun InstallerPicker(allowed: Set<String>, onClose: () -> Unit) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    var showAll by remember { mutableStateOf(false) }
    val apps by produceState(initialValue = emptyList<Triple<String, String, Boolean>>(), showAll) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(android.content.pm.PackageManager.GET_PERMISSIONS)
                .filter { it.packageName != context.packageName }
                .map { p ->
                    val canInstall = p.requestedPermissions.orEmpty().any {
                        it == "android.permission.REQUEST_INSTALL_PACKAGES" || it == "android.permission.INSTALL_PACKAGES"
                    }
                    Triple(p.packageName, p.applicationInfo?.loadLabel(pm)?.toString() ?: p.packageName, canInstall)
                }
                .filter { showAll || it.third || it.first in allowed }
                .sortedWith(compareByDescending<Triple<String, String, Boolean>> { it.first in allowed }.thenBy { it.second.lowercase() })
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = c.panel,
        title = { Text("Apps allowed to install", color = c.textPrimary) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show every app", color = c.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    GlassSwitch(checked = showAll, onCheckedChange = { showAll = it })
                }
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(apps.size) { i ->
                        val (pkg, label, _) = apps[i]
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(label, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(pkg, color = c.textSecondary, fontSize = 10.sp)
                            }
                            GlassSwitch(
                                checked = pkg in allowed,
                                onCheckedChange = { RemotePolicies.setListItem(context, com.core.mdm.policy.InstallGuard.KEY_ALLOWED, pkg, it) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done", color = c.cyan, fontWeight = FontWeight.Bold) } }
    )
}
