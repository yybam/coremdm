package com.core.mdm.ui.limits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.core.mdm.firebase.RemotePolicies
import com.core.mdm.policy.AppInventory
import com.core.mdm.policy.AppTimeLimits
import com.core.mdm.ui.theme.*

/**
 * Daily minute allowances per app. Time comes from Android's usage statistics, so the admin
 * grants usage access once — the card at the top says whether that's been done.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeLimitsScreen(onNavigateBack: () -> Unit) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val policies by RemotePolicies.policies.collectAsState()
    var hasAccess by remember { mutableStateOf(AppTimeLimits.hasUsageAccess(context)) }
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Pair<String, String>?>(null) }   // pkg to label
    var tick by remember { mutableIntStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasAccess = AppTimeLimits.hasUsageAccess(context)
        tick++
    }

    val limits = remember(policies) { AppTimeLimits.readLimits(policies) }
    val usage = remember(limits, hasAccess, tick) {
        AppTimeLimits.todayUsage(context, limits).associateBy { it.pkg }
    }
    val apps by produceState(initialValue = emptyList<Map<String, Any>>(), tick) {
        value = AppInventory.collect(context)
    }
    fun labelOf(pkg: String) =
        apps.firstOrNull { it["pkg"] == pkg }?.get("label") as? String ?: pkg

    Scaffold(
        modifier = Modifier.siteBackground(),
        topBar = {
            TopAppBar(
                title = { Text("Time limits", fontWeight = FontWeight.Bold, color = c.textPrimary, fontSize = 19.sp) },
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
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!hasAccess) item {
                Row(
                    Modifier.fillMaxWidth().clip(GlassShape).background(c.yellow.copy(alpha = 0.1f))
                        .border(1.dp, c.yellow.copy(alpha = 0.35f), GlassShape).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Timer, null, tint = c.yellow, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Usage access is off", fontWeight = FontWeight.SemiBold, color = c.yellow, fontSize = 15.sp)
                        Text("Time limits can't be counted until it's on.",
                            color = c.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    GradientButton(onClick = { AppTimeLimits.openUsageAccessSettings(context) },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                        Text("Turn on", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }

            item {
                Text("When an app runs out of time it's paused until after midnight.",
                    color = c.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            }

            if (limits.isNotEmpty()) {
                item { SectionHeader("Apps with a limit") }
                items(limits.toList(), key = { "lim-${it.first}" }) { (pkg, mins) ->
                    val u = usage[pkg]
                    LimitRow(
                        label = labelOf(pkg), pkg = pkg, minutes = mins,
                        usedMinutes = u?.usedMinutes, isOver = u?.isOver == true,
                        onEdit = { editing = pkg to labelOf(pkg) },
                        onRemove = { RemotePolicies.setTimeLimit(context, pkg, 0) }
                    )
                }
            }

            item { SectionHeader("Add a limit") }
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search apps", color = c.textSecondary) },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = c.textSecondary) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Clear, "Clear search", tint = c.textSecondary)
                        }
                    },
                    singleLine = true, shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.accent, unfocusedBorderColor = c.cardBorder,
                        focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary,
                        cursorColor = c.accent, focusedContainerColor = c.card, unfocusedContainerColor = c.card
                    )
                )
            }
            val candidates = apps.filter { a ->
                val pkg = a["pkg"] as? String ?: return@filter false
                pkg !in limits && (query.isBlank() ||
                    (a["label"] as? String).orEmpty().contains(query, true) || pkg.contains(query, true))
            }
            items(candidates, key = { "add-${it["pkg"]}" }) { a ->
                val pkg = a["pkg"] as String
                val label = (a["label"] as? String).orEmpty()
                Row(
                    Modifier.fillMaxWidth().clip(GlassShape).background(c.card)
                        .border(1.dp, c.cardBorder, GlassShape)
                        .clickable { editing = pkg to label }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(label, fontWeight = FontWeight.SemiBold, color = c.textPrimary,
                            fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(pkg, color = c.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("Set limit", color = c.cyan, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
    }

    editing?.let { (pkg, label) ->
        LimitDialog(
            label = label,
            initial = limits[pkg] ?: 60,
            onDismiss = { editing = null },
            onSave = { RemotePolicies.setTimeLimit(context, pkg, it); editing = null }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, fontWeight = FontWeight.Bold, color = LocalAppColors.current.accent2,
        fontSize = 12.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun LimitRow(
    label: String, pkg: String, minutes: Int,
    usedMinutes: Int?, isOver: Boolean,
    onEdit: () -> Unit, onRemove: () -> Unit,
) {
    val c = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().clip(GlassShape)
            .background(if (isOver) c.yellow.copy(alpha = 0.08f) else c.card)
            .border(1.dp, if (isOver) c.yellow.copy(alpha = 0.35f) else c.cardBorder, GlassShape)
            .clickable(onClick = onEdit).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold, color = c.textPrimary, fontSize = 15.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    isOver -> "Paused — $minutes min used up today"
                    usedMinutes != null -> "$usedMinutes of $minutes min used today"
                    else -> "$minutes min a day"
                },
                color = if (isOver) c.yellow else c.textSecondary, fontSize = 12.sp
            )
            if (usedMinutes != null && minutes > 0) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (usedMinutes.toFloat() / minutes).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(99.dp)),
                    color = if (isOver) c.yellow else c.accent2,
                    trackColor = c.cardBorder
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        IconButton(onClick = onRemove) {
            Icon(Icons.Outlined.Delete, "Remove limit", tint = c.textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun LimitDialog(label: String, initial: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    val c = LocalAppColors.current
    var text by remember { mutableStateOf(initial.toString()) }
    val mins = text.toIntOrNull() ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.panel,
        icon = { Icon(Icons.Outlined.Timer, null, tint = c.cyan) },
        title = { Text("Daily limit for $label", color = c.textPrimary, fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("How many minutes a day may this app be used?", color = c.textSecondary, fontSize = 13.sp)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(4) },
                    singleLine = true,
                    suffix = { Text("min", color = c.textSecondary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.accent, unfocusedBorderColor = c.cardBorder,
                        focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary, cursorColor = c.accent
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60, 120).forEach { preset ->
                        SuggestionChip(
                            onClick = { text = preset.toString() },
                            label = { Text("$preset") },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = c.card, labelColor = c.textSecondary)
                        )
                    }
                }
            }
        },
        confirmButton = {
            GradientButton(
                onClick = { onSave(mins) },
                enabled = mins in 1..1440,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
            ) { Text("Save", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.cyan) } }
    )
}
