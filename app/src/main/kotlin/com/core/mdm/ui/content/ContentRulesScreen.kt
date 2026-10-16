package com.core.mdm.ui.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.core.mdm.content.ContentRules
import com.core.mdm.content.GuardPolicy
import com.core.mdm.firebase.RemotePolicies
import com.core.mdm.policy.AppInventory
import com.core.mdm.ui.theme.*

private enum class RulesTab { SECTIONS, WEBVIEW }

/**
 * Picks which parts of apps are blocked (WhatsApp Status, Reels, Shorts…) and which apps may
 * show web pages inside themselves. Both are carried out by the screen guard, so the state of
 * that is shown at the top, with a way to switch it on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentRulesScreen(onNavigateBack: () -> Unit) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val policies by RemotePolicies.policies.collectAsState()
    var tab by rememberSaveable { mutableStateOf(RulesTab.SECTIONS) }
    var guardOn by remember { mutableStateOf(GuardPolicy.isEnabled(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { guardOn = GuardPolicy.isEnabled(context) }

    val blockedSections = remember(policies) {
        (policies[ContentRules.KEY_BLOCKED_SECTIONS] as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty()
    }
    val webviewGuard = policies[ContentRules.KEY_WEBVIEW_GUARD] as? Boolean ?: false
    val webviewAllowed = remember(policies) {
        (policies[ContentRules.KEY_WEBVIEW_ALLOWED] as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty()
    }

    Scaffold(
        modifier = Modifier.siteBackground(),
        topBar = {
            TopAppBar(
                title = { Text("Block parts of apps", fontWeight = FontWeight.Bold, color = c.textPrimary, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.navyLight)
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GuardStatusCard(guardOn = guardOn, onTurnOn = { GuardPolicy.openSetup(context) })
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.card)
                        .border(1.dp, c.cardBorder, RoundedCornerShape(14.dp)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(RulesTab.SECTIONS to "App sections", RulesTab.WEBVIEW to "Web in apps").forEach { (t, label) ->
                        val sel = t == tab
                        Box(
                            Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(11.dp))
                                .then(if (sel) Modifier.background(brandGradient()) else Modifier)
                                .clickable { tab = t },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = if (sel) Color.White else c.textSecondary,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium, fontSize = 15.sp)
                        }
                    }
                }
            }

            if (tab == RulesTab.SECTIONS) {
                SectionsList(blocked = blockedSections, onToggle = { id, on ->
                    RemotePolicies.setListItem(context, ContentRules.KEY_BLOCKED_SECTIONS, id, on)
                })
            } else {
                WebViewList(
                    guardOn = webviewGuard,
                    allowed = webviewAllowed,
                    onGuard = { RemotePolicies.set(context, ContentRules.KEY_WEBVIEW_GUARD, it) },
                    onToggle = { pkg, on ->
                        RemotePolicies.setListItem(context, ContentRules.KEY_WEBVIEW_ALLOWED, pkg, on)
                    }
                )
            }
        }
    }
}

@Composable
private fun GuardStatusCard(guardOn: Boolean, onTurnOn: () -> Unit) {
    val c = LocalAppColors.current
    val tint = if (guardOn) c.green else c.yellow
    Row(
        Modifier.fillMaxWidth().clip(GlassShape).background(tint.copy(alpha = 0.1f))
            .border(1.dp, tint.copy(alpha = 0.35f), GlassShape).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (guardOn) Icons.Outlined.VerifiedUser else Icons.Outlined.GppMaybe, null,
            tint = tint, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (guardOn) "Screen guard is on" else "Screen guard is off",
                fontWeight = FontWeight.SemiBold, color = tint, fontSize = 15.sp)
            Text(
                if (guardOn) "The blocks below are being applied."
                else "Nothing below takes effect until it's switched on.",
                color = c.textSecondary, fontSize = 12.sp, lineHeight = 16.sp
            )
        }
        if (!guardOn) {
            Spacer(Modifier.width(10.dp))
            GradientButton(onClick = onTurnOn, shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                Text("Turn on", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun SectionsList(blocked: Set<String>, onToggle: (String, Boolean) -> Unit) {
    val c = LocalAppColors.current
    val groups = remember { ContentRules.byApp().toList() }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Blocks one part of an app while the rest keeps working — WhatsApp chats still " +
                    "work with Status blocked.",
                color = c.textSecondary, fontSize = 13.sp, lineHeight = 18.sp
            )
        }
        items(groups, key = { it.first }) { (app, sections) ->
            Column(
                Modifier.fillMaxWidth().clip(GlassShape).background(c.card)
                    .border(1.dp, c.cardBorder, GlassShape)
            ) {
                Text(app, fontWeight = FontWeight.Bold, color = c.textPrimary, fontSize = 16.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp))
                HorizontalDivider(color = c.cardBorder, thickness = 0.5.dp)
                sections.forEachIndexed { i, s ->
                    if (i > 0) HorizontalDivider(color = c.cardBorder, thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 16.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(s.label, fontWeight = FontWeight.SemiBold, color = c.textPrimary, fontSize = 15.sp)
                            Text(s.description, color = c.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        GlassSwitch(checked = s.id in blocked, onCheckedChange = { onToggle(s.id, it) })
                    }
                }
            }
        }
    }
}

@Composable
private fun WebViewList(
    guardOn: Boolean,
    allowed: Set<String>,
    onGuard: (Boolean) -> Unit,
    onToggle: (String, Boolean) -> Unit,
) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val apps by produceState(initialValue = emptyList<Map<String, Any>>()) {
        value = AppInventory.collect(context)
    }
    val shown = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter {
            val l = (it["label"] as? String).orEmpty()
            val p = (it["pkg"] as? String).orEmpty()
            l.contains(query, true) || p.contains(query, true)
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().clip(GlassShape).background(c.card)
                    .border(1.dp, c.cardBorder, GlassShape).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Only allow the apps I choose", fontWeight = FontWeight.SemiBold,
                        color = c.textPrimary, fontSize = 15.sp)
                    Text("Web pages opened inside any other app are closed. Normal browsers aren't affected.",
                        color = c.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                }
                Spacer(Modifier.width(10.dp))
                GlassSwitch(checked = guardOn, onCheckedChange = onGuard)
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search apps", color = c.textSecondary) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = c.textSecondary) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Clear, "Clear search", tint = c.textSecondary)
                    }
                },
                singleLine = true,
                enabled = guardOn,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.accent, unfocusedBorderColor = c.cardBorder,
                    focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary, cursorColor = c.accent,
                    focusedContainerColor = c.card, unfocusedContainerColor = c.card
                )
            )
        }
        if (!guardOn) {
            item {
                Text("Turn the switch above on to choose which apps may show web pages.",
                    color = c.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        } else {
            items(shown, key = { it["pkg"] as String }) { app ->
                val pkg = app["pkg"] as String
                Row(
                    Modifier.fillMaxWidth().clip(GlassShape).background(c.card)
                        .border(1.dp, c.cardBorder, GlassShape).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text((app["label"] as? String).orEmpty(), fontWeight = FontWeight.SemiBold,
                            color = c.textPrimary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(pkg, color = c.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(10.dp))
                    GlassSwitch(checked = pkg in allowed, onCheckedChange = { onToggle(pkg, it) })
                }
            }
        }
    }
}
