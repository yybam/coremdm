package com.core.mdm.ui.remote

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.core.mdm.firebase.DeviceInfo
import com.core.mdm.ui.theme.LocalAppColors
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteControlScreen(
    onNavigateBack: () -> Unit,
    viewModel: RemoteControlViewModel = viewModel()
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val c = LocalAppColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.SettingsRemote, null, tint = c.red, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Remote Control", fontWeight = FontWeight.Bold, color = c.textPrimary, fontSize = 18.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = c.cyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.navyLight)
            )
        },
        containerColor = c.navy
    ) { padding ->
        if (devices.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Outlined.PhoneAndroid, null, tint = c.textSecondary, modifier = Modifier.size(56.dp))
                    Text("No enrolled devices", color = c.textSecondary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Devices appear here once the MDM app\nhas connected to Firebase.",
                        color = c.textSecondary, fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(devices, key = { it.id }) { device ->
                    DeviceCard(device = device, viewModel = viewModel)
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun DeviceCard(device: DeviceInfo, viewModel: RemoteControlViewModel) {
    val c = LocalAppColors.current
    val borderColor = if (device.alarmActive) c.red.copy(alpha = 0.5f) else c.cardBorder

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = c.card),
        border   = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // ── Device header ─────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (device.alarmActive) Icons.Filled.NotificationsActive else Icons.Outlined.PhoneAndroid,
                    contentDescription = null,
                    tint     = if (device.alarmActive) c.red else c.textSecondary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(device.name, fontWeight = FontWeight.Bold, color = c.textPrimary, fontSize = 15.sp)
                    Text(device.osVersion, color = c.textSecondary, fontSize = 11.sp)
                }
                if (device.alarmActive) {
                    Surface(shape = RoundedCornerShape(6.dp), color = c.red.copy(alpha = 0.15f)) {
                        Text(
                            "SOUNDING",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            color = c.red, fontSize = 10.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ── Last seen ─────────────────────────────────────────────────────
            device.lastSeen?.let { ts ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Outlined.AccessTime, null, tint = c.textSecondary, modifier = Modifier.size(13.dp))
                    val fmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
                    Text("Last seen ${fmt.format(Date(ts))}", color = c.textSecondary, fontSize = 11.sp)
                }
            }

            HorizontalDivider(color = c.cardBorder, thickness = 0.5.dp)

            // ── Alarm button ──────────────────────────────────────────────────
            if (device.alarmActive) {
                Button(
                    onClick  = { viewModel.stopAlarm(device.id) },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = c.red)
                ) {
                    Icon(Icons.Filled.NotificationsOff, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Stop Alarm", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            } else {
                OutlinedButton(
                    onClick  = { viewModel.soundAlarm(device.id) },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = c.red),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, c.red.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Outlined.NotificationsActive, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sound Alarm", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}
