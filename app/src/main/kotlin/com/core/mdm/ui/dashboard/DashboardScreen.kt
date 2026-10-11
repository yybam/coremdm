package com.core.mdm.ui.dashboard

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.core.mdm.MdmDeviceAdmin
import com.core.mdm.vpn.DnsVpnService
import com.core.mdm.vpn.DummyBrowserVpnService
import com.core.mdm.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToApps: () -> Unit,
    onNavigateToPolicies: () -> Unit = {},
    onNavigateToPinSetup: () -> Unit = {},
    onNavigateToFilter: () -> Unit = {},
    onNavigateToParental: () -> Unit = {},
    onNavigateToKiosk: () -> Unit = {},
    onNavigateToTelemetry: () -> Unit = {},
    onNavigateToRemote: () -> Unit = {},
    onSignOut: () -> Unit = {},
    viewModel: DashboardViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showWipeDialog by remember { mutableStateOf(false) }
    var showRemoveMdmDialog by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    val isFilterRunning by DnsVpnService.isRunning.collectAsState()
    val isDummyBrowserRunning by DummyBrowserVpnService.isRunning.collectAsState()

    val context = LocalContext.current
    val adminLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refresh() }

    val provisionDeviceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refresh() }

    val provisionProfileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refresh() }

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
                        Icon(Icons.Filled.Security, null,
                            tint = LocalAppColors.current.cyan, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("CORE MDM", fontWeight = FontWeight.Bold,
                            color = LocalAppColors.current.textPrimary, fontSize = 18.sp)
                    }
                },
                actions = {
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.Outlined.Logout, "Sign Out", tint = LocalAppColors.current.textSecondary)
                    }
                    IconButton(
                        onClick  = { viewModel.refresh() },
                        enabled  = !state.isRefreshing
                    ) {
                        RefreshIcon(
                            isRefreshing = state.isRefreshing,
                            tint = if (state.isRefreshing) LocalAppColors.current.cyan.copy(alpha = 0.55f) else LocalAppColors.current.cyan
                        )
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
        containerColor = LocalAppColors.current.navy,
        // Edge-to-edge stops the window from shrinking for the keyboard, so pad for it here —
        // otherwise it covers the bottom of the scroll area and focused fields can't scroll into view.
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->

        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LocalAppColors.current.cyan)
            }
            return@Scaffold
        }

        val c = LocalAppColors.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Status ────────────────────────────────────────────────────────
            StatusCard(
                isAdminActive  = state.isAdminActive,
                isDeviceOwner  = state.isDeviceOwner,
                deviceModel    = state.deviceModel,
                androidVersion = state.androidVersion,
                onLock         = { viewModel.lockDevice() },
                onReboot       = { viewModel.reboot() }
            )

            if (!state.isDeviceOwner) {
                DeviceAdminCard(
                    isAdminActive = state.isAdminActive,
                    isDeviceOwner = state.isDeviceOwner,
                    onActivateAdmin = {
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                ComponentName(context, MdmDeviceAdmin::class.java))
                            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                "Required to enforce device policies and restrictions.")
                        }
                        adminLauncher.launch(intent)
                    },
                    onProvisionDevice = {
                        val intent = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE).apply {
                            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                                ComponentName(context, MdmDeviceAdmin::class.java))
                            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true)
                        }
                        provisionDeviceLauncher.launch(intent)
                    },
                    onProvisionProfile = {
                        val intent = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
                            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                                ComponentName(context, MdmDeviceAdmin::class.java))
                        }
                        provisionProfileLauncher.launch(intent)
                    },
                    onRefresh = { viewModel.refresh() }
                )
            }

            // ── Management grid ───────────────────────────────────────────────
            SectionLabel("DEVICE MANAGEMENT")

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CategoryTile(
                    icon      = Icons.Outlined.AdminPanelSettings,
                    title     = "Device Policies",
                    subtitle  = "Restrictions & security controls",
                    iconColor = c.red,
                    onClick   = onNavigateToPolicies,
                    modifier  = Modifier.weight(1f)
                )
                CategoryTile(
                    icon      = Icons.Filled.Shield,
                    title     = "Network & Filter",
                    subtitle  = "DNS, VPN & content filter",
                    iconColor = c.green,
                    badge     = when {
                        isDummyBrowserRunning -> "VPN"
                        isFilterRunning       -> "ON"
                        else                  -> null
                    },
                    onClick   = onNavigateToFilter,
                    modifier  = Modifier.weight(1f)
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CategoryTile(
                    icon      = Icons.Outlined.FamilyRestroom,
                    title     = "Parental Controls",
                    subtitle  = "Screen time, email & SMS",
                    iconColor = c.purple,
                    onClick   = onNavigateToParental,
                    modifier  = Modifier.weight(1f)
                )
                CategoryTile(
                    icon      = Icons.Outlined.Apps,
                    title     = "App Manager",
                    subtitle  = "Block & manage individual apps",
                    iconColor = c.cyan,
                    onClick   = onNavigateToApps,
                    modifier  = Modifier.weight(1f)
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CategoryTile(
                    icon      = Icons.Outlined.LockPerson,
                    title     = "Kiosk Mode",
                    subtitle  = "Lock device to specific apps",
                    iconColor = c.orange,
                    onClick   = onNavigateToKiosk,
                    modifier  = Modifier.weight(1f)
                )
                CategoryTile(
                    icon      = Icons.Outlined.SettingsRemote,
                    title     = "Remote Control",
                    subtitle  = "Alarm & remote commands",
                    iconColor = c.red,
                    onClick   = onNavigateToRemote,
                    modifier  = Modifier.weight(1f)
                )
            }

            NavCard(
                icon      = Icons.Outlined.Analytics,
                title     = "Device Details",
                subtitle  = "Battery, storage, network diagnostics",
                iconColor = c.cyan,
                onClick   = onNavigateToTelemetry
            )

            // ── Quick Actions ─────────────────────────────────────────────────
            SectionLabel("QUICK ACTIONS")

            PolicyCard(icon = Icons.Outlined.FlashOn, title = "Quick Actions",
                iconColor = c.yellow) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.lockDevice() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.cyanDim)
                    ) {
                        Icon(Icons.Filled.Lock, null, tint = c.cyan, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Lock Now", color = c.cyan, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Button(
                        onClick = { viewModel.reboot() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A00))
                    ) {
                        Icon(Icons.Filled.RestartAlt, null,
                            tint = c.yellow, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reboot", color = c.yellow, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }

            // ── Settings ──────────────────────────────────────────────────────
            SectionLabel("SETTINGS")
            GeneralSettingsCard()
            PinSecurityCard(onNavigateToPinSetup = onNavigateToPinSetup)

            // ── Danger Zone ───────────────────────────────────────────────────
            SectionLabel("DANGER ZONE")
            PolicyCard(icon = Icons.Outlined.Warning, title = "Danger Zone",
                iconColor = c.red) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.CloudOff, null, tint = c.yellow, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Disconnect from Console", fontWeight = FontWeight.SemiBold,
                            color = c.yellow, fontSize = 14.sp)
                        Text("Stop remote management and sign out",
                            color = c.textSecondary, fontSize = 11.sp)
                    }
                    Button(
                        onClick = { showDisconnectDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2000)),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Disconnect", color = c.yellow, fontWeight = FontWeight.Bold) }
                }
                PolicyDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.DeleteForever, null, tint = c.red, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Factory Reset Device", fontWeight = FontWeight.SemiBold,
                            color = c.red, fontSize = 14.sp)
                        Text("Wipes ALL data — irreversible",
                            color = c.textSecondary, fontSize = 11.sp)
                    }
                    Button(
                        onClick = { showWipeDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A1010)),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Wipe", color = c.red, fontWeight = FontWeight.Bold) }
                }
                PolicyDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.AppBlocking, null, tint = c.red, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Remove MDM", fontWeight = FontWeight.SemiBold,
                            color = c.red, fontSize = 14.sp)
                        Text("Clears admin privileges and uninstalls this app",
                            color = c.textSecondary, fontSize = 11.sp)
                    }
                    Button(
                        onClick = { showRemoveMdmDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A1010)),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Remove", color = c.red, fontWeight = FontWeight.Bold) }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            containerColor = LocalAppColors.current.card,
            icon = { Icon(Icons.Filled.Warning, null, tint = LocalAppColors.current.red) },
            title = { Text("Factory Reset Device?", color = LocalAppColors.current.red) },
            text = {
                Text(
                    "ALL data will be permanently erased. This action cannot be undone.",
                    color = LocalAppColors.current.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.apply { /* wipeDevice() */ }; showWipeDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.red)
                ) { Text("WIPE DEVICE", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDialog = false }) {
                    Text("Cancel", color = LocalAppColors.current.cyan)
                }
            }
        )
    }

    if (showRemoveMdmDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveMdmDialog = false },
            containerColor = LocalAppColors.current.card,
            icon = { Icon(Icons.Outlined.AppBlocking, null, tint = LocalAppColors.current.red) },
            title = { Text("Remove MDM?", color = LocalAppColors.current.red) },
            text = {
                Text(
                    "This will:\n\n" +
                    "• Clear Device Owner status\n" +
                    "• Remove Device Admin privileges\n" +
                    "• Uninstall this app\n\n" +
                    "All enforced policies will be lifted. This cannot be undone without reinstalling.",
                    color = LocalAppColors.current.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.removeSelf(); showRemoveMdmDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.red)
                ) { Text("REMOVE MDM", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveMdmDialog = false }) {
                    Text("Cancel", color = LocalAppColors.current.cyan)
                }
            }
        )
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            containerColor = LocalAppColors.current.card,
            icon = { Icon(Icons.Outlined.CloudOff, null, tint = LocalAppColors.current.yellow) },
            title = { Text("Disconnect from Console?", color = LocalAppColors.current.yellow) },
            text = {
                Text(
                    "This device will stop receiving remote commands and policy updates " +
                    "from the web console.\n\nAll restrictions already in place stay active. " +
                    "You can reconnect at any time by signing back in to CORE MDM.",
                    color = LocalAppColors.current.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { showDisconnectDialog = false; onSignOut() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalAppColors.current.yellow.copy(alpha = 0.15f),
                        contentColor   = LocalAppColors.current.yellow
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, LocalAppColors.current.yellow.copy(alpha = 0.5f)
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) { Text("Disconnect", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Cancel", color = LocalAppColors.current.cyan)
                }
            }
        )
    }
}

// ── Status Card ───────────────────────────────────────────────────────────────

@Composable
private fun StatusCard(
    isAdminActive: Boolean,
    isDeviceOwner: Boolean,
    deviceModel: String,
    androidVersion: String,
    onLock: () -> Unit,
    onReboot: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth()
            // Own render layer: scrolling translates the cached card instead of re-recording it
            .graphicsLayer(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.card),
        border = androidx.compose.foundation.BorderStroke(1.dp, LocalAppColors.current.cardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Status indicator dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isAdminActive) LocalAppColors.current.green else LocalAppColors.current.red)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isDeviceOwner) "Device Owner — Protected"
                               else if (isAdminActive) "Admin Active" else "Not Admin",
                        fontWeight = FontWeight.Bold,
                        color = if (isAdminActive) LocalAppColors.current.green else LocalAppColors.current.red,
                        fontSize = 15.sp
                    )
                    Text(deviceModel, color = LocalAppColors.current.textSecondary, fontSize = 11.sp)
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDeviceOwner) Color(0xFF0A2A0A) else Color(0xFF2A0A0A),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = if (isDeviceOwner) "FULL CONTROL" else "LIMITED",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = if (isDeviceOwner) LocalAppColors.current.green else LocalAppColors.current.yellow,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(androidVersion, color = LocalAppColors.current.textSecondary, fontSize = 11.sp)
        }
    }
}

// ── Section label ─────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        color = LocalAppColors.current.textSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
}

// ── Category tile (2-column grid item) ────────────────────────────────────────

@Composable
private fun CategoryTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    iconColor: Color,
    modifier: Modifier = Modifier,
    badge: String? = null,
    onClick: () -> Unit,
) {
    val c = LocalAppColors.current
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = c.card),
        border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 108.dp)
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconColor.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
                }
                if (badge != null) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = iconColor.copy(alpha = 0.18f)
                    ) {
                        Text(
                            badge,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            color = iconColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = c.textPrimary,
                    fontSize = 13.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = c.textSecondary, fontSize = 10.sp, lineHeight = 13.sp)
            }
        }
    }
}

// ── Shared composables ────────────────────────────────────────────────────────

/**
 * Refresh icon that spins only while a refresh is in flight. The infinite transition is
 * created inside the `isRefreshing` branch so nothing is animating (and no frame callback
 * is scheduled every vsync) while the dashboard is idle or scrolling.
 */
@Composable
private fun RefreshIcon(isRefreshing: Boolean, tint: Color) {
    val angle = if (isRefreshing) {
        rememberInfiniteTransition(label = "refresh_spin").animateFloat(
            initialValue  = 0f,
            targetValue   = 360f,
            animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing)),
            label = "spin_angle"
        ).value
    } else 0f
    Icon(Icons.Filled.Refresh, "Refresh", tint = tint, modifier = Modifier.rotate(angle))
}

@Composable
fun PolicyCard(
    icon: ImageVector,
    title: String,
    iconColor: Color = MdmCyan,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
            // Own render layer: scrolling translates the cached card instead of re-recording it
            .graphicsLayer(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.card),
        border = androidx.compose.foundation.BorderStroke(1.dp, LocalAppColors.current.cardBorder)
    ) {
        Column {
            // Card header
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(title, fontWeight = FontWeight.Bold,
                    color = LocalAppColors.current.textPrimary, fontSize = 15.sp)
            }
            HorizontalDivider(color = LocalAppColors.current.cardBorder, thickness = 0.5.dp)
            content()
        }
    }
}

@Composable
fun PolicyToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null,
            tint = if (checked) LocalAppColors.current.cyan else LocalAppColors.current.textSecondary,
            modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold,
                color = if (enabled) LocalAppColors.current.textPrimary else LocalAppColors.current.textSecondary,
                fontSize = 14.sp)
            Text(subtitle, color = LocalAppColors.current.textSecondary, fontSize = 10.sp,
                lineHeight = 13.sp)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor       = Color.White,
                checkedTrackColor       = LocalAppColors.current.green,
                uncheckedThumbColor     = LocalAppColors.current.textSecondary,
                uncheckedTrackColor     = LocalAppColors.current.navy,
                uncheckedBorderColor    = LocalAppColors.current.cardBorder,
            )
        )
    }
}

@Composable
fun PolicyDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 48.dp),
        color = LocalAppColors.current.cardBorder,
        thickness = 0.5.dp
    )
}

@Composable
fun NavCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconColor: Color = MdmCyan,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
            // Own render layer: scrolling translates the cached card instead of re-recording it
            .graphicsLayer(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.card),
        border = androidx.compose.foundation.BorderStroke(1.dp, LocalAppColors.current.cardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = LocalAppColors.current.textPrimary, fontSize = 15.sp)
                Text(subtitle, color = LocalAppColors.current.textSecondary, fontSize = 11.sp)
            }
            Icon(Icons.Filled.ChevronRight, null,
                tint = LocalAppColors.current.textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

// ── Device Admin card ─────────────────────────────────────────────────────────────

@Composable
private fun DeviceAdminCard(
    isAdminActive: Boolean,
    isDeviceOwner: Boolean,
    onActivateAdmin: () -> Unit,
    onProvisionDevice: () -> Unit,
    onProvisionProfile: () -> Unit,
    onRefresh: () -> Unit,
) {
    val isNotAdmin  = !isAdminActive
    val adminOnly   = isAdminActive && !isDeviceOwner
    val borderColor = if (isNotAdmin) LocalAppColors.current.red.copy(alpha = 0.5f) else LocalAppColors.current.yellow.copy(alpha = 0.5f)
    val bgColor     = if (isNotAdmin) Color(0xFF1E0808) else Color(0xFF1E1800)
    val accentColor = if (isNotAdmin) LocalAppColors.current.red else LocalAppColors.current.yellow

    Card(
        modifier = Modifier.fillMaxWidth()
            // Own render layer: scrolling translates the cached card instead of re-recording it
            .graphicsLayer(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = bgColor),
        border   = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // Header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isNotAdmin) Icons.Filled.GppBad else Icons.Filled.GppMaybe,
                        contentDescription = null,
                        tint     = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text       = if (isNotAdmin) "Device Admin Not Active" else "Admin Active — Not Device Owner",
                        fontWeight = FontWeight.Bold,
                        color      = accentColor,
                        fontSize   = 15.sp
                    )
                    Text(
                        text     = if (isNotAdmin) "Policies cannot be enforced" else "Some features require Device Owner",
                        color    = LocalAppColors.current.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            HorizontalDivider(color = borderColor, thickness = 0.5.dp)

            if (isNotAdmin) {
                Text(
                    text     = "Tap below to grant Device Administrator privileges. You will see a system confirmation screen.",
                    color    = LocalAppColors.current.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Button(
                    onClick = onActivateAdmin,
                    modifier = Modifier.fillMaxWidth(),
                    shape  = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.red)
                ) {
                    Icon(Icons.Filled.AdminPanelSettings, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Activate Device Admin", fontWeight = FontWeight.Bold)
                }
            } else {
                // Option 1 — Full Device Owner (requires no Google account on device)
                Text(
                    text      = "Option 1 — Full Device Owner",
                    fontWeight = FontWeight.SemiBold,
                    color     = LocalAppColors.current.green,
                    fontSize  = 13.sp
                )
                Text(
                    text      = "Grants complete device control. Requires no Google account signed in on this device.",
                    color     = LocalAppColors.current.textSecondary,
                    fontSize  = 12.sp,
                    lineHeight = 16.sp
                )
                Button(
                    onClick  = onProvisionDevice,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.green.copy(alpha = 0.85f))
                ) {
                    Icon(Icons.Filled.AdminPanelSettings, null, modifier = Modifier.size(18.dp),
                        tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("Set as Device Owner", fontWeight = FontWeight.Bold, color = Color.Black)
                }

                HorizontalDivider(color = borderColor, thickness = 0.5.dp)

                // Option 2 — Work Profile (always works, even with Google account)
                Text(
                    text      = "Option 2 — Work Profile",
                    fontWeight = FontWeight.SemiBold,
                    color     = LocalAppColors.current.cyan,
                    fontSize  = 13.sp
                )
                Text(
                    text      = "Creates a managed work profile. Works with any Google account. Less control than Device Owner.",
                    color     = LocalAppColors.current.textSecondary,
                    fontSize  = 12.sp,
                    lineHeight = 16.sp
                )
                Button(
                    onClick  = onProvisionProfile,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.cyan.copy(alpha = 0.15f))
                ) {
                    Icon(Icons.Filled.WorkspacePremium, null, modifier = Modifier.size(18.dp),
                        tint = LocalAppColors.current.cyan)
                    Spacer(Modifier.width(8.dp))
                    Text("Create Work Profile", fontWeight = FontWeight.Bold, color = LocalAppColors.current.cyan)
                }

                HorizontalDivider(color = borderColor, thickness = 0.5.dp)

                // ADB fallback
                Text(
                    text      = "Or run via ADB (works regardless of accounts):",
                    color     = LocalAppColors.current.textSecondary,
                    fontSize  = 11.sp
                )
                Surface(
                    shape  = RoundedCornerShape(8.dp),
                    color  = LocalAppColors.current.navy,
                    border = androidx.compose.foundation.BorderStroke(1.dp, LocalAppColors.current.cardBorder)
                ) {
                    Text(
                        text       = "adb shell dpm set-device-owner com.core.mdm/.MdmDeviceAdmin",
                        modifier   = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        color      = LocalAppColors.current.cyan,
                        fontSize   = 11.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                OutlinedButton(
                    onClick  = onRefresh,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = LocalAppColors.current.yellow),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, LocalAppColors.current.yellow.copy(alpha = 0.4f))
                ) {
                    Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Re-check Status", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ── Content Filter card ───────────────────────────────────────────────────────────

@Composable
private fun ContentFilterCard(onNavigateToFilter: () -> Unit) {
    val isRunning by DnsVpnService.isRunning.collectAsState()

    PolicyCard(
        icon      = if (isRunning) Icons.Filled.Shield else Icons.Outlined.Shield,
        title     = "Content Filter",
        iconColor = if (isRunning) LocalAppColors.current.green else LocalAppColors.current.textSecondary
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text       = if (isRunning) "DNS Filter Active" else "DNS Filter Inactive",
                    fontWeight = FontWeight.SemiBold,
                    color      = if (isRunning) LocalAppColors.current.green else LocalAppColors.current.textSecondary,
                    fontSize   = 14.sp
                )
                Text(
                    text     = if (isRunning)
                        "Blocking sites at network level"
                    else
                        "Enable to block sites across all apps",
                    color    = LocalAppColors.current.textSecondary,
                    fontSize = 11.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onNavigateToFilter,
                shape  = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) LocalAppColors.current.green.copy(alpha = 0.15f)
                                     else LocalAppColors.current.card,
                    contentColor   = if (isRunning) LocalAppColors.current.green else LocalAppColors.current.textPrimary
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isRunning) LocalAppColors.current.green.copy(alpha = 0.4f) else LocalAppColors.current.cardBorder
                ),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Text("Manage", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

// ── General Settings card ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeneralSettingsCard() {
    val c       = LocalAppColors.current
    val context = LocalContext.current
    val themeMode by com.core.mdm.settings.ThemePrefs.themeMode.collectAsState()
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        }.getOrDefault("?")
    }

    PolicyCard(icon = Icons.Outlined.Settings, title = "App Settings", iconColor = c.cyan) {
        // ── Theme row ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Palette, null, tint = c.cyan, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("App Theme", fontWeight = FontWeight.SemiBold,
                    color = c.textPrimary, fontSize = 14.sp)
                Text("Light, Dark, or follow system",
                    color = c.textSecondary, fontSize = 10.sp)
            }
            Spacer(Modifier.width(8.dp))
            SingleChoiceSegmentedButtonRow {
                listOf(
                    com.core.mdm.ui.theme.ThemeMode.SYSTEM to "Auto",
                    com.core.mdm.ui.theme.ThemeMode.LIGHT  to "Light",
                    com.core.mdm.ui.theme.ThemeMode.DARK   to "Dark",
                ).forEachIndexed { i, (mode, label) ->
                    SegmentedButton(
                        selected = themeMode == mode,
                        onClick  = { com.core.mdm.settings.ThemePrefs.setTheme(context, mode) },
                        shape    = SegmentedButtonDefaults.itemShape(index = i, count = 3),
                        colors   = SegmentedButtonDefaults.colors(
                            activeContainerColor   = c.cyan.copy(alpha = 0.15f),
                            activeContentColor     = c.cyan,
                            activeBorderColor      = c.cyan.copy(alpha = 0.6f),
                            inactiveContainerColor = c.card,
                            inactiveContentColor   = c.textSecondary,
                            inactiveBorderColor    = c.cardBorder,
                        ),
                        icon = {}
                    ) {
                        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 48.dp),
            color = c.cardBorder, thickness = 0.5.dp
        )
        // ── Version row ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Info, null,
                tint = c.textSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("App Version", fontWeight = FontWeight.SemiBold,
                    color = c.textPrimary, fontSize = 14.sp)
                Text("CORE MDM v$versionName",
                    color = c.textSecondary, fontSize = 10.sp)
            }
        }
    }
}

// ── PIN Security card ─────────────────────────────────────────────────────────────

@Composable
private fun PinSecurityCard(onNavigateToPinSetup: () -> Unit) {
    // Read pin state locally; lightweight (no DPM call)
    val context    = androidx.compose.ui.platform.LocalContext.current
    val pinManager = remember { com.core.mdm.security.PinManager.getInstance(context) }
    var isPinSet   by remember { mutableStateOf(pinManager.isPinSet) }

    // Refresh when composable re-enters composition (e.g., back from PIN setup screen)
    LaunchedEffect(Unit) { isPinSet = pinManager.isPinSet }

    PolicyCard(
        icon      = Icons.Outlined.Lock,
        title     = "App PIN Lock",
        iconColor = LocalAppColors.current.yellow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isPinSet) "PIN Lock Enabled" else "PIN Lock Disabled",
                    fontWeight = FontWeight.SemiBold,
                    color      = if (isPinSet) LocalAppColors.current.green else LocalAppColors.current.textSecondary,
                    fontSize   = 14.sp
                )
                Text(
                    text = if (isPinSet)
                        "App locks automatically when sent to background"
                    else
                        "Set a PIN to require authentication on launch",
                    color    = LocalAppColors.current.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onNavigateToPinSetup,
                shape  = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPinSet) LocalAppColors.current.card else LocalAppColors.current.yellow.copy(alpha = 0.15f),
                    contentColor   = if (isPinSet) LocalAppColors.current.textPrimary else LocalAppColors.current.yellow
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isPinSet) LocalAppColors.current.cardBorder else LocalAppColors.current.yellow.copy(alpha = 0.4f)
                ),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Text(
                    if (isPinSet) "Change / Remove" else "Set PIN",
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 13.sp
                )
            }
        }
    }
}
