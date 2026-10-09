package com.core.mdm.firebase

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.core.mdm.remote.AlarmController
import com.core.mdm.remote.UpdateNotifier

class MdmFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        DeviceRegistry.storeFcmToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        when (message.data["type"]) {
            "alarm_start"      -> AlarmController.playAlarm(applicationContext)
            "alarm_stop"       -> AlarmController.stopAlarm()
            "update_available" -> UpdateNotifier.show(
                applicationContext,
                version     = message.data["version"] ?: "New",
                downloadUrl = message.data["url"] ?: UpdateNotifier.INSTALLER_URL,
            )
        }
    }
}
