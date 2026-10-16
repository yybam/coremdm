package com.core.mdm.ui.remote

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.core.mdm.ui.theme.*

/**
 * Shown instead of the dashboard when the website turns on "Remote-only mode": nothing can be
 * changed on the phone itself.
 */
@Composable
fun RemoteOnlyScreen() {
    val c = LocalAppColors.current
    Box(
        Modifier.fillMaxSize().siteBackground().safeDrawingPadding().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BrandLogo(size = 88.dp)
            Text("Managed remotely", fontWeight = FontWeight.Bold, color = c.textPrimary, fontSize = 26.sp)
            Text(
                "This phone's settings are controlled by its administrator from the CORE MDM website. " +
                    "Nothing can be changed here.",
                color = c.textSecondary, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center
            )
        }
    }
}
