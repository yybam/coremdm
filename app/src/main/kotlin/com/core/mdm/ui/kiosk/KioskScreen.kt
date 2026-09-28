package com.core.mdm.ui.kiosk

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.core.mdm.ui.theme.LocalAppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KioskScreen(
    onNavigateBack: () -> Unit,
    viewModel: KioskViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val c = LocalAppColors.current

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let { snackbarHost.showSnackbar(it); viewModel.clearSnackbar() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LockPerson, null, tint = c.orange, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Kiosk / Lock Task Mode", fontWeight = FontWeight.Bold,
                            color = c.textPrimary, fontSize = 18.sp)
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
        snackbarHost = {
            SnackbarHost(snackbarHost) { data ->
                Snackbar(containerColor = c.card, contentColor = c.textPrimary,
                    modifier = Modifier.padding(12.dp), snackbarData = data)
            }
        },
        containerColor = c.navy
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Status card ───────────────────────────────────────────────────
            item {
                PolicyCard(
                    icon      = if (state.isEnabled) Icons.Filled.Lock else Icons.Outlined.LockOpen,
                    title     = "Lock Task Status",
                    iconColor = if (state.isEnabled) c.orange else c.textSecondary
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (state.isEnabled) "Kiosk Active — ${state.allowedPackages.size} package(s)"
                                else "Kiosk Disabled",
                                fontWeight = FontWeight.SemiBold,
                                color = if (state.isEnabled) c.orange else c.textSecondary,
                                fontSize = 14.sp
                            )
                            Text(
                                "Requires Device Owner",
                                color = if (state.isDeviceOwner) c.green else c.red,
                                fontSize = 11.sp
                            )
                        }
                        if (state.isEnabled) {
                            OutlinedButton(
                                onClick  = viewModel::disableKiosk,
                                shape    = RoundedCornerShape(10.dp),
                                colors   = ButtonDefaults.outlinedButtonColors(contentColor = c.red),
                                border   = androidx.compose.foundation.BorderStroke(1.dp, c.red.copy(alpha = 0.4f))
                            ) { Text("Disable", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                        }
                    }
                }
            }

            // ── Explainer ─────────────────────────────────────────────────────
            item {
                Card(
                    shape  = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = c.orange.copy(alpha = 0.06f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, c.orange.copy(alpha = 0.2f))
                ) {
                    Text(
                        "Lock Task Mode prevents users from leaving an app. Add the package name(s) " +
                        "of allowed apps below. The target app must call startLockTask() from its own Activity.",
                        color    = c.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            // ── Add package ───────────────────────────────────────────────────
            item {
                var input by remember { mutableStateOf("") }
                val focusManager = LocalFocusManager.current

                PolicyCard(icon = Icons.Outlined.Add, title = "Add Allowed Package", iconColor = c.green) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("com.example.app", color = c.textSecondary, fontSize = 12.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                viewModel.addPackage(input); input = ""; focusManager.clearFocus()
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = c.green,
                                unfocusedBorderColor = c.cardBorder,
                                focusedTextColor     = c.textPrimary,
                                unfocusedTextColor   = c.textPrimary,
                                cursorColor          = c.green
                            ),
                            shape = RoundedCornerShape(10.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                color = c.textPrimary)
                        )
                        Button(
                            onClick = { viewModel.addPackage(input); input = ""; focusManager.clearFocus() },
                            shape  = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = c.green.copy(alpha = 0.2f))
                        ) { Text("Add", color = c.green, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }

            // ── Package list ──────────────────────────────────────────────────
            if (state.allowedPackages.isNotEmpty()) {
                item {
                    PolicyCard(icon = Icons.Outlined.List, title = "Allowed Packages", iconColor = c.cyan) {
                        state.allowedPackages.forEachIndexed { idx, pkg ->
                            if (idx > 0) PolicyDivider()
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(pkg, Modifier.weight(1f), color = c.textPrimary,
                                    fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                                IconButton(onClick = { viewModel.removePackage(pkg) },
                                    modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Filled.Close, "Remove",
                                        tint = c.red, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
