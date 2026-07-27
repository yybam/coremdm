package com.core.mdm.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.core.mdm.ui.theme.LocalAppColors
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

private const val GOOGLE_WEB_CLIENT_ID = "457812602789-4ha9mpjq8rirbge9j5l55qsitgfkt8en.apps.googleusercontent.com"

@Composable
fun LoginScreen(vm: LoginViewModel = viewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val c = LocalAppColors.current
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSignUp by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.navy)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(Icons.Filled.Security, null, tint = c.cyan, modifier = Modifier.size(56.dp))
            Text("CORE MDM", fontWeight = FontWeight.Bold, color = c.textPrimary, fontSize = 24.sp, letterSpacing = 2.sp)
            Text(
                if (isSignUp) "Create your account" else "Sign in to manage your devices",
                color = c.textSecondary,
                fontSize = 13.sp
            )

            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; vm.clearError() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                leadingIcon = { Icon(Icons.Outlined.Email, null, tint = c.textSecondary) },
                colors = fieldColors(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; vm.clearError() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    if (isSignUp) vm.signUp(email, password) else vm.signIn(email, password)
                }),
                leadingIcon  = { Icon(Icons.Outlined.Lock, null, tint = c.textSecondary) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            null, tint = c.textSecondary
                        )
                    }
                },
                colors = fieldColors(),
                shape = RoundedCornerShape(12.dp)
            )

            AnimatedVisibility(visible = state.error != null) {
                Text(state.error ?: "", color = c.red, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
            }

            Button(
                onClick = {
                    focusManager.clearFocus()
                    if (isSignUp) vm.signUp(email, password) else vm.signIn(email, password)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.cyan),
                enabled = !state.isLoading && email.isNotBlank() && password.isNotBlank()
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        if (isSignUp) "Create Account" else "Sign In",
                        color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = c.cardBorder)
                Text("  or  ", color = c.textSecondary, fontSize = 12.sp)
                HorizontalDivider(modifier = Modifier.weight(1f), color = c.cardBorder)
            }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        val credentialManager = CredentialManager.create(context)
                        val googleIdOption = GetGoogleIdOption.Builder()
                            .setFilterByAuthorizedAccounts(false)
                            .setServerClientId(GOOGLE_WEB_CLIENT_ID)
                            .build()
                        val request = GetCredentialRequest.Builder()
                            .addCredentialOption(googleIdOption)
                            .build()
                        try {
                            val result = credentialManager.getCredential(context, request)
                            val googleCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
                            vm.signInWithGoogleToken(googleCredential.idToken)
                        } catch (e: GetCredentialException) {
                            vm.setError(e.message ?: "Google Sign-In failed")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !state.isLoading,
                border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textPrimary)
            ) {
                Text("Continue with Google", fontWeight = FontWeight.Medium, fontSize = 15.sp)
            }

            TextButton(onClick = { isSignUp = !isSignUp; vm.clearError() }) {
                Text(
                    if (isSignUp) "Already have an account? Sign in" else "New here? Create an account",
                    color = c.cyan, fontSize = 13.sp
                )
            }

            AnimatedVisibility(visible = !isSignUp) {
                TextButton(onClick = { showResetDialog = true }) {
                    Text("Forgot password?", color = c.textSecondary, fontSize = 12.sp)
                }
            }
        }
    }

    if (showResetDialog) {
        var resetEmail by remember { mutableStateOf(email) }
        AlertDialog(
            onDismissRequest = { showResetDialog = false; vm.clearResetSent() },
            containerColor = c.card,
            icon  = { Icon(Icons.Outlined.LockReset, null, tint = c.cyan) },
            title = { Text("Reset Password", color = c.textPrimary) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.resetEmailSent) {
                        Text("Reset link sent! Check your inbox.", color = c.green, fontSize = 13.sp)
                    } else {
                        Text("We'll email you a link to reset your password.", color = c.textSecondary, fontSize = 13.sp)
                        OutlinedTextField(
                            value = resetEmail,
                            onValueChange = { resetEmail = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Email") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            colors = fieldColors(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                if (state.resetEmailSent) {
                    Button(
                        onClick = { showResetDialog = false; vm.clearResetSent() },
                        colors  = ButtonDefaults.buttonColors(containerColor = c.cyan)
                    ) { Text("Done", color = Color.Black, fontWeight = FontWeight.Bold) }
                } else {
                    Button(
                        onClick  = { vm.sendPasswordReset(resetEmail) },
                        enabled  = !state.isLoading && resetEmail.isNotBlank(),
                        colors   = ButtonDefaults.buttonColors(containerColor = c.cyan)
                    ) { Text("Send Link", color = Color.Black, fontWeight = FontWeight.Bold) }
                }
            },
            dismissButton = {
                if (!state.resetEmailSent) {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("Cancel", color = c.textSecondary)
                    }
                }
            }
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor    = LocalAppColors.current.cyan,
    unfocusedBorderColor  = LocalAppColors.current.cardBorder,
    focusedTextColor      = LocalAppColors.current.textPrimary,
    unfocusedTextColor    = LocalAppColors.current.textPrimary,
    cursorColor           = LocalAppColors.current.cyan,
    focusedLabelColor     = LocalAppColors.current.cyan,
    unfocusedLabelColor   = LocalAppColors.current.textSecondary,
)
