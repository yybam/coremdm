package com.core.mdm.ui.message

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MessageAlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Message from Administrator"
        val text  = intent.getStringExtra(EXTRA_TEXT)  ?: ""

        setContent {
            MessageAlertScreen(title = title, text = text, onDismiss = { finish() })
        }
    }

    companion object {
        const val EXTRA_TITLE = "msg_title"
        const val EXTRA_TEXT  = "msg_text"
    }
}

@Composable
private fun MessageAlertScreen(title: String, text: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xDD000000)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .wrapContentHeight(),
            shape  = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1120)),
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("📢", fontSize = 42.sp)
                Spacer(Modifier.height(14.dp))
                Text(
                    title,
                    fontWeight  = FontWeight.Bold,
                    fontSize    = 18.sp,
                    color       = Color(0xFFEEF1F8),
                    textAlign   = TextAlign.Center,
                )
                if (text.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text,
                        fontSize   = 14.sp,
                        color      = Color(0xFF9CA8C4),
                        textAlign  = TextAlign.Center,
                        lineHeight = 20.sp,
                    )
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E9AE6)),
                ) {
                    Text("Dismiss", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}
