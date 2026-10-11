package com.core.mdm.ui.location

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.core.mdm.ui.dashboard.PolicyCard
import com.core.mdm.ui.theme.LocalAppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationScreen(
    onNavigateBack: () -> Unit,
    viewModel: LocationViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = LocalAppColors.current

    // Refresh permission state when screen is shown
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocationOn, null, tint = c.green, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Location & Tracking", fontWeight = FontWeight.Bold,
                            color = c.textPrimary, fontSize = 18.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = c.cyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.navyLight),
            )
        },
        containerColor = c.navy,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Location permission status
            PolicyCard(
                icon      = Icons.Outlined.LocationOn,
                title     = "Location Permission",
                iconColor = if (state.locationPermission) c.green else c.red,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (state.locationPermission) "Granted — tracking can operate"
                            else "Not granted — auto-granted when Device Owner enables tracking",
                            color    = if (state.locationPermission) c.green else c.textSecondary,
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            // Usage stats permission
            PolicyCard(
                icon      = Icons.Outlined.BarChart,
                title     = "Usage Statistics Access",
                iconColor = if (state.usageStatsPermission) c.green else c.orange,
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        if (state.usageStatsPermission)
                            "Granted — app usage stats are being reported to the console"
                        else
                            "Not granted. This permission cannot be auto-granted; tap the button below to allow it in system settings.",
                        color    = if (state.usageStatsPermission) c.green else c.textSecondary,
                        fontSize = 13.sp,
                    )
                    if (!state.usageStatsPermission) {
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick  = { viewModel.openUsageAccessSettings() },
                            shape    = RoundedCornerShape(10.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = c.orange.copy(alpha = 0.15f)),
                        ) {
                            Text("Open Usage Access Settings", color = c.orange, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Info card
            Card(
                shape  = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = c.cyan.copy(alpha = 0.06f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, c.cyan.copy(alpha = 0.2f)),
            ) {
                Text(
                    "Location tracking and geofencing are controlled remotely from the web console. " +
                    "Enable \"Location Tracking\" in the device's policy settings to start sending GPS " +
                    "coordinates. Geofence alerts are configured per-device from the Location tab in the console.",
                    color    = c.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(14.dp),
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
