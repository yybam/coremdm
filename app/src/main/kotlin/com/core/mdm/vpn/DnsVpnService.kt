package com.core.mdm.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
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
import kotlinx.coroutines.Job
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
        private const val VPN_ADDRESS = "10.33.33.1"
        private const val FAKE_DNS    = "10.33.33.2"
        private const val TAG         = "DnsVpnService"

        private const val PREFS_NAME    = "dns_vpn_prefs"
        private const val KEY_ENABLED   = "filter_enabled"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        // Whether the admin intentionally enabled the filter (persists across process restarts).
        fun isFilterEnabled(ctx: Context): Boolean =
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false)

        private fun setFilterEnabled(ctx: Context, enabled: Boolean) {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, enabled).apply()
        }
    }

    private var tunInterface: ParcelFileDescriptor? = null
    // Fresh job/scope per start so a stop→start cycle within the same instance works.
    private var vpnJob: Job? = null
    private var scope: CoroutineScope? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                setFilterEnabled(this, false)
                stopVpn()
                stopSelf()
                return START_NOT_STICKY
            }
            null -> {
                // System restarted the service (START_STICKY). Only continue if the admin
                // deliberately had the filter on; if it was stopped via the quick-settings toggle
                // (onRevoke) or the Stop button, the pref was set to false and we bail out.
                if (!isFilterEnabled(this)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        // ACTION_START or system restart with filter still enabled.
        setFilterEnabled(this, true)
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        vpnJob?.cancel()
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
        val newJob = SupervisorJob()
        vpnJob = newJob
        scope  = CoroutineScope(newJob + Dispatchers.IO)

        scope!!.launch {
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
        vpnJob?.cancel()
        vpnJob = null
        scope  = null
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
        // Called when the user disconnects via the system VPN quick-settings tile or Android
        // VPN settings. Save the intent so that START_STICKY doesn't re-launch the filter.
        setFilterEnabled(this, false)
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
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("CORE MDM — Filter Active")
            .setContentText("Open CORE MDM to manage content filtering")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
