package com.core.mdm.ui.filter

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.core.mdm.ui.dashboard.PolicyCard
import com.core.mdm.ui.dashboard.PolicyDivider
import com.core.mdm.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterScreen(
    onNavigateBack: () -> Unit,
    viewModel: FilterViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    // Handle VPN permission dialog
    val vpnPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onVpnPermissionResult(result.resultCode == android.app.Activity.RESULT_OK)
    }
    LaunchedEffect(state.vpnPermissionIntent) {
        state.vpnPermissionIntent?.let { vpnPermLauncher.launch(it) }
    }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let { snackbarHost.showSnackbar(it); viewModel.clearSnackbar() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Shield, null, tint = LocalAppColors.current.cyan, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Content Filter", fontWeight = FontWeight.Bold,
                            color = LocalAppColors.current.textPrimary, fontSize = 18.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = LocalAppColors.current.cyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LocalAppColors.current.navyLight,
                    titleContentColor = LocalAppColors.current.textPrimary
                )
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHost) { data ->
                Snackbar(modifier = Modifier.padding(12.dp),
                    containerColor = LocalAppColors.current.card, contentColor = LocalAppColors.current.textPrimary,
                    snackbarData = data)
            }
        },
        containerColor = LocalAppColors.current.navy
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Master toggle ─────────────────────────────────────────────────
            item { FilterToggleCard(state.isFilterRunning, viewModel::requestToggleFilter) }

            // ── Default blocklist ─────────────────────────────────────────────
            item {
                PolicyCard(icon = Icons.Outlined.Block, title = "Default Blocklist",
                    iconColor = LocalAppColors.current.red) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Block social media & adult content",
                                fontWeight = FontWeight.SemiBold,
                                color = LocalAppColors.current.textPrimary, fontSize = 14.sp)
                            Text("Instagram, TikTok, YouTube, adult sites + more",
                                color = LocalAppColors.current.textSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = state.useDefaultBlocklist,
                            onCheckedChange = viewModel::setUseDefaultBlocklist,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor   = LocalAppColors.current.green,
                                uncheckedTrackColor = LocalAppColors.current.navy,
                                uncheckedBorderColor = LocalAppColors.current.cardBorder
                            )
                        )
                    }
                }
            }

            // ── Custom blocked domains ────────────────────────────────────────
            item {
                DomainListCard(
                    title       = "Custom Blocked Sites",
                    subtitle    = "Block specific domains",
                    iconColor   = LocalAppColors.current.red,
                    icon        = Icons.Outlined.Block,
                    domains     = state.customBlocked,
                    placeholder = "e.g. reddit.com",
                    onAdd       = viewModel::addBlocked,
                    onRemove    = viewModel::removeBlocked
                )
            }

            // ── Whitelist ─────────────────────────────────────────────────────
            item {
                DomainListCard(
                    title       = "Always Allowed",
                    subtitle    = "These domains bypass all blocking",
                    iconColor   = LocalAppColors.current.green,
                    icon        = Icons.Outlined.CheckCircle,
                    domains     = state.whitelist,
                    placeholder = "e.g. school.edu",
                    onAdd       = viewModel::addWhitelisted,
                    onRemove    = viewModel::removeWhitelisted
                )
            }

            // ── Remote blocklist updater ──────────────────────────────────────
            item {
                PolicyCard(icon = Icons.Outlined.CloudDownload,
                    title = "Remote Blocklist", iconColor = LocalAppColors.current.cyan) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Paste a URL to a hosts-format blocklist. " +
                            "Domains are added to your custom list.",
                            color = LocalAppColors.current.textSecondary, fontSize = 12.sp)
                        OutlinedTextField(
                            value = state.blocklistUrl,
                            onValueChange = viewModel::setBlocklistUrl,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("https://example.com/blocklist.txt",
                                color = LocalAppColors.current.textSecondary, fontSize = 12.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done),
                            colors = mdmTextFieldColors(),
                            shape = RoundedCornerShape(10.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                color = LocalAppColors.current.textPrimary)
                        )
                        Button(
                            onClick = viewModel::fetchBlocklist,
                            enabled = !state.isFetching,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.cyanDim)
                        ) {
                            if (state.isFetching) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp),
                                    color = LocalAppColors.current.cyan, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Fetching…", color = LocalAppColors.current.cyan)
                            } else {
                                Icon(Icons.Filled.CloudDownload, null,
                                    modifier = Modifier.size(16.dp), tint = LocalAppColors.current.cyan)
                                Spacer(Modifier.width(8.dp))
                                Text("Fetch & Import", color = LocalAppColors.current.cyan,
                                    fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // ── Upstream DNS ──────────────────────────────────────────────────
            item {
                PolicyCard(icon = Icons.Outlined.Dns, title = "Upstream DNS Server",
                    iconColor = LocalAppColors.current.purple) {
                    var dns by remember { mutableStateOf(state.upstreamDns) }
                    val focusManager = LocalFocusManager.current
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = dns,
                            onValueChange = { dns = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("DNS IP", color = LocalAppColors.current.textSecondary) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                viewModel.setUpstreamDns(dns); focusManager.clearFocus()
                            }),
                            colors = mdmTextFieldColors(),
                            shape = RoundedCornerShape(10.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                                color = LocalAppColors.current.textPrimary)
                        )
                        // Quick presets
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("1.1.1.1" to "CF", "8.8.8.8" to "G").forEach { (ip, lbl) ->
                                OutlinedButton(
                                    onClick = { dns = ip; viewModel.setUpstreamDns(ip) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = LocalAppColors.current.cyan),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp, LocalAppColors.current.cyanDim),
                                    modifier = Modifier.height(32.dp)
                                ) { Text(lbl, fontSize = 11.sp) }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ── Filter toggle card ────────────────────────────────────────────────────────

@Composable
private fun FilterToggleCard(isRunning: Boolean, onToggle: () -> Unit) {
    val bg     = if (isRunning) Color(0xFF0A1F0A) else Color(0xFF1A1A1A)
    val border = if (isRunning) LocalAppColors.current.green.copy(alpha = 0.5f) else LocalAppColors.current.cardBorder
    val color  = if (isRunning) LocalAppColors.current.green else LocalAppColors.current.textSecondary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = bg),
        border   = androidx.compose.foundation.BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isRunning) Icons.Filled.Shield else Icons.Outlined.Shield,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (isRunning) "Filter Active" else "Filter Off",
                    fontWeight = FontWeight.Bold, color = color, fontSize = 17.sp
                )
                Text(
                    text = if (isRunning)
                        "DNS content filter is running"
                    else
                        "Tap to enable DNS-level content filtering",
                    color = LocalAppColors.current.textSecondary, fontSize = 12.sp
                )
            }
            Switch(
                checked = isRunning,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedTrackColor    = LocalAppColors.current.green,
                    uncheckedTrackColor  = LocalAppColors.current.navy,
                    uncheckedBorderColor = LocalAppColors.current.cardBorder
                )
            )
        }
    }
}

// ── Domain list card ──────────────────────────────────────────────────────────

@Composable
private fun DomainListCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: androidx.compose.ui.graphics.Color,
    domains: List<String>,
    placeholder: String,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    PolicyCard(icon = icon, title = title, iconColor = iconColor) {
        // Add field
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(placeholder, color = LocalAppColors.current.textSecondary, fontSize = 12.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    onAdd(input.trim()); input = ""; focusManager.clearFocus()
                }),
                colors = mdmTextFieldColors(),
                shape = RoundedCornerShape(10.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 13.sp, color = LocalAppColors.current.textPrimary,
                    fontFamily = FontFamily.Monospace)
            )
            Button(
                onClick = { onAdd(input.trim()); input = ""; focusManager.clearFocus() },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = iconColor.copy(alpha = 0.2f))
            ) { Text("Add", color = iconColor, fontWeight = FontWeight.SemiBold) }
        }

        AnimatedVisibility(visible = domains.isNotEmpty()) {
            Column {
                PolicyDivider()
                Text(
                    text = subtitle,
                    color = LocalAppColors.current.textSecondary, fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
                domains.forEach { domain ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = domain,
                            modifier = Modifier.weight(1f),
                            color = LocalAppColors.current.textPrimary, fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        IconButton(
                            onClick = { onRemove(domain) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.Close, "Remove",
                                tint = LocalAppColors.current.red, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun mdmTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = LocalAppColors.current.cyan,
    unfocusedBorderColor = LocalAppColors.current.cardBorder,
    focusedTextColor     = LocalAppColors.current.textPrimary,
    unfocusedTextColor   = LocalAppColors.current.textPrimary,
    cursorColor          = LocalAppColors.current.cyan
)
