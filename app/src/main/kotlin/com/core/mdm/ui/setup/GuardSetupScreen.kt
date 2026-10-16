package com.core.mdm.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.core.mdm.content.GuardPolicy
import com.core.mdm.ui.theme.*

/**
 * First-run gate: the screen guard has to be switched on before the phone can be signed in, so
 * a device is never enrolled without the piece that enforces section and WebView rules.
 */
@Composable
fun GuardSetupScreen(onReady: () -> Unit) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(GuardPolicy.isEnabled(context)) }

    // Re-check each time we come back from Android's settings screen
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { enabled = GuardPolicy.isEnabled(context) }
    LaunchedEffect(enabled) { if (enabled) GuardPolicy.lockToThisService(context, true) }

    Box(
        Modifier.fillMaxSize().siteBackground().safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                Modifier.widthIn(max = 460.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(c.card).border(1.dp, c.cardBorder, RoundedCornerShape(24.dp))
                    .padding(horizontal = 24.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BrandLogo(size = 64.dp)
                Text("Set up CORE MDM", fontWeight = FontWeight.Bold, color = c.textPrimary,
                    fontSize = 24.sp, letterSpacing = (-0.5).sp)
                Text(
                    "One step before you sign in: turn on the CORE MDM screen guard.",
                    color = c.textSecondary, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center
                )

                Column(
                    Modifier.fillMaxWidth().clip(GlassShape)
                        .background(c.accent.copy(alpha = 0.08f))
                        .border(1.dp, c.accent.copy(alpha = 0.25f), GlassShape)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Shield, null, tint = c.cyan, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("What it does", fontWeight = FontWeight.SemiBold, color = c.textPrimary, fontSize = 15.sp)
                    }
                    listOf(
                        "Blocks single sections of apps — WhatsApp Status and Channels, Instagram Reels, YouTube Shorts — while the rest of the app keeps working.",
                        "Blocks web pages inside apps, except the apps you allow.",
                        "It only checks which screen is open so it can close it. Nothing is recorded, saved or sent anywhere.",
                    ).forEach { line ->
                        Text("•  $line", color = c.textSecondary, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }

                if (enabled) {
                    Row(
                        Modifier.fillMaxWidth().clip(GlassShape).background(c.green.copy(alpha = 0.1f))
                            .border(1.dp, c.green.copy(alpha = 0.35f), GlassShape).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(26.dp).clip(CircleShape).background(c.green), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Check, null, tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("Screen guard is on", fontWeight = FontWeight.SemiBold, color = c.green, fontSize = 15.sp)
                    }
                    GradientButton(onClick = onReady, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("Continue to sign in", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                } else {
                    Text(
                        "Tap below, choose \"CORE MDM screen guard\" in the list, and turn it on. " +
                            "Android asks you to confirm — then come back here.",
                        color = c.textSecondary, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center
                    )
                    GradientButton(
                        onClick = { GuardPolicy.openSetup(context) },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text("Open Accessibility settings", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                    TextButton(onClick = { enabled = GuardPolicy.isEnabled(context) }) {
                        Text("I've turned it on — check again", color = c.cyan, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
