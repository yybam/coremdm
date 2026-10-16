package com.core.mdm.ui.filter

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.core.mdm.ui.dashboard.PolicyCard
import com.core.mdm.ui.dashboard.PolicyDivider
import com.core.mdm.ui.theme.GlassSwitch
import com.core.mdm.ui.theme.LocalAppColors
import com.core.mdm.vpn.DnsFilter
import com.core.mdm.vpn.FilterSettings
import com.core.mdm.vpn.MatchMode
import com.core.mdm.vpn.PrivateDnsGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val GROUP_LABELS = linkedMapOf(
    "ads"                    to "Ads",
    "analytics"              to "Analytics & tracking",
    "social"                 to "Social media",
    "video"                  to "Video (YouTube)",
    "gifs-images"            to "GIFs & keyboard pictures",
    "spotify-music"          to "Spotify & music images",
    "google-apple-microsoft" to "Google, Apple & Microsoft pages",
    "app-help-pages"         to "Help & about pages inside apps",
    "cloud-infra"            to "Cloud & CDN hosts",
    "other-sites"            to "Other sites",
    "google-maps-images"     to "Google Maps images & YouTube",
)

/**
 * Per-group switches for the bundled blocklists, plus the match mode. Changes apply to the
 * running filter straight away (DnsFilter rebuilds its matcher when a setting changes).
 */
@Composable
fun BlocklistGroupsCard() {
    val context  = LocalContext.current
    val settings = remember { FilterSettings.getInstance(context) }

    val sizes by produceState(initialValue = emptyMap<String, Int>()) {
        value = withContext(Dispatchers.IO) { DnsFilter.getInstance(context).groupSizes() }
    }
    val groups = remember(sizes) {
        GROUP_LABELS.keys.filter { it in sizes } + sizes.keys.filter { it !in GROUP_LABELS }.sorted()
    }
    val groupOn = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(groups) { groups.forEach { groupOn[it] = settings.isGroupEnabled(it) } }

    var subdomains by remember { mutableStateOf(settings.matchMode == MatchMode.SUBDOMAIN) }
    val privateDnsBypass = remember { PrivateDnsGuard.isBypassing(context) }

    PolicyCard(
        icon        = Icons.Outlined.Category,
        title       = "Blocklist Groups",
        iconColor   = LocalAppColors.current.red,
        summary     = "${groups.count { groupOn[it] == true }} of ${groups.size} on",
        collapsible = true,
    ) {
        SwitchRow(
            title    = "Block subdomains too",
            subtitle = if (subdomains) "example.com also blocks www.example.com, m.example.com…"
                       else "Exact names only, like a hosts file",
            checked  = subdomains,
            onChange = {
                subdomains = it
                settings.matchMode = if (it) MatchMode.SUBDOMAIN else MatchMode.EXACT
                com.core.mdm.firebase.RemotePolicies.record(context, com.core.mdm.firebase.LocalSettingsSync.FILTER_SUBDOMAINS, it)
            },
        )
        if (privateDnsBypass) {
            Text(
                "Private DNS is set to a custom server, so lookups go around this filter. " +
                    "Turn Private DNS off or to Automatic in Settings › Network.",
                color = LocalAppColors.current.orange, fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        PolicyDivider()
        groups.forEach { group ->
            SwitchRow(
                title    = GROUP_LABELS[group] ?: group,
                subtitle = when (group) {
                    "google-maps-images" -> "Map photos & Street View images, Google image hosts, YouTube site and video"
                    "gifs-images"        -> "Hides GIFs and pictures in the keyboard (Gboard GIFs and stickers) and on GIF sites"
                    else                 -> "%,d sites".format(sizes[group] ?: 0)
                },
                checked  = groupOn[group] ?: settings.isGroupEnabled(group),
                onChange = {
                    groupOn[group] = it
                    settings.setGroupEnabled(group, it)
                    val sync = com.core.mdm.firebase.LocalSettingsSync
                    if (group in sync.OPT_IN_GROUPS) {
                        com.core.mdm.firebase.RemotePolicies.record(context, sync.FILTER_GROUPS_ON,
                            sync.OPT_IN_GROUPS.filter { g -> settings.isGroupEnabled(g) })
                    } else {
                        com.core.mdm.firebase.RemotePolicies.record(context, sync.FILTER_GROUPS_OFF,
                            sync.FILTER_GROUPS.filterNot { g -> settings.isGroupEnabled(g) })
                    }
                },
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold,
                color = LocalAppColors.current.textPrimary, fontSize = 14.sp)
            Text(subtitle, color = LocalAppColors.current.textSecondary, fontSize = 11.sp)
        }
        GlassSwitch(checked = checked, onCheckedChange = onChange)
    }
}
