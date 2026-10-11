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
import java.io.FileInputStream
import java.io.FileOutputStream

class DummyBrowserVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.core.mdm.dummybrowser.START"
        const val ACTION_STOP  = "com.core.mdm.dummybrowser.STOP"
        const val CHANNEL_ID   = "mdm_dummy_browser"
        const val NOTIF_ID     = 1002

        private const val VPN_ADDRESS = "10.44.44.1"
        private const val TAG         = "DummyBrowserVpn"
        private const val PREFS_NAME  = "dummy_browser_prefs"
        private const val KEY_ENABLED = "dummy_browser_enabled"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun isEnabled(ctx: Context): Boolean =
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false)

        private fun setEnabled(ctx: Context, v: Boolean) =
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, v).apply()
    }

    private var tun: ParcelFileDescriptor? = null
    private var job: Job? = null
    private var scope: CoroutineScope? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                setEnabled(this, false)
                teardown()
                stopSelf()
                return START_NOT_STICKY
            }
            null -> {
                if (!isEnabled(this)) { stopSelf(); return START_NOT_STICKY }
            }
        }
        setEnabled(this, true)
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
        setup()
        return START_STICKY
    }

    private fun setup() {
        teardown()
        val iface = Builder()
            .addAddress(VPN_ADDRESS, 32)
            .addRoute("0.0.0.0", 0)              // route all IPv4 traffic
            .addDisallowedApplication(packageName) // MDM app bypasses — Firebase stays connected
            .setMtu(1500)
            .setSession("CORE MDM Dummy Browser")
            .establish() ?: run {
                Log.e(TAG, "establish() failed"); stopSelf(); return
            }

        tun = iface
        _isRunning.value = true
        Log.i(TAG, "Dummy browser VPN active — all traffic intercepted")

        val newJob = SupervisorJob()
        job = newJob
        scope = CoroutineScope(newJob + Dispatchers.IO)
        scope!!.launch {
            val input  = FileInputStream(iface.fileDescriptor)
            val output = FileOutputStream(iface.fileDescriptor)
            val buf    = ByteArray(32_767)
            while (isActive) {
                val n = runCatching { input.read(buf) }.getOrDefault(-1)
                if (n <= 0) continue
                val resp = handlePacket(buf.copyOf(n)) ?: continue
                runCatching { output.write(resp) }
            }
        }
    }

    /**
     * DNS queries (UDP/53) → SERVFAIL: browser shows ERR_NAME_NOT_RESOLVED.
     * Everything else → null (dropped): TCP connections time out → ERR_TIMED_OUT /
     * ERR_INTERNET_DISCONNECTED. The MDM app itself is excluded via addDisallowedApplication
     * so Firestore/FCM remain connected regardless of this toggle.
     */
    private fun handlePacket(pkt: ByteArray): ByteArray? {
        if (pkt.size < 20) return null
        if ((pkt[0].toInt() ushr 4) and 0xF != 4) return null  // IPv4 only
        val ihl   = (pkt[0].toInt() and 0xF) * 4
        val proto = pkt[9].toInt() and 0xFF
        if (proto != 17 || pkt.size < ihl + 8) return null     // UDP only past this point
        if (pkt.u16(ihl + 2) != 53) return null                // non-DNS UDP → drop silently

        val dns      = pkt.copyOfRange(ihl + 8, pkt.size)
        val servfail = dns.copyOf()
        servfail[2] = (servfail[2].toInt() or 0x80).toByte()               // QR = 1 (response)
        servfail[3] = ((servfail[3].toInt() and 0xF0.inv()) or 0x02).toByte() // RCODE = 2 SERVFAIL

        return buildIpUdp(
            srcIp   = pkt.copyOfRange(16, 20),  // original dst (fake DNS) → new src
            dstIp   = pkt.copyOfRange(12, 16),  // original src (device) → new dst
            srcPort = 53,
            dstPort = pkt.u16(ihl),
            payload = servfail
        )
    }

    private fun buildIpUdp(
        srcIp: ByteArray, dstIp: ByteArray,
        srcPort: Int, dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val udpLen = 8 + payload.size
        val ipLen  = 20 + udpLen
        val p = ByteArray(ipLen)
        p[0] = 0x45.toByte()
        p[2] = (ipLen shr 8).toByte(); p[3] = (ipLen and 0xFF).toByte()
        p[6] = 0x40; p[8] = 64; p[9] = 0x11.toByte()
        srcIp.copyInto(p, 12); dstIp.copyInto(p, 16)
        val cs = ipChecksum(p, 0, 20)
        p[10] = (cs shr 8).toByte(); p[11] = (cs and 0xFF).toByte()
        p[20] = (srcPort shr 8).toByte(); p[21] = (srcPort and 0xFF).toByte()
        p[22] = (dstPort shr 8).toByte(); p[23] = (dstPort and 0xFF).toByte()
        p[24] = (udpLen shr 8).toByte(); p[25] = (udpLen and 0xFF).toByte()
        payload.copyInto(p, 28)
        return p
    }

    private fun ipChecksum(buf: ByteArray, off: Int, len: Int): Int {
        var sum = 0; var i = off
        while (i < off + len - 1) {
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF); i += 2
        }
        if (len % 2 == 1) sum += (buf[off + len - 1].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }

    private fun ByteArray.u16(i: Int) =
        ((this[i].toInt() and 0xFF) shl 8) or (this[i + 1].toInt() and 0xFF)

    private fun teardown() {
        job?.cancel(); job = null; scope = null
        tun?.close(); tun = null
        _isRunning.value = false
        Log.i(TAG, "Dummy browser VPN torn down")
    }

    override fun onDestroy() { teardown(); super.onDestroy() }

    override fun onRevoke() {
        teardown()
        if (isEnabled(this)) setup() else stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Dummy Browser", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "CORE MDM dummy browser VPN"; setShowBadge(false) }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("CORE MDM — Dummy Browser Active")
            .setContentText("All web requests return network errors")
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
