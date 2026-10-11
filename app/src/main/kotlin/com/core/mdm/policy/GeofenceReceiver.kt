package com.core.mdm.policy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.core.mdm.remote.AlarmController
import com.core.mdm.ui.message.MessageAlertActivity
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_GEOFENCE_EVENT) return
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) { Log.e(TAG, "Geofence error code: ${event.errorCode}"); return }
        if (event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            val prefs   = context.getSharedPreferences("mdm_geofence", Context.MODE_PRIVATE)
            val action  = prefs.getString("action", "lock")  ?: "lock"
            val message = prefs.getString("message", "")     ?: ""
            handleExit(context, action, message)
        }
    }

    companion object {
        const val ACTION_GEOFENCE_EVENT = "com.core.mdm.GEOFENCE_EVENT"
        private const val TAG           = "GeofenceReceiver"

        fun handleExit(context: Context, action: String, message: String) {
            Log.i(TAG, "Geofence exit — action=$action")
            when (action) {
                "alarm"   -> AlarmController.playAlarm(context)
                "message" -> showMessage(context, "⚠️ Geofence Alert",
                    message.ifEmpty { "Device has left the designated area." })
                else      -> DevicePolicyHelper.getInstance(context).lockNow()  // "lock" + default
            }
        }

        private fun showMessage(context: Context, title: String, text: String) {
            context.startActivity(
                Intent(context, MessageAlertActivity::class.java).apply {
                    putExtra(MessageAlertActivity.EXTRA_TITLE, title)
                    putExtra(MessageAlertActivity.EXTRA_TEXT, text)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            )
        }
    }
}
