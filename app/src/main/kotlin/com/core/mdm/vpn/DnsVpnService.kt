package com.core.mdm.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.core.mdm.R
import com.core.mdm.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream

class DnsVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.core.mdm.vpn.START"
        const val ACTION_STOP  = "com.core.mdm.vpn.STOP"
        const val CHANNEL_ID   = "mdm_vpn_filter"
        const val NOTIF_ID     = 1001

        // Fake DNS server address — only this IP is routed through VPN
        private const val VPN_ADDRESS    = "10.33.33.1"
        private const val FAKE_DNS       = "10.33.33.2"
        private const val TAG            = "DnsVpnService"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }

    private var tunInterface: ParcelFileDescriptor? = null
    private val job   = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            stopSelf()
            return START_NOT_STICKY
        }
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        tunInterface?.close()
        val tun = Builder()
            .addAddress(VPN_ADDRESS, 32)
            .addDnsServer(FAKE_DNS)
            .addRoute(FAKE_DNS, 32)   // only DNS traffic goes through VPN
            .setMtu(1500)
            .setSession("CORE MDM Filter")
            .establish()

        if (tun == null) {
            Log.e(TAG, "Failed to establish VPN tunnel")
            stopSelf()
            return
        }

        tunInterface = tun
        _isRunning.value = true
        Log.i(TAG, "VPN tunnel established — DNS filter active")

        val blocklist = BlocklistRepository.getInstance(this)

        scope.launch {
            val input  = FileInputStream(tun.fileDescriptor)
            val output = FileOutputStream(tun.fileDescriptor)
            val buffer = ByteArray(32_767)

            while (isActive) {
                val n = withContext(Dispatchers.IO) {
                    runCatching { input.read(buffer) }.getOrDefault(-1)
                }
                if (n <= 0) continue
                val packet   = buffer.copyOf(n)
                val response = DnsPacketProcessor.process(packet, blocklist, this@DnsVpnService)
                    ?: continue
                withContext(Dispatchers.IO) {
                    runCatching { output.write(response) }
                }
            }
        }
    }

    private fun stopVpn() {
        job.cancel()
        tunInterface?.close()
        tunInterface = null
        _isRunning.value = false
        Log.i(TAG, "VPN tunnel closed")
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopVpn()
        stopSelf()
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Content Filter",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "CORE MDM DNS content filter"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, DnsVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("CORE MDM — Filter Active")
            .setContentText("DNS content filter is running")
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
