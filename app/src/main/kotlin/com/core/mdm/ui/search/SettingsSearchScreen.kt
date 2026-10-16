package com.core.mdm.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.core.mdm.firebase.RemotePolicies
import com.core.mdm.ui.dashboard.DashboardViewModel
import com.core.mdm.ui.theme.*

/**
 * Finds any setting by name, by what it does, or by its old Android constant — "wifi",
 * "DISALLOW_SAFE_BOOT" and "screenshot" all land on the right switch. Matches can be flipped
 * here directly; entries that are whole screens open them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSearchScreen(
    onNavigateBack: () -> Unit,
    onOpenRoute: (String) -> Unit,
    viewModel: DashboardViewModel = viewModel(),
) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val policies by RemotePolicies.policies.collectAsState()
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val results = remember(query) { SettingsCatalog.search(query) }

    Scaffold(
        modifier = Modifier.siteBackground(),
        topBar = {
            TopAppBar(
                title = { Text("Search settings", fontWeight = FontWeight.Bold, color = c.textPrimary, fontSize = 19.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.navyLight)
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                    .focusRequester(focus),
                placeholder = { Text("Search any setting", color = c.textSecondary, fontSize = 15.sp) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Clear, "Clear search", tint = c.textSecondary)
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.accent, unfocusedBorderColor = c.cardBorder,
                    focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary,
                    cursorColor = c.accent, focusedContainerColor = c.card, unfocusedContainerColor = c.card
                )
            )

            when {
                query.isBlank() -> Hint()
                results.isEmpty() -> Empty(query)
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(results, key = { it.key }) { entry ->
                        ResultRow(
                            entry = entry,
                            checked = when (entry.kind) {
                                SettingsCatalog.Kind.LOCAL  -> SettingsCatalog.localValue(state, entry.key)
                                SettingsCatalog.Kind.REMOTE -> policies[entry.key] as? Boolean ?: false
                                SettingsCatalog.Kind.SCREEN -> false
                            },
                            onToggle = { on ->
                                when (entry.kind) {
                                    SettingsCatalog.Kind.LOCAL  -> viewModel.setByKey(entry.key, on)
                                    SettingsCatalog.Kind.REMOTE -> RemotePolicies.set(context, entry.key, on)
                                    SettingsCatalog.Kind.SCREEN -> {}
                                }
                            },
                            onOpen = { entry.route?.let(onOpenRoute) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(
    entry: SettingsCatalog.Entry,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    val c = LocalAppColors.current
    val isScreen = entry.kind == SettingsCatalog.Kind.SCREEN
    Row(
        Modifier.fillMaxWidth().clip(GlassShape).background(c.card)
            .border(1.dp, c.cardBorder, GlassShape)
            .then(if (isScreen) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.title, fontWeight = FontWeight.SemiBold, color = c.textPrimary, fontSize = 15.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.subtitle, color = c.textSecondary, fontSize = 12.sp, lineHeight = 16.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(entry.category, color = c.accent2, fontSize = 10.sp, letterSpacing = 0.6.sp)
        }
        Spacer(Modifier.width(10.dp))
        if (isScreen) Icon(Icons.Filled.ChevronRight, "Open", tint = c.textSecondary)
        else GlassSwitch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun Hint() {
    val c = LocalAppColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Try searching for:", color = c.textSecondary, fontSize = 13.sp)
        listOf("whatsapp", "camera", "wifi", "time limit", "calls", "screenshot", "chatgpt")
            .forEach { Text("•  $it", color = c.textSecondary, fontSize = 13.sp) }
    }
}

@Composable
private fun Empty(query: String) {
    val c = LocalAppColors.current
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.SearchOff, null, tint = c.textSecondary, modifier = Modifier.size(48.dp))
            Text("Nothing matches \"$query\"", color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
