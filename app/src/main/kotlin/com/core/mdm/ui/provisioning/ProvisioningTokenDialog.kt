package com.core.mdm.ui.provisioning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@Composable
fun ProvisioningTokenDialog(
    state:         ProvisioningState,
    onTokenChange: (String) -> Unit,
    onVerify:      () -> Unit,
    onSkip:        () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { /* block back-tap; user must choose */ },
        properties       = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("Enrollment Token") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "If your admin gave you an enrollment token, paste it below to register this device.\n" +
                    "Otherwise, tap Skip — your account sign-in is all you need.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value         = state.token,
                    onValueChange = onTokenChange,
                    label         = { Text("Enrollment token") },
                    singleLine    = true,
                    isError       = state.error != null,
                    supportingText = state.error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        imeAction      = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onVerify() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick  = onVerify,
                enabled  = !state.isLoading && state.token.isNotBlank(),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier  = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color     = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Verify & Enroll")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip, enabled = !state.isLoading) {
                Text("Skip")
            }
        },
    )
}
