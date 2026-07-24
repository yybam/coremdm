package com.core.mdm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.util.Log
import com.google.firebase.firestore.ListenerRegistration
import com.core.mdm.R
import com.core.mdm.firebase.DeviceRegistry
import com.core.mdm.remote.AlarmController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MdmCommandService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var commandListener: ListenerRegistration? = null
    private var lastAlarmActive: Boolean? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())

        DeviceRegistry.register(applicationContext)

        commandListener = DeviceRegistry.watchCommands(applicationContext) { alarmActive ->
            if (alarmActive == lastAlarmActive) return@watchCommands
            lastAlarmActive = alarmActive
            Log.d("MdmCommandService", "Alarm state changed: $alarmActive")
            if (alarmActive) AlarmController.playAlarm(applicationContext)
            else AlarmController.stopAlarm()
        }

        serviceScope.launch {
            while (isActive) {
                delay(60_000L)
                DeviceRegistry.updateLastSeen(applicationContext)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        commandListener?.remove()
        serviceScope.cancel()
        AlarmController.stopAlarm()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MDM Remote Control",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps remote alarm monitoring active"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("CORE MDM")
            .setContentText("Remote monitoring active")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    companion object {
        private const val CHANNEL_ID = "mdm_command_service"
        private const val NOTIF_ID   = 9001

        fun start(context: Context) {
            val intent = Intent(context, MdmCommandService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MdmCommandService::class.java))
        }
    }
}
