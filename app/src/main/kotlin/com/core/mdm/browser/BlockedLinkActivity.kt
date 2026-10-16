package com.core.mdm.browser

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.core.mdm.settings.ThemePrefs
import com.core.mdm.ui.theme.*

/**
 * CoreMDM registers as a browser so that, on a phone with no other browser installed, web links
 * open here instead of falling back to a stock browser the admin can't control. Rather than
 * rendering the page, this shows why it was blocked and closes.
 *
 * Android only offers the "default browser" choice when an app handles plain http/https VIEW
 * intents with CATEGORY_BROWSABLE and no host filter — hence the manifest entry — so this is
 * what makes CoreMDM eligible.
 */
class BlockedLinkActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemePrefs.init(this)
        enableEdgeToEdge()
        val url = intent?.data?.toString().orEmpty()
        setContent {
            val mode = ThemePrefs.themeMode.value
            CoreMdmTheme(themeMode = mode) {
                BlockedLink(url = url, onClose = { finish() })
            }
        }
    }

    companion object {
        /** True when this device has a browser other than CoreMDM. */
        fun hasOtherBrowser(context: Context): Boolean {
            val probe = Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com"))
                .addCategory(Intent.CATEGORY_BROWSABLE)
            @Suppress("DEPRECATION")
            return context.packageManager.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
                .any { it.activityInfo.packageName != context.packageName }
        }
    }
}

@Composable
private fun BlockedLink(url: String, onClose: () -> Unit) {
    val c = LocalAppColors.current
    Box(
        Modifier.fillMaxSize().siteBackground().safeDrawingPadding().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().clip(GlassShape).background(c.card)
                .border(1.dp, c.cardBorder, GlassShape).padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(c.red.copy(alpha = 0.12f))
                    .border(1.dp, c.red.copy(alpha = 0.3f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.Block, null, tint = c.red, modifier = Modifier.size(30.dp)) }

            Text("Web browsing is off", fontWeight = FontWeight.Bold, color = c.textPrimary, fontSize = 21.sp)
            Text(
                "This phone's administrator has turned off web browsing. The link you tapped wasn't opened.",
                color = c.textSecondary, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center
            )
            if (url.isNotBlank()) {
                Text(
                    url.take(120),
                    color = c.textSecondary.copy(alpha = 0.7f), fontSize = 11.sp,
                    textAlign = TextAlign.Center, maxLines = 2,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(c.navy.copy(alpha = 0.35f)).padding(10.dp)
                )
            }
            GradientButton(onClick = onClose, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("Close", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}
