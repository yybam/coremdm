package com.core.mdm.ui.org

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.core.mdm.org.OrgState
import com.core.mdm.ui.dashboard.PolicyCard
import com.core.mdm.ui.theme.*
import kotlinx.coroutines.launch

/** Join an organization with its code, or see which organization this phone belongs to. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrgScreen(onNavigateBack: () -> Unit) {
    val c = LocalAppColors.current
    val orgId by OrgState.orgId.collectAsStateWithLifecycle()
    val profile by OrgState.profile.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.siteBackground(),
        topBar = {
            TopAppBar(
                title = { Text("Organization", fontWeight = FontWeight.Bold, color = c.textPrimary, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = c.cyan) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.navyLight, titleContentColor = c.textPrimary)
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (orgId != null) MemberCard(profile) else JoinCard()
        }
    }
}

@Composable
private fun MemberCard(profile: OrgState.Profile?) {
    val c = LocalAppColors.current
    val name = OrgState.name()
    val locked = profile?.rules.orEmpty().count { it.value.mode == "locked" }
    PolicyCard(icon = Icons.Outlined.Apartment, title = "This phone belongs to $name", iconColor = c.purple) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OrgLogo(size = 72.dp)
            Text(name, fontWeight = FontWeight.Bold, color = c.textPrimary, fontSize = 18.sp)
            profile?.message?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = c.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            Text(
                "$name's admins set rules for this phone from the CORE MDM website. " +
                    (if (locked > 0) "$locked setting${if (locked == 1) " is" else "s are"} locked and can't be changed here. " else "") +
                    "Only $name's admin can take this phone out of the organization.",
                color = c.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun JoinCard() {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<Pair<String, OrgState.Profile>?>(null) }

    fun find() {
        if (busy) return
        busy = true; error = null
        scope.launch {
            found = runCatching { OrgState.lookUp(code) }.getOrElse { error = "Couldn't check the code — are you online?"; null }
            if (found == null && error == null) error = "No organization has that code. Check it and try again."
            busy = false
        }
    }

    PolicyCard(icon = Icons.Outlined.Apartment, title = "Join an organization", iconColor = c.purple) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("If a charity, school, group or company gave you a join code, type it here.",
                color = c.textSecondary, fontSize = 13.sp)
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(12); error = null },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. 7KX2M9QD", color = c.textSecondary) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { find() }),
                shape = RoundedCornerShape(10.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 18.sp, color = c.textPrimary),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.cyan, unfocusedBorderColor = c.cardBorder,
                    focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary, cursorColor = c.cyan),
            )
            error?.let { Text(it, color = c.red, fontSize = 12.sp) }
            GradientButton(onClick = { find() }, modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp), enabled = code.length >= 8 && !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Find organization", fontWeight = FontWeight.SemiBold)
            }
        }
    }

    found?.let { (orgId, profile) ->
        val name = profile.name.ifBlank { "this organization" }
        AlertDialog(
            onDismissRequest = { if (!busy) found = null },
            containerColor = c.panel,
            icon = { OrgLogo(size = 64.dp, logo = profile.logo) },
            title = { Text("Join $name?", color = c.textPrimary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) },
            text = {
                Text(
                    "Joining $name means:\n\n" +
                        "• $name's admins can set rules on this phone, including ones you can't change here.\n" +
                        "• \"$name\", its logo and message appear on the CORE MDM lock screen.\n" +
                        "• Only $name's admin can take this phone out again.",
                    color = c.textSecondary, fontSize = 14.sp,
                )
            },
            confirmButton = {
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching { OrgState.join(context, orgId, code) }
                                .onSuccess { found = null; code = "" }
                                .onFailure { error = "Couldn't join: ${it.message}"; found = null }
                            busy = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = c.purple),
                ) { Text("Join $name") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { found = null }) { Text("Cancel", color = c.textSecondary) }
            },
        )
    }
}

/** The organization's logo, or the CoreMDM logo when it has none. */
@Composable
fun OrgLogo(size: Dp, logo: String? = null) {
    val bmp = remember(logo, OrgState.profile.value?.logo) {
        if (logo != null) runCatching {
            val bytes = android.util.Base64.decode(logo.substringAfter("base64,"), android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull() else OrgState.logoBitmap()
    }
    if (bmp != null) Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.size(size).clip(RoundedCornerShape(16.dp)))
    else BrandLogo(size = size)
}
