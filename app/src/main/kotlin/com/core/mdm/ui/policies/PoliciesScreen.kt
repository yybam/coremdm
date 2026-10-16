package com.core.mdm.ui.policies

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.core.mdm.ui.dashboard.DashboardViewModel
import com.core.mdm.ui.dashboard.PolicyCard
import com.core.mdm.ui.dashboard.PolicyDivider
import com.core.mdm.ui.dashboard.PolicyToggleRow
import com.core.mdm.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoliciesScreen(
    onNavigateBack: () -> Unit,
    onNavigateToApps: () -> Unit = {},
    onNavigateToContent: () -> Unit = {},
    onNavigateToLimits: () -> Unit = {},
    viewModel: DashboardViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLockdownDialog by remember { mutableStateOf(false) }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AdminPanelSettings, null,
                            tint = LocalAppColors.current.red, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Device Policies", fontWeight = FontWeight.Bold,
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
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(modifier = Modifier.padding(12.dp),
                    containerColor = LocalAppColors.current.panel,
                    contentColor = LocalAppColors.current.textPrimary,
                    snackbarData = data)
            }
        },
        containerColor = Color.Transparent,
        modifier = Modifier.siteBackground(),
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { showLockdownDialog = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalAppColors.current.yellow),
                border = androidx.compose.foundation.BorderStroke(1.dp, LocalAppColors.current.yellow.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Filled.Lock, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Apply Full Device Lockdown", fontWeight = FontWeight.SemiBold)
            }

            // ── Blocking (WhatsApp, social, AI chats, WebView, remote-only) ────────
            com.core.mdm.ui.dashboard.BlockingCard(
                onNavigateToApps    = onNavigateToApps,
                onNavigateToContent = onNavigateToContent
            )

            // ── Phone & communication (calls, SMS, hotspot, app settings) ─────────
            com.core.mdm.ui.dashboard.PhoneControlCard(onNavigateToLimits = onNavigateToLimits)

            // ── Which apps may install other apps ─────────────────────────────────
            com.core.mdm.ui.dashboard.AppInstallCard()

            // ── App Restrictions ──────────────────────────────────────────────────
            PolicyCard(icon = Icons.Outlined.Apps, title = "App Restrictions",
                iconColor = LocalAppColors.current.cyan) {
                PolicyToggleRow(
                    icon = Icons.Outlined.Block, title = "Block App Installation",
                    subtitle = "DISALLOW_INSTALL_APPS — prevents Play Store installs",
                    checked = state.installAppsBlocked, onCheckedChange = viewModel::setInstallAppsBlocked
                )
                PolicyDivider()
                PolicyToggleRow(
                    icon = Icons.Outlined.DeleteSweep, title = "Block App Uninstallation",
                    subtitle = "DISALLOW_UNINSTALL_APPS — locks existing apps",
                    checked = state.uninstallAppsBlocked, onCheckedChange = viewModel::setUninstallAppsBlocked
                )
                PolicyDivider()
                PolicyToggleRow(
                    icon = Icons.Outlined.PhonelinkLock, title = "Block Sideloading",
                    subtitle = "DISALLOW_INSTALL_UNKNOWN_SOURCES — no APK sideloads",
                    checked = state.unknownSourcesBlocked, onCheckedChange = viewModel::setUnknownSourcesBlocked
                )
                PolicyDivider()
                PolicyToggleRow(
                    icon = Icons.Outlined.StoreMallDirectory, title = "Hide Play Store",
                    subtitle = "Removes Play Store icon — requires Device Owner",
                    checked = state.playStoreHidden, onCheckedChange = viewModel::setPlayStoreHidden
                )
                PolicyDivider()
                PolicyToggleRow(
                    icon = Icons.Outlined.Language, title = "Hide Browsers",
                    subtitle = "Hides Chrome, Firefox, Samsung Internet + more",
                    checked = state.browsersHidden, onCheckedChange = viewModel::setBrowsersHidden
                )
                PolicyDivider()
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ManageAccounts, null,
                        tint = LocalAppColors.current.cyan, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Manage Individual Apps", fontWeight = FontWeight.SemiBold,
                            color = LocalAppColors.current.textPrimary, fontSize = 14.sp)
                        Text("Suspend or hide specific applications",
                            color = LocalAppColors.current.textSecondary, fontSize = 11.sp)
                    }
                    IconButton(onClick = onNavigateToApps) {
                        Icon(Icons.Filled.ChevronRight, null, tint = LocalAppColors.current.cyan)
                    }
                }
            }

            // ── System & Anti-Bypass ──────────────────────────────────────────────
            PolicyCard(icon = Icons.Outlined.Security, title = "System & Anti-Bypass Security",
                iconColor = LocalAppColors.current.red) {
                PolicyToggleRow(icon = Icons.Outlined.SafetyCheck, title = "Prevent Safe Boot",
                    subtitle = "DISALLOW_SAFE_BOOT — blocks recovery bypass",
                    checked = state.safeBootBlocked, onCheckedChange = viewModel::setSafeBootBlocked)
                PolicyDivider()
                PolicyToggleRow(icon = Icons.Outlined.RestartAlt, title = "Prevent Factory Reset",
                    subtitle = "DISALLOW_FACTORY_RESET — protects device data",
                    checked = state.factoryResetBlocked, onCheckedChange = viewModel::setFactoryResetBlocked)
                PolicyDivider()
                PolicyToggleRow(icon = Icons.Outlined.DeveloperMode, title = "Disable Developer Options & ADB",
                    subtitle = "DISALLOW_DEBUGGING_FEATURES — blocks USB control",
                    checked = state.debuggingBlocked, onCheckedChange = viewModel::setDebuggingBlocked)
                PolicyDivider()
                PolicyToggleRow(icon = Icons.Outlined.PersonOff, title = "Prevent Multi-User Profiles",
                    subtitle = "DISALLOW_ADD_USER — single-user device enforcement",
                    checked = state.addUserBlocked, onCheckedChange = viewModel::setAddUserBlocked)
                PolicyDivider()
                PolicyToggleRow(icon = Icons.Outlined.SwitchAccount, title = "Block User Switching",
                    subtitle = "DISALLOW_USER_SWITCH — one active session only",
                    checked = state.userSwitchBlocked, onCheckedChange = viewModel::setUserSwitchBlocked)
                PolicyDivider()
                PolicyToggleRow(icon = Icons.Outlined.Screenshot, title = "Disable Screen Capture",
                    subtitle = "Blocks screenshots and screen recording",
                    checked = state.screenCaptureDisabled, onCheckedChange = viewModel::setScreenCaptureDisabled)
                PolicyDivider()
                PolicyToggleRow(icon = Icons.Outlined.CameraAlt, title = "Disable Camera",
                    subtitle = "Blocks all camera apps device-wide",
                    checked = state.cameraDisabled, onCheckedChange = viewModel::setCameraDisabled)
            }

            // ── Hardware & Settings Control ───────────────────────────────────────
            PolicyCard(icon = Icons.Outlined.Tune, title = "Hardware & Settings Control",
                iconColor = LocalAppColors.current.purple) {
                PolicyToggleRow(icon = Icons.Outlined.Wifi, title = "Lock Wi-Fi Configuration",
                    subtitle = "DISALLOW_CONFIG_WIFI — prevents network changes",
                    checked = state.wifiConfigBlocked, onCheckedChange = viewModel::setWifiConfigBlocked)
                PolicyDivider()
                PolicyToggleRow(icon = Icons.Outlined.SignalCellularAlt, title = "Lock Cellular Data Settings",
                    subtitle = "DISALLOW_CONFIG_MOBILE_NETWORKS — locks APN/carrier",
                    checked = state.mobileNetworksBlocked, onCheckedChange = viewModel::setMobileNetworksBlocked)
                PolicyDivider()
                PolicyToggleRow(icon = Icons.Outlined.Bluetooth, title = "Lock Bluetooth Settings",
                    subtitle = "DISALLOW_CONFIG_BLUETOOTH — prevents pairing",
                    checked = state.bluetoothConfigBlocked, onCheckedChange = viewModel::setBluetoothConfigBlocked)
                PolicyDivider()
                PolicyToggleRow(icon = Icons.Outlined.VpnLock, title = "Block VPN Configuration",
                    subtitle = "DISALLOW_CONFIG_VPN — prevents VPN profiles",
                    checked = state.vpnBlocked, onCheckedChange = viewModel::setVpnBlocked)
                PolicyDivider()
                PolicyToggleRow(icon = Icons.Outlined.NotificationsOff, title = "Lock Status Bar",
                    subtitle = "Disables pull-down shade and quick settings",
                    checked = state.statusBarDisabled, onCheckedChange = { viewModel.setStatusBarDisabled(it) })
                PolicyDivider()
                PolicyToggleRow(icon = Icons.Outlined.SettingsBackupRestore, title = "Block Network Reset",
                    subtitle = "DISALLOW_NETWORK_RESET — prevents bulk reset",
                    checked = state.networkResetBlocked, onCheckedChange = viewModel::setNetworkResetBlocked)
            }

            // ── Hardware Restrictions ─────────────────────────────────────────────
            PolicyCard(icon = Icons.Outlined.Hardware, title = "Hardware Restrictions",
                iconColor = LocalAppColors.current.orange) {
                PolicyToggleRow(icon = Icons.Outlined.Usb, title = "Block USB File Transfer",
                    subtitle = "DISALLOW_USB_FILE_TRANSFER — Android 9+",
                    checked = state.usbTransferBlocked, onCheckedChange = viewModel::setUsbTransferBlocked)
                PolicyDivider()
                PolicyToggleRow(icon = Icons.Outlined.BluetoothDisabled, title = "Disable Bluetooth",
                    subtitle = "DISALLOW_BLUETOOTH — fully disables BT",
                    checked = state.bluetoothDisabled, onCheckedChange = viewModel::setBluetoothDisabled)
                PolicyDivider()
                PolicyToggleRow(icon = Icons.Outlined.PhoneDisabled, title = "Block Outgoing Calls",
                    subtitle = "DISALLOW_OUTGOING_CALLS — no phone calls",
                    checked = state.outgoingCallsBlocked, onCheckedChange = viewModel::setOutgoingCallsBlocked)
                PolicyDivider()
                PolicyToggleRow(icon = Icons.Outlined.SdCardAlert, title = "Block Physical Media",
                    subtitle = "DISALLOW_MOUNT_PHYSICAL_MEDIA — no SD card",
                    checked = state.physicalMediaBlocked, onCheckedChange = viewModel::setPhysicalMediaBlocked)
                PolicyDivider()
                PolicyToggleRow(icon = Icons.Outlined.Shield, title = "Protect MDM from Uninstall",
                    subtitle = "setUninstallBlocked — requires Device Owner",
                    checked = state.mdmUninstallProtected, onCheckedChange = viewModel::setMdmUninstallProtected)
            }

            // ── DNS Protection ────────────────────────────────────────────────────
            DnsCard(
                privateDnsRestricted = state.privateDnsRestricted,
                privateDnsHost       = state.privateDnsHost,
                onEnforceDns         = viewModel::enforcePrivateDns,
                onClearDns           = viewModel::clearPrivateDns
            )

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showLockdownDialog) {
        AlertDialog(
            onDismissRequest = { showLockdownDialog = false },
            containerColor = LocalAppColors.current.panel,
            icon = { Icon(Icons.Filled.Lock, null, tint = LocalAppColors.current.yellow) },
            title = { Text("Apply Full Lockdown?", color = LocalAppColors.current.textPrimary) },
            text = {
                Text(
                    "This will enforce all anti-bypass restrictions, block installs/uninstalls, " +
                    "lock Wi-Fi/BT/mobile settings, disable screen capture, and disable the status bar.",
                    color = LocalAppColors.current.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.applyFullLockdown(); showLockdownDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.yellow)
                ) { Text("Apply", color = Color.Black, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showLockdownDialog = false }) {
                    Text("Cancel", color = LocalAppColors.current.cyan)
                }
            }
        )
    }
}

// ── DNS Card (Network & DNS Protection) ───────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DnsCard(
    privateDnsRestricted: Boolean,
    privateDnsHost: String,
    onEnforceDns: (String) -> Unit,
    onClearDns: () -> Unit,
) {
    var customDns by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val c = LocalAppColors.current

    val presets = listOf(
        "AdGuard"             to "dns.adguard.com",
        "Cloudflare Security" to "security.cloudflare-dns.com",
        "Cloudflare Family"   to "family.cloudflare-dns.com",
        "CleanBrowsing"       to "security-filter-dns.cleanbrowsing.org"
    )

    PolicyCard(icon = Icons.Outlined.Dns, title = "Network & DNS Protection",
        iconColor = c.green) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (privateDnsRestricted) Icons.Filled.Lock else Icons.Outlined.LockOpen,
                null,
                tint = if (privateDnsRestricted) c.green else c.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (privateDnsRestricted) "DNS Enforced" else "DNS — Not Enforced",
                    fontWeight = FontWeight.SemiBold,
                    color = if (privateDnsRestricted) c.green else c.textPrimary,
                    fontSize = 14.sp
                )
                if (privateDnsHost.isNotBlank()) {
                    Text(privateDnsHost, color = c.textSecondary, fontSize = 11.sp)
                } else {
                    Text("Tap a preset or enter a custom host", color = c.textSecondary, fontSize = 11.sp)
                }
            }
            AnimatedVisibility(privateDnsRestricted) {
                IconButton(onClick = onClearDns) {
                    Icon(Icons.Filled.Clear, "Clear DNS", tint = c.red)
                }
            }
        }
        PolicyDivider()
        presets.forEach { (label, host) ->
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(label, modifier = Modifier.weight(1f), color = c.textPrimary, fontSize = 13.sp)
                Text(host, color = c.textSecondary, fontSize = 10.sp, modifier = Modifier.padding(end = 8.dp))
                OutlinedButton(
                    onClick = { onEnforceDns(host) },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = c.cyan),
                    border = androidx.compose.foundation.BorderStroke(1.dp, c.cyanDim),
                    modifier = Modifier.height(32.dp)
                ) { Text("Set", fontSize = 12.sp) }
            }
        }
        PolicyDivider()
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = customDns, onValueChange = { customDns = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("custom.dns.host", fontSize = 13.sp, color = c.textSecondary) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (customDns.isNotBlank()) { onEnforceDns(customDns); focusManager.clearFocus() }
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.cyan, unfocusedBorderColor = c.cardBorder,
                    focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary, cursorColor = c.cyan
                ),
                shape = RoundedCornerShape(10.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
            )
            Button(
                onClick = { if (customDns.isNotBlank()) { onEnforceDns(customDns.trim()); focusManager.clearFocus() } },
                colors = ButtonDefaults.buttonColors(containerColor = c.cyanDim),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Lock", color = c.cyan, fontWeight = FontWeight.SemiBold) }
        }
    }
}
