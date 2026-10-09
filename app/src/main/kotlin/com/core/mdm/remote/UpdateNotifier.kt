package com.core.mdm.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.core.mdm.R

/**
 * Shows the "update available" notification. Shared by both delivery paths so they look and
 * behave identically:
 *   - FCM push (`MdmFirebaseMessagingService`, wakes offline/backgrounded devices)
 *   - the Firestore command queue (`MdmCommandService`, fired from the web console)
 */
object UpdateNotifier {

    const val CHANNEL_ID   = "mdm_update"
    const val INSTALLER_URL = "https://coremdm.web.app/install"
    private const val NOTIF_ID = 9002

    fun show(context: Context, version: String, downloadUrl: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "App Updates", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "CORE MDM app update notifications" }
            )
        }

        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl.ifBlank { INSTALLER_URL }))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("CORE MDM Update Available")
            .setContentText("Version $version is ready — tap to download and install.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Version $version is ready. Tap \"Download\" to get the latest CORE MDM APK and install it."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, "Download", openIntent)
            .setContentIntent(openIntent)
            .build()

        nm.notify(NOTIF_ID, notification)
    }
}
