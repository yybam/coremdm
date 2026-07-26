package com.core.mdm.firebase

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.core.mdm.remote.AlarmController

class MdmFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        DeviceRegistry.storeFcmToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        when (message.data["type"]) {
            "alarm_start" -> AlarmController.playAlarm(applicationContext)
            "alarm_stop"  -> AlarmController.stopAlarm()
        }
    }
}
