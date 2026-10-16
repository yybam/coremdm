package com.core.mdm.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.core.mdm.R
import com.core.mdm.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress

class DnsVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.core.mdm.vpn.START"
        const val ACTION_STOP  = "com.core.mdm.vpn.STOP"
        const val CHANNEL_ID   = "mdm_vpn_filter"
        const val NOTIF_ID     = 1001

        // Fake DNS server addresses — only these are routed through the VPN, so every other
        // packet goes straight out. IPv6 too, so AAAA lookups can't go around the filter.
        private const val VPN_ADDRESS    = "10.33.33.1"
        private const val FAKE_DNS       = "10.33.33.2"
        private const val VPN_ADDRESS_V6 = "fd00:33:33::1"
        private const val FAKE_DNS_V6    = "fd00:33:33::2"
        private const val MTU            = 1500
        private const val TAG            = "DnsVpnService"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }

    private var tunInterface: ParcelFileDescriptor? = null
    private var tunnel: DnsTunnel? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val settings = FilterSettings.getInstance(this)
        if (intent?.action == ACTION_STOP) {
            settings.enabled = false
            // Before stopping: with lockdown still on, stopping would cut the phone off the internet.
            FilterLockdown.release(this)
            stopVpn()
            stopSelf()
            return START_NOT_STICKY
        }
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        // Remembered so BootReceiver turns the filter back on after a reboot.
        settings.enabled = true
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        stopVpn()

        // A Private DNS hostname would send lookups around the tunnel.
        PrivateDnsGuard.turnOffIfBypassing(this)

        val tun = runCatching {
            Builder()
                .addAddress(VPN_ADDRESS, 32)
                .addDnsServer(FAKE_DNS)
                .addRoute(FAKE_DNS, 32)
                .addAddress(VPN_ADDRESS_V6, 128)
                .addDnsServer(FAKE_DNS_V6)
                .addRoute(FAKE_DNS_V6, 128)
                .setMtu(MTU)
                .setSession("CORE MDM Filter")
                .apply {
                    // Our own traffic (sign-in, push messages, updates) never goes through the filter.
                    runCatching { addDisallowedApplication(packageName) }
                }
                .establish()
        }.onFailure { Log.e(TAG, "establish(): ${it.message}") }.getOrNull()

        if (tun == null) {
            Log.e(TAG, "Failed to establish VPN tunnel")
            stopSelf()
            return
        }

        tunInterface = tun
        val repo = BlocklistRepository.getInstance(this)
        tunnel = DnsTunnel(this, tun, DnsFilter.getInstance(this), { repo.upstreamDns }, ::networkDnsServers, MTU)
            .also { it.start() }
        _isRunning.value = true
        Log.i(TAG, "VPN tunnel established — DNS filter active")

        // After PrivateDnsGuard above: clearing Private DNS can lift the lock this re-applies.
        FilterLockdown.apply(this)
    }

    /** DNS servers of the real networks (Wi-Fi, mobile) — not our own VPN. */
    private fun networkDnsServers(): List<Pair<Network, InetAddress>> = runCatching {
        val cm = getSystemService(ConnectivityManager::class.java)
        @Suppress("DEPRECATION")
        cm.allNetworks
            .filter { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == false }
            .flatMap { network -> cm.getLinkProperties(network)?.dnsServers.orEmpty().map { network to it } }
    }.getOrDefault(emptyList())

    private fun stopVpn() {
        tunnel?.stop()
        tunnel = null
        tunInterface?.let {
            runCatching { it.close() }
            Log.i(TAG, "VPN tunnel closed")
        }
        tunInterface = null
        _isRunning.value = false
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    // Another VPN took over or the user revoked us. `enabled` stays on, so the filter comes back
    // after a reboot once the permission is ours again.
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

    // No Stop action: the filter is switched off only from inside the (PIN-protected) app.
    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("CORE MDM — Filter Active")
            .setContentText("DNS content filter is running")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
