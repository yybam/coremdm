package com.core.mdm.ui.apps

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
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
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("App Management", fontWeight = FontWeight.Bold,
                            color = LocalAppColors.current.textPrimary, fontSize = 17.sp)
                        Text("${state.filteredApps.size} apps",
                            color = LocalAppColors.current.textSecondary, fontSize = 11.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = LocalAppColors.current.cyan)
                    }
                },
                actions = {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Outlined.PlaylistRemove, "Clear all", tint = LocalAppColors.current.yellow)
                    }
                    IconButton(onClick = { viewModel.loadApps() }) {
                        Icon(Icons.Filled.Refresh, "Refresh", tint = LocalAppColors.current.cyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LocalAppColors.current.navyLight,
                    titleContentColor = LocalAppColors.current.textPrimary
                )
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    modifier = Modifier.padding(12.dp),
                    containerColor = LocalAppColors.current.card,
                    contentColor = LocalAppColors.current.textPrimary,
                    snackbarData = data
                )
            }
        },
        containerColor = LocalAppColors.current.navy
    ) { padding ->

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Toolbar ───────────────────────────────────────────────────────
            Surface(color = LocalAppColors.current.navyLight, tonalElevation = 0.dp) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {

                    // Search bar
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search apps…", color = LocalAppColors.current.textSecondary, fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(Icons.Filled.Search, null, tint = LocalAppColors.current.textSecondary,
                                modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Filled.Clear, null, tint = LocalAppColors.current.textSecondary,
                                        modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = LocalAppColors.current.cyan,
                            unfocusedBorderColor = LocalAppColors.current.cardBorder,
                            focusedTextColor     = LocalAppColors.current.textPrimary,
                            unfocusedTextColor   = LocalAppColors.current.textPrimary,
                            cursorColor          = LocalAppColors.current.cyan,
                            focusedContainerColor   = LocalAppColors.current.card,
                            unfocusedContainerColor = LocalAppColors.current.card
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                    )

                    Spacer(Modifier.height(8.dp))

                    // System apps toggle + active enforcement badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Show system apps", color = LocalAppColors.current.textSecondary, fontSize = 13.sp,
                            modifier = Modifier.weight(1f))
                        Switch(
                            checked = state.showSystemApps,
                            onCheckedChange = viewModel::setShowSystemApps,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor  = Color.White,
                                checkedTrackColor  = LocalAppColors.current.cyan,
                                uncheckedThumbColor = LocalAppColors.current.textSecondary,
                                uncheckedTrackColor = LocalAppColors.current.navy,
                                uncheckedBorderColor = LocalAppColors.current.cardBorder
                            )
                        )
                        val enforced = state.apps.count { it.isHidden || it.isSuspended }
                        if (enforced > 0) {
                            Spacer(Modifier.width(12.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = LocalAppColors.current.yellow.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    "$enforced enforced",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = LocalAppColors.current.yellow, fontSize = 11.sp, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = LocalAppColors.current.cardBorder, thickness = 0.5.dp)

            // ── App list ──────────────────────────────────────────────────────
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LocalAppColors.current.cyan)
                }
            } else if (state.filteredApps.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.AppBlocking, null,
                            tint = LocalAppColors.current.textSecondary, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (state.searchQuery.isNotBlank()) "No apps match \"${state.searchQuery}\""
                            else "No apps",
                            color = LocalAppColors.current.textSecondary, fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(
                        items = state.filteredApps,
                        key = { it.packageName }
                    ) { app ->
                        AppRow(
                            app             = app,
                            onToggleHide    = { viewModel.toggleHidden(app) },
                            onToggleSuspend = { viewModel.toggleSuspended(app) }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = LocalAppColors.current.card,
            icon = { Icon(Icons.Outlined.PlaylistRemove, null, tint = LocalAppColors.current.yellow) },
            title = { Text("Clear All Enforcements?", color = LocalAppColors.current.textPrimary) },
            text = {
                Text("All hidden and suspended apps will be restored to their normal state.",
                    color = LocalAppColors.current.textSecondary)
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearAllEnforcements(); showClearDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.yellow)
                ) { Text("Clear All", color = Color.Black, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = LocalAppColors.current.cyan)
                }
            }
        )
    }
}

// ── App row ───────────────────────────────────────────────────────────────────

@Composable
private fun AppRow(
    app: AppStatus,
    onToggleHide: () -> Unit,
    onToggleSuspend: () -> Unit,
) {
    val isEnforced = app.isHidden || app.isSuspended

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnforced) Color(0xFF1A1430) else LocalAppColors.current.card
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, if (isEnforced) LocalAppColors.current.purple.copy(alpha = 0.3f) else LocalAppColors.current.cardBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // App icon
            AppIcon(drawable = app.icon, modifier = Modifier.size(42.dp))

            Spacer(Modifier.width(12.dp))

            // Labels
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    app.label,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalAppColors.current.textPrimary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    app.packageName,
                    color = LocalAppColors.current.textSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isEnforced) {
                    Spacer(Modifier.height(3.dp))
                    PolicyBadge(app)
                }
            }

            Spacer(Modifier.width(8.dp))

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Hide / Unhide
                SmallActionButton(
                    icon = if (app.isHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                    label = if (app.isHidden) "Show" else "Hide",
                    color = if (app.isHidden) LocalAppColors.current.green else LocalAppColors.current.cyan,
                    onClick = onToggleHide
                )
                // Suspend / Unsuspend
                SmallActionButton(
                    icon = if (app.isSuspended) Icons.Filled.PlayCircle else Icons.Outlined.PauseCircleOutline,
                    label = if (app.isSuspended) "Resume" else "Pause",
                    color = if (app.isSuspended) LocalAppColors.current.green else LocalAppColors.current.yellow,
                    onClick = onToggleSuspend
                )
            }
        }
    }
}

@Composable
private fun PolicyBadge(app: AppStatus) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (app.isHidden) {
            MiniChip("Hidden", LocalAppColors.current.purple)
        }
        if (app.isSuspended) {
            MiniChip("Suspended", LocalAppColors.current.yellow)
        }
        if (app.isSystem) {
            MiniChip("System", LocalAppColors.current.textSecondary)
        }
    }
}

@Composable
private fun MiniChip(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.15f)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun SmallActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.12f))
        ) {
            Icon(icon, label, tint = color, modifier = Modifier.size(18.dp))
        }
        Text(label, fontSize = 8.sp, color = color.copy(alpha = 0.8f))
    }
}

@Composable
private fun AppIcon(drawable: Drawable?, modifier: Modifier = Modifier) {
    if (drawable != null) {
        val bitmap = remember(drawable) {
            runCatching { drawable.toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = modifier.clip(RoundedCornerShape(10.dp))
            )
            return
        }
    }
    // Fallback
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(LocalAppColors.current.card),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Outlined.Android, null, tint = LocalAppColors.current.textSecondary,
            modifier = Modifier.size(24.dp))
    }
}
