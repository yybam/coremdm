package com.core.mdm.ui.apps

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.core.mdm.policy.AppStatus
import com.core.mdm.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    onNavigateBack: () -> Unit,
    viewModel: AppsViewModel = viewModel()
) {
    val c = LocalAppColors.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showRestoreAll by remember { mutableStateOf(false) }
    var sheetApp by remember { mutableStateOf<AppStatus?>(null) }
    var ruleToTurnOff by remember { mutableStateOf<BlockRule?>(null) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        modifier = Modifier.siteBackground(),
        topBar = {
            TopAppBar(
                title = { Text("Apps", fontWeight = FontWeight.Bold, color = c.textPrimary, fontSize = 19.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.textPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadApps() }) {
                        Icon(Icons.Filled.Refresh, "Refresh", tint = c.cyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.navyLight)
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(modifier = Modifier.padding(12.dp), containerColor = c.panel,
                    contentColor = c.textPrimary, snackbarData = data)
            }
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── Tabs + search ─────────────────────────────────────────────────
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TabSwitcher(
                    tab = state.tab,
                    managedCount = state.managedApps.size,
                    onSelect = viewModel::setTab
                )
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search apps", color = c.textSecondary, fontSize = 15.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = c.textSecondary) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Filled.Clear, "Clear search", tint = c.textSecondary)
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.accent, unfocusedBorderColor = c.cardBorder,
                        focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary, cursorColor = c.accent,
                        focusedContainerColor = c.card, unfocusedContainerColor = c.card
                    )
                )
                if (state.tab == AppsTab.MANAGED && state.managedApps.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Hidden apps aren't on the home screen. Paused apps can't be opened.",
                            color = c.textSecondary, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(10.dp))
                        OutlinedButton(onClick = { showRestoreAll = true }, shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder)) {
                            Text("Restore all", color = c.textPrimary, fontSize = 13.sp)
                        }
                    }
                }
                if (state.tab == AppsTab.ALL) {
                    FilterChip(
                        selected = state.showSystemApps,
                        onClick = { viewModel.setShowSystemApps(!state.showSystemApps) },
                        label = { Text("Show system apps") },
                        leadingIcon = if (state.showSystemApps) {{ Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) }} else null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = c.card, labelColor = c.textSecondary,
                            selectedContainerColor = c.accent.copy(alpha = 0.18f), selectedLabelColor = c.textPrimary,
                            selectedLeadingIconColor = c.cyan
                        ),
                        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = state.showSystemApps,
                            borderColor = c.cardBorder, selectedBorderColor = c.accent.copy(alpha = 0.5f))
                    )
                }
            }

            // ── List ──────────────────────────────────────────────────────────
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.cyan)
                }
                state.visibleApps.isEmpty() -> EmptyState(
                    managedTab = state.tab == AppsTab.MANAGED,
                    searching = state.searchQuery.isNotBlank(),
                    onBrowseAll = { viewModel.setTab(AppsTab.ALL) }
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.visibleApps, key = { it.packageName }) { app ->
                        val rule = state.rules[app.packageName]
                        if (state.tab == AppsTab.MANAGED) ManagedAppRow(
                            app = app, rule = rule,
                            onRestore = { if (rule != null) ruleToTurnOff = rule else viewModel.restore(app) }
                        ) else AppRow(app = app, rule = rule, onClick = { sheetApp = app })
                    }
                }
            }
        }
    }

    // ── Per-app options (All apps tab) ────────────────────────────────────────
    sheetApp?.let { app ->
        val current = state.apps.firstOrNull { it.packageName == app.packageName } ?: app
        val rule = state.rules[app.packageName]
        ModalBottomSheet(onDismissRequest = { sheetApp = null }, containerColor = c.panel) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(current.icon, Modifier.size(48.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(current.label, fontWeight = FontWeight.Bold, color = c.textPrimary, fontSize = 18.sp)
                        Text(statusText(current), color = statusColor(current), fontSize = 13.sp)
                    }
                }
                if (rule != null) {
                    Text("Blocked by the \"${rule.label}\" setting.", color = c.yellow, fontSize = 13.sp)
                    SheetOption(Icons.Outlined.ToggleOff, "Turn off \"${rule.label}\"",
                        "Unblocks every app that setting covers", c.yellow) { sheetApp = null; ruleToTurnOff = rule }
                } else {
                    SheetOption(
                        if (current.isSuspended) Icons.Outlined.PlayCircle else Icons.Outlined.PauseCircle,
                        if (current.isSuspended) "Unpause" else "Pause",
                        if (current.isSuspended) "Let the app open again" else "App stays on the phone but can't be opened",
                        if (current.isSuspended) c.green else c.yellow
                    ) { viewModel.setPaused(current, !current.isSuspended); sheetApp = null }
                    SheetOption(
                        if (current.isHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        if (current.isHidden) "Unhide" else "Hide",
                        if (current.isHidden) "Put the app back on the home screen" else "Remove the app from the home screen and app drawer",
                        if (current.isHidden) c.green else c.cyan
                    ) { viewModel.setHidden(current, !current.isHidden); sheetApp = null }
                }
            }
        }
    }

    if (showRestoreAll) {
        AlertDialog(
            onDismissRequest = { showRestoreAll = false },
            containerColor = c.panel,
            icon = { Icon(Icons.Outlined.SettingsBackupRestore, null, tint = c.cyan) },
            title = { Text("Restore all apps?", color = c.textPrimary) },
            text = {
                Text("Every hidden or paused app goes back to normal. Apps blocked by a setting " +
                    "(WhatsApp, social media, WebView) stay blocked until that setting is turned off.",
                    color = c.textSecondary)
            },
            confirmButton = {
                GradientButton(onClick = { viewModel.clearAllEnforcements(); showRestoreAll = false },
                    shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)) {
                    Text("Restore all", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { showRestoreAll = false }) { Text("Cancel", color = c.cyan) } }
        )
    }

    ruleToTurnOff?.let { rule ->
        AlertDialog(
            onDismissRequest = { ruleToTurnOff = null },
            containerColor = c.panel,
            icon = { Icon(Icons.Outlined.ToggleOff, null, tint = c.yellow) },
            title = { Text("Turn off \"${rule.label}\"?", color = c.textPrimary) },
            text = {
                Text("This app is blocked by that setting, so it can only be unblocked by turning the " +
                    "setting off. That unblocks every app it covers, here and on the website.",
                    color = c.textSecondary)
            },
            confirmButton = {
                Button(onClick = { viewModel.turnOffRule(rule); ruleToTurnOff = null },
                    colors = ButtonDefaults.buttonColors(containerColor = c.yellow)) {
                    Text("Turn off", color = Color(0xFF231400), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { ruleToTurnOff = null }) { Text("Cancel", color = c.cyan) } }
        )
    }
}

// ── Pieces ────────────────────────────────────────────────────────────────────

@Composable
private fun TabSwitcher(tab: AppsTab, managedCount: Int, onSelect: (AppsTab) -> Unit) {
    val c = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.card)
            .border(1.dp, c.cardBorder, RoundedCornerShape(14.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(AppsTab.MANAGED to "Managed ($managedCount)", AppsTab.ALL to "All apps").forEach { (t, label) ->
            val selected = t == tab
            Box(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(11.dp))
                    .then(if (selected) Modifier.background(brandGradient()) else Modifier)
                    .clickable { onSelect(t) },
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (selected) Color.White else c.textSecondary,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun ManagedAppRow(app: AppStatus, rule: BlockRule?, onRestore: () -> Unit) {
    val c = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().clip(GlassShape).background(c.card)
            .border(1.dp, c.cardBorder, GlassShape).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(app.icon, Modifier.size(46.dp).alpha(if (app.isHidden) 0.55f else 1f))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, fontWeight = FontWeight.SemiBold, color = c.textPrimary, fontSize = 16.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(statusText(app), color = statusColor(app), fontSize = 13.sp)
            if (rule != null) Text("By \"${rule.label}\"", color = c.textSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.width(10.dp))
        val label = when {
            rule != null -> "Unblock"
            app.isHidden && app.isSuspended -> "Restore"
            app.isHidden -> "Unhide"
            else -> "Unpause"
        }
        GradientButton(onClick = onRestore, shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 11.dp)) {
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun AppRow(app: AppStatus, rule: BlockRule?, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val managed = app.isHidden || app.isSuspended
    Row(
        Modifier.fillMaxWidth().clip(GlassShape).background(c.card)
            .border(1.dp, if (managed) c.yellow.copy(alpha = 0.35f) else c.cardBorder, GlassShape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(app.icon, Modifier.size(42.dp).alpha(if (app.isHidden) 0.55f else 1f))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, fontWeight = FontWeight.SemiBold, color = c.textPrimary, fontSize = 15.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (rule != null) "${statusText(app)} · by \"${rule.label}\"" else statusText(app),
                color = statusColor(app), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.ChevronRight, "Options", tint = c.textSecondary)
    }
}

@Composable
private fun SheetOption(icon: ImageVector, title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, color = c.textPrimary, fontSize = 16.sp)
            Text(subtitle, color = c.textSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun EmptyState(managedTab: Boolean, searching: Boolean, onBrowseAll: () -> Unit) {
    val c = LocalAppColors.current
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(if (managedTab && !searching) Icons.Outlined.CheckCircle else Icons.Outlined.SearchOff, null,
                tint = if (managedTab && !searching) c.green else c.textSecondary, modifier = Modifier.size(52.dp))
            Text(
                when {
                    searching -> "No apps match your search"
                    managedTab -> "No hidden or paused apps"
                    else -> "No apps"
                },
                color = c.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
            )
            if (managedTab && !searching) {
                Text("Every app on this phone works normally.", color = c.textSecondary, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                GradientButton(onClick = onBrowseAll, shape = RoundedCornerShape(12.dp)) {
                    Text("Browse all apps", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun statusText(app: AppStatus): String = when {
    app.isHidden && app.isSuspended -> "Hidden and paused"
    app.isHidden    -> "Hidden from the home screen"
    app.isSuspended -> "Paused — can't be opened"
    app.isSystem    -> "System app · allowed"
    else            -> "Allowed"
}

@Composable
private fun statusColor(app: AppStatus): Color {
    val c = LocalAppColors.current
    return when {
        app.isHidden || app.isSuspended -> c.yellow
        else -> c.textSecondary
    }
}

@Composable
private fun AppIcon(icon: Bitmap?, modifier: Modifier = Modifier) {
    if (icon != null) {
        // Already rendered off the main thread (see AppStatus.icon); wrapping it is free.
        val bitmap = remember(icon) { icon.asImageBitmap() }
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier.clip(RoundedCornerShape(11.dp)))
        return
    }
    Box(modifier.clip(RoundedCornerShape(11.dp)).background(LocalAppColors.current.card), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Android, null, tint = LocalAppColors.current.textSecondary, modifier = Modifier.size(24.dp))
    }
}
