package com.core.mdm.ui.pin

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.core.mdm.ui.theme.*
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextDecoration

/**
 * Single composable used for both the lock overlay (VERIFY mode, back blocked)
 * and the in-nav PIN setup screen (SETUP/CHANGE mode, back allowed).
 *
 * @param preventBack   true when shown as a lock overlay over the dashboard.
 * @param onAuthenticated   called when VERIFY succeeds.
 * @param onComplete        called when SETUP or CHANGE finishes.
 */
@Composable
fun PinLockScreen(
    preventBack: Boolean = true,
    onAuthenticated: () -> Unit = {},
    onComplete: () -> Unit = {},
    viewModel: PinLockViewModel = viewModel(factory = PinLockViewModel.factory()),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope  = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Block or allow back navigation
    BackHandler(enabled = preventBack) { /* intentionally suppressed while locked */ }

    // Callbacks on terminal states
    LaunchedEffect(state.isAuthenticated) { if (state.isAuthenticated) onAuthenticated() }
    LaunchedEffect(state.isSetupComplete) { if (state.isSetupComplete) onComplete() }

    // Remove PIN confirmation dialog
    if (state.showRemoveDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissRemoveDialog,
            containerColor   = LocalAppColors.current.card,
            icon = {
                Icon(Icons.Filled.LockOpen, null, tint = LocalAppColors.current.red, modifier = Modifier.size(32.dp))
            },
            title = {
                Text("Remove PIN Lock?", color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "This will remove the MDM app lock entirely. You will need to enter your " +
                    "current PIN to confirm removal.",
                    color    = LocalAppColors.current.textSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmRemovePin,
                    colors  = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.red)
                ) { Text("Enter PIN to Confirm") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRemoveDialog) {
                    Text("Cancel", color = LocalAppColors.current.textSecondary)
                }
            }
        )
    }

    // Show snackbar for success messages
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    // Shake animation triggered by error
    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(state.errorMessage) {
        if (state.errorMessage != null) {
            scope.launch {
                shakeOffset.animateTo(0f, keyframes {
                    durationMillis = 400
                    0f   at  0
                    (-12f) at  60 using LinearEasing
                    12f  at 130 using LinearEasing
                    (-8f) at 200 using LinearEasing
                    8f   at 270 using LinearEasing
                    0f   at 360
                })
            }
        }
    }

    val scrollState = rememberScrollState()

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    modifier      = Modifier.padding(12.dp),
                    containerColor = LocalAppColors.current.card,
                    contentColor  = LocalAppColors.current.textPrimary,
                    snackbarData  = data
                )
            }
        },
        containerColor = LocalAppColors.current.navy
    ) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(LocalAppColors.current.navy)
                .verticalScroll(scrollState)
                .padding(horizontal = 32.dp, vertical = 40.dp)
        ) {
                // ── Icon + title ──────────────────────────────────────────────
                PinHeader(state)

                // ── Dots ──────────────────────────────────────────────────────
                PinDotsRow(
                    filled = state.digits.length,
                    modifier = Modifier.offset(x = shakeOffset.value.dp)
                )

                // ── Error message ─────────────────────────────────────────────
                AnimatedVisibility(
                    visible = state.errorMessage != null,
                    enter   = fadeIn(),
                    exit    = fadeOut()
                ) {
                    Text(
                        text      = state.errorMessage ?: "",
                        color     = LocalAppColors.current.red,
                        fontSize  = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }

                // ── Lockout card or numpad ────────────────────────────────────
                if (state.isLockedOut) {
                    LockoutCard(state.lockoutSecondsRemaining)
                } else {
                    NumPad(
                        onDigit     = viewModel::onDigit,
                        onBackspace = viewModel::onBackspace,
                        enabled     = !state.isLockedOut
                    )
                }

                // ── Remove PIN button (only in manage-PIN context, not lock overlay) ──
                if (!preventBack && state.mode == PinScreenMode.CHANGE_VERIFY) {
                    TextButton(onClick = viewModel::requestRemovePin) {
                        Text(
                            text            = "Remove PIN Lock",
                            color           = LocalAppColors.current.red.copy(alpha = 0.8f),
                            fontSize        = 13.sp,
                            textDecoration  = TextDecoration.Underline
                        )
                    }
                }

                // ── Context hint ──────────────────────────────────────────────
                Text(
                    text = when (state.mode) {
                        PinScreenMode.VERIFY         -> "Enter your PIN to continue"
                        PinScreenMode.SETUP          -> "Choose a PIN (4–6 digits)"
                        PinScreenMode.SETUP_CONFIRM  -> "Re-enter PIN to confirm"
                        PinScreenMode.CHANGE_VERIFY  -> "Enter your current PIN"
                        PinScreenMode.CHANGE_NEW     -> "Enter new PIN (4–6 digits)"
                        PinScreenMode.CHANGE_CONFIRM -> "Re-enter new PIN to confirm"
                        PinScreenMode.REMOVE_VERIFY  -> "Enter current PIN to confirm removal"
                    },
                    color    = LocalAppColors.current.textSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
        }
    }
}

// ── Header ────────────────────────────────────────────────────────────────────────

@Composable
private fun PinHeader(state: PinLockUiState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(LocalAppColors.current.cyan.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (state.mode == PinScreenMode.VERIFY) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = null,
                tint = LocalAppColors.current.cyan,
                modifier = Modifier.size(34.dp)
            )
        }
        Text(
            text = when (state.mode) {
                PinScreenMode.VERIFY                               -> "CORE MDM"
                PinScreenMode.SETUP, PinScreenMode.SETUP_CONFIRM  -> "Set Admin PIN"
                PinScreenMode.REMOVE_VERIFY                        -> "Remove PIN Lock"
                else                                               -> "Change PIN"
            },
            fontWeight = FontWeight.Bold,
            color      = LocalAppColors.current.textPrimary,
            fontSize   = 22.sp
        )
        Text("Managed Device", color = LocalAppColors.current.textSecondary, fontSize = 12.sp)
    }
}

// ── PIN dots ──────────────────────────────────────────────────────────────────────

@Composable
private fun PinDotsRow(filled: Int, modifier: Modifier = Modifier) {
    Row(
        modifier              = modifier,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        repeat(4) { index ->
            val isFilled = index < filled
            val scale by animateFloatAsState(
                targetValue  = if (isFilled) 1f else 0.85f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label        = "dot_scale_$index"
            )
            val bgColor by animateColorAsState(
                targetValue  = if (isFilled) LocalAppColors.current.cyan else Color.Transparent,
                animationSpec = tween(120),
                label        = "dot_color_$index"
            )
            Box(
                modifier = Modifier
                    .scale(scale)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(bgColor)
                    .border(
                        width = 1.5.dp,
                        color = if (isFilled) LocalAppColors.current.cyan else LocalAppColors.current.cardBorder,
                        shape = CircleShape
                    )
            )
        }
    }
}

// ── Lockout card ──────────────────────────────────────────────────────────────────

@Composable
private fun LockoutCard(seconds: Int) {
    Card(
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E0808)),
        border = androidx.compose.foundation.BorderStroke(1.dp, LocalAppColors.current.red.copy(alpha = 0.35f))
    ) {
        Column(
            modifier              = Modifier.padding(horizontal = 36.dp, vertical = 22.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.Timer, null, tint = LocalAppColors.current.red, modifier = Modifier.size(30.dp))
            Text("Too many attempts", color = LocalAppColors.current.red, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(
                text     = "Try again in ${seconds}s",
                color    = LocalAppColors.current.textSecondary,
                fontSize = 13.sp
            )
        }
    }
}

// ── Numpad ────────────────────────────────────────────────────────────────────────

private val PAD_ROWS = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf("",  "0", "⌫"),
)

@Composable
private fun NumPad(onDigit: (String) -> Unit, onBackspace: () -> Unit, enabled: Boolean) {
    Column(
        verticalArrangement   = Arrangement.spacedBy(14.dp),
        horizontalAlignment   = Alignment.CenterHorizontally
    ) {
        PAD_ROWS.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                row.forEach { label ->
                    when (label) {
                        ""  -> Spacer(Modifier.size(74.dp))
                        "⌫" -> BackspaceKey(onClick = onBackspace, enabled = enabled)
                        else -> DigitKey(label = label, onClick = { onDigit(label) }, enabled = enabled)
                    }
                }
            }
        }
    }
}

@Composable
private fun DigitKey(label: String, onClick: () -> Unit, enabled: Boolean) {
    Surface(
        onClick  = onClick,
        modifier = Modifier.size(74.dp),
        shape    = CircleShape,
        color    = LocalAppColors.current.card,
        enabled  = enabled,
        border   = androidx.compose.foundation.BorderStroke(1.dp, LocalAppColors.current.cardBorder),
        tonalElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text       = label,
                fontSize   = 26.sp,
                fontWeight = FontWeight.Light,
                color      = if (enabled) LocalAppColors.current.textPrimary else LocalAppColors.current.textSecondary
            )
        }
    }
}

@Composable
private fun BackspaceKey(onClick: () -> Unit, enabled: Boolean) {
    Surface(
        onClick  = onClick,
        modifier = Modifier.size(74.dp),
        shape    = CircleShape,
        color    = Color.Transparent,
        enabled  = enabled
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector        = Icons.AutoMirrored.Outlined.Backspace,
                contentDescription = "Delete",
                tint               = if (enabled) LocalAppColors.current.textPrimary else LocalAppColors.current.textSecondary,
                modifier           = Modifier.size(26.dp)
            )
        }
    }
}
