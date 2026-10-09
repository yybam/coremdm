package com.core.mdm.firebase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.core.mdm.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.core.mdm.remote.AlarmController

class MdmFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        DeviceRegistry.storeFcmToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        when (message.data["type"]) {
            "alarm_start"      -> AlarmController.playAlarm(applicationContext)
            "alarm_stop"       -> AlarmController.stopAlarm()
            "update_available" -> showUpdateNotification(
                version    = message.data["version"] ?: "New",
                downloadUrl = message.data["url"]    ?: INSTALLER_URL,
            )
        }
    }

    private fun showUpdateNotification(version: String, downloadUrl: String) {
        val nm = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    UPDATE_CHANNEL_ID,
                    "App Updates",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "CoreMDM app update notifications" }
            )
        }

        val openIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, UPDATE_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("CoreMDM Update Available")
            .setContentText("Version $version is ready — tap to download and install.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Version $version is ready. Tap \"Download\" to get the latest CoreMDM APK and install it."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, "Download", openIntent)
            .setContentIntent(openIntent)
            .build()

        nm.notify(UPDATE_NOTIF_ID, notification)
    }

    companion object {
        private const val UPDATE_CHANNEL_ID = "mdm_update"
        private const val UPDATE_NOTIF_ID   = 9002
        private const val INSTALLER_URL     = "https://coremdm.web.app/install"
    }
}
