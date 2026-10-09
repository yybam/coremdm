package com.core.mdm.firebase

import com.core.mdm.installer.SelfUpdateService
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.core.mdm.remote.AlarmController
import com.core.mdm.remote.UpdateNotifier

class MdmFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        DeviceRegistry.storeFcmToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val version = message.data["version"] ?: ""
        val url     = message.data["url"]     ?: ""
        when (message.data["type"]) {
            "alarm_start"      -> AlarmController.playAlarm(applicationContext)
            "alarm_stop"       -> AlarmController.stopAlarm()
            "update_available" -> UpdateNotifier.show(
                applicationContext,
                version     = version.ifEmpty { "New" },
                downloadUrl = url.ifEmpty { UpdateNotifier.INSTALLER_URL },
            )
            // Silent self-update: download and install as Device Owner —
            // no user interaction, bypasses DISALLOW_INSTALL_APPS restriction.
            "install_update"   -> if (url.isNotEmpty()) SelfUpdateService.start(
                applicationContext, apkUrl = url, version = version,
            )
        }
    }
}
