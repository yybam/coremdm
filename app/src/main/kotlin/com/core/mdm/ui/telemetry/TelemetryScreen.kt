package com.core.mdm.ui.telemetry

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.core.mdm.telemetry.DeviceMetrics
import com.core.mdm.ui.dashboard.PolicyCard
import com.core.mdm.ui.theme.LocalAppColors
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelemetryScreen(
    onNavigateBack: () -> Unit,
    viewModel: TelemetryViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = LocalAppColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Analytics, null, tint = c.cyan, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Device Details", fontWeight = FontWeight.Bold,
                            color = c.textPrimary, fontSize = 18.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = c.cyan)
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, "Refresh", tint = c.cyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.navyLight)
            )
        },
        containerColor = c.navy
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = c.cyan)
                    }
                }
            }

            state.error?.let { err ->
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = c.red.copy(alpha = 0.1f))
                    ) {
                        Text(err, color = c.red, modifier = Modifier.padding(16.dp))
                    }
                }
            }

            state.metrics?.let { m ->
                item { BatteryCard(m) }
                item { StorageCard(m) }
                item { NetworkCard(m) }
                item { SystemCard(m) }
                item {
                    val fmt = SimpleDateFormat("MMM d, h:mm:ss a", Locale.getDefault())
                    Text(
                        "Last updated: ${fmt.format(Date(m.collectedAt))}",
                        color = c.textSecondary, fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun BatteryCard(m: DeviceMetrics) {
    val c = LocalAppColors.current
    PolicyCard(icon = Icons.Outlined.BatteryChargingFull, title = "Battery", iconColor = c.green) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${m.batteryPercent}%",
                    fontSize = 32.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (m.isCharging) "Charging" else "On Battery",
                    color = if (m.isCharging) c.green else c.yellow,
                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                )
            }
            LinearProgressIndicator(
                progress = { m.batteryPercent / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = when {
                    m.batteryPercent > 50 -> c.green
                    m.batteryPercent > 20 -> c.yellow
                    else -> c.red
                },
                trackColor = c.cardBorder
            )
        }
    }
}

@Composable
private fun StorageCard(m: DeviceMetrics) {
    val c = LocalAppColors.current
    PolicyCard(icon = Icons.Outlined.Storage, title = "Internal Storage", iconColor = c.cyan) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                MetricValue("${m.storageUsedMb} MB", "Used")
                Spacer(Modifier.width(24.dp))
                MetricValue("${m.storageTotalMb} MB", "Total")
                Spacer(Modifier.width(24.dp))
                MetricValue("${m.storageFreePercent}%", "Free")
            }
            LinearProgressIndicator(
                progress = { m.storageUsedPercent / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = when {
                    m.storageUsedPercent < 70 -> c.cyan
                    m.storageUsedPercent < 90 -> c.yellow
                    else -> c.red
                },
                trackColor = c.cardBorder
            )
        }
    }
}

@Composable
private fun NetworkCard(m: DeviceMetrics) {
    val c = LocalAppColors.current
    PolicyCard(icon = Icons.Outlined.Wifi, title = "Network", iconColor = c.purple) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MetricRow("Type", m.networkType)
            m.wifiSsid?.let { MetricRow("SSID", it) }
            m.ipAddress?.let { MetricRow("IP", it) }
            if (m.wifiSsid == null && m.ipAddress == null) {
                Text("No active network connection", color = c.textSecondary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SystemCard(m: DeviceMetrics) {
    val c = LocalAppColors.current
    PolicyCard(icon = Icons.Outlined.Info, title = "System", iconColor = c.yellow) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MetricRow("Device", m.deviceModel)
            MetricRow("Android", m.osVersion)
            MetricRow("Security Patch", m.securityPatch)
        }
    }
}

@Composable
private fun MetricValue(value: String, label: String) {
    val c = LocalAppColors.current
    Column(horizontalAlignment = Alignment.Start) {
        Text(value, fontWeight = FontWeight.Bold, color = c.textPrimary, fontSize = 15.sp,
            fontFamily = FontFamily.Monospace)
        Text(label, color = c.textSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.width(110.dp))
        Text(value, color = c.textPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold)
    }
}
