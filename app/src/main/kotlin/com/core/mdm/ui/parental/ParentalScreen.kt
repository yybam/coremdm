package com.core.mdm.ui.parental

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.core.mdm.policy.ScreenTimeScheduler
import com.core.mdm.ui.theme.LocalAppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentalScreen(
    onNavigateBack: () -> Unit,
    viewModel: ParentalViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let { snackbarHost.showSnackbar(it); viewModel.clearSnackbar() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.FamilyRestroom, null,
                            tint = LocalAppColors.current.cyan, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Parental Controls", fontWeight = FontWeight.Bold,
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
                    containerColor = LocalAppColors.current.card,
                    contentColor = LocalAppColors.current.textPrimary,
                    snackbarData = data)
            }
        },
        containerColor = LocalAppColors.current.navy,
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
            ScreenTimeCard(
                enabled    = state.screenTimeEnabled,
                startTime  = state.screenTimeStart,
                endTime    = state.screenTimeEnd,
                onToggle   = viewModel::setScreenTimeEnabled,
                onSetStart = viewModel::setScreenTimeStart,
                onSetEnd   = viewModel::setScreenTimeEnd,
            )

            ParentalToggleCard(
                icon      = Icons.Outlined.Email,
                title     = "Block Email Apps",
                subtitle  = if (state.emailBlocked)
                    "Gmail, Outlook and other email apps are hidden"
                else
                    "Email apps are visible and accessible",
                checked   = state.emailBlocked,
                onToggle  = viewModel::setEmailBlocked,
                color     = LocalAppColors.current.yellow,
            )

            ParentalToggleCard(
                icon      = Icons.Outlined.Sms,
                title     = "Block Text Messaging",
                subtitle  = if (state.smsBlocked)
                    "SMS/MMS blocked at OS level — messaging apps hidden"
                else
                    "Text messaging is enabled",
                checked   = state.smsBlocked,
                onToggle  = viewModel::setSmsBlocked,
                color     = LocalAppColors.current.purple,
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Screen time card ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenTimeCard(
    enabled: Boolean,
    startTime: String,
    endTime: String,
    onToggle: (Boolean) -> Unit,
    onSetStart: (String) -> Unit,
    onSetEnd: (String) -> Unit,
) {
    val colors = LocalAppColors.current
    val bg     = if (enabled) Color(0xFF0A1520) else Color(0xFF1A1A1A)
    val border = if (enabled) colors.cyan.copy(alpha = 0.5f) else colors.cardBorder

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker   by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = bg),
        border   = BorderStroke(1.dp, border)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Schedule, null,
                    tint = if (enabled) colors.cyan else colors.textSecondary,
                    modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (enabled) "Screen Time Active" else "Screen Time Off",
                        fontWeight = FontWeight.Bold,
                        color = if (enabled) colors.cyan else colors.textSecondary,
                        fontSize = 16.sp
                    )
                    Text(
                        text = if (enabled) "Device locks at $endTime daily" else "Set a daily usage window",
                        color = colors.textSecondary, fontSize = 12.sp
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor    = colors.cyan,
                        uncheckedTrackColor  = colors.navy,
                        uncheckedBorderColor = colors.cardBorder
                    )
                )
            }

            // Time pickers — always visible so the schedule can be set before enabling
            HorizontalDivider(color = colors.cardBorder)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TimePickerButton(
                    label     = "Available from",
                    time      = startTime,
                    color     = colors.green,
                    modifier  = Modifier.weight(1f),
                    onClick   = { showStartPicker = true }
                )
                TimePickerButton(
                    label     = "Locks at",
                    time      = endTime,
                    color     = colors.red,
                    modifier  = Modifier.weight(1f),
                    onClick   = { showEndPicker = true }
                )
            }
        }
    }

    if (showStartPicker) {
        val (h, m) = ScreenTimeScheduler.parse(startTime)
        TimePickerDialog(
            initialHour   = h,
            initialMinute = m,
            title         = "Device available from",
            onDismiss     = { showStartPicker = false },
            onConfirm     = { hour, min ->
                showStartPicker = false
                onSetStart(ScreenTimeScheduler.format(hour, min))
            }
        )
    }

    if (showEndPicker) {
        val (h, m) = ScreenTimeScheduler.parse(endTime)
        TimePickerDialog(
            initialHour   = h,
            initialMinute = m,
            title         = "Device locks at",
            onDismiss     = { showEndPicker = false },
            onConfirm     = { hour, min ->
                showEndPicker = false
                onSetEnd(ScreenTimeScheduler.format(hour, min))
            }
        )
    }
}

@Composable
private fun TimePickerButton(
    label: String,
    time: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    OutlinedButton(
        onClick  = onClick,
        modifier = modifier,
        shape    = RoundedCornerShape(12.dp),
        colors   = ButtonDefaults.outlinedButtonColors(contentColor = color),
        border   = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 10.sp, color = colors.textSecondary)
            Spacer(Modifier.height(2.dp))
            Text(time, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val pickerState = rememberTimePickerState(
        initialHour   = initialHour,
        initialMinute = initialMinute,
        is24Hour      = true,
    )
    val colors = LocalAppColors.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape  = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.card)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(title, fontWeight = FontWeight.Bold,
                    color = colors.textPrimary, fontSize = 15.sp)
                TimePicker(
                    state  = pickerState,
                    colors = TimePickerDefaults.colors(
                        clockDialColor          = colors.navy,
                        clockDialSelectedContentColor = colors.navy,
                        clockDialUnselectedContentColor = colors.textPrimary,
                        selectorColor           = colors.cyan,
                        containerColor          = colors.card,
                        timeSelectorSelectedContainerColor   = colors.cyanDim,
                        timeSelectorUnselectedContainerColor = colors.navy,
                        timeSelectorSelectedContentColor     = colors.cyan,
                        timeSelectorUnselectedContentColor   = colors.textSecondary,
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = colors.textSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(pickerState.hour, pickerState.minute) },
                        colors  = ButtonDefaults.buttonColors(containerColor = colors.cyan)
                    ) {
                        Text("Set", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ── Generic parental toggle card ──────────────────────────────────────────────

@Composable
private fun ParentalToggleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    color: Color,
) {
    val colors = LocalAppColors.current
    val bg     = if (checked) Color(0xFF1A1520) else Color(0xFF1A1A1A)
    val border = if (checked) color.copy(alpha = 0.5f) else colors.cardBorder

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = bg),
        border   = BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null,
                tint = if (checked) color else colors.textSecondary,
                modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold,
                    color = if (checked) color else colors.textSecondary, fontSize = 15.sp)
                Text(subtitle, color = colors.textSecondary, fontSize = 12.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedTrackColor    = color,
                    uncheckedTrackColor  = colors.navy,
                    uncheckedBorderColor = colors.cardBorder
                )
            )
        }
    }
}
