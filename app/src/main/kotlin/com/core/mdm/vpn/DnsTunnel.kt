package com.core.mdm.vpn

import android.net.Network
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicLong

/**
 * Pumps DNS queries for one TUN interface: read a query, answer NXDOMAIN if it's blocked,
 * otherwise forward it upstream over a protect()ed socket and hand the answer back.
 * Queries are answered in parallel so one slow lookup doesn't hold up the rest.
 *
 * Only counts are logged — never the names looked up.
 */
class DnsTunnel(
    private val service: VpnService,
    private val tun: ParcelFileDescriptor,
    private val filter: DnsFilter,
    private val upstream: () -> String,
    /** The underlying network's own DNS servers — used when [upstream] doesn't answer. */
    private val networkDns: () -> List<Pair<Network, InetAddress>>,
    private val mtu: Int,
) {
    companion object {
        private const val TAG = "DnsTunnel"
        private const val DNS_PORT = 53
        private const val UPSTREAM_TIMEOUT_MS = 3_000
        // After the upstream server fails, skip straight to the network's DNS for this long,
        // so each lookup doesn't sit through the timeout first.
        private const val UPSTREAM_RETRY_MS = 60_000L
        // Only how often an idle loop checks for stop(); queries wake it immediately.
        private const val POLL_TIMEOUT_MS = 10_000
        private const val MAX_IN_FLIGHT = 64
        private const val LOG_EVERY_BLOCKED = 50L
    }

    private val scope    = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = Semaphore(MAX_IN_FLIGHT)
    private val total    = AtomicLong()
    private val blocked  = AtomicLong()
    @Volatile private var upstreamDownUntil = 0L

    fun start() {
        scope.launch { readLoop() }
    }

    fun stop() {
        scope.cancel()
        Log.i(TAG, "Stopped — blocked ${blocked.get()} of ${total.get()} lookups this session")
    }

    private suspend fun readLoop() {
        val input  = FileInputStream(tun.fileDescriptor)
        val output = FileOutputStream(tun.fileDescriptor)
        val buffer = ByteArray(32_767)
        // Poll with a timeout instead of a bare blocking read, so stop() is noticed even when
        // no packets arrive (a blocked read() isn't woken by closing the interface).
        val pollFd = StructPollfd().apply {
            fd = tun.fileDescriptor
            events = OsConstants.POLLIN.toShort()
        }

        while (scope.isActive) {
            val n = try {
                if (Os.poll(arrayOf(pollFd), POLL_TIMEOUT_MS) == 0) continue
                input.read(buffer)
            } catch (e: ErrnoException) {
                if (e.errno == OsConstants.EINTR) continue
                break
            } catch (e: IOException) {
                break                                   // interface closed
            }
            if (n < 0) break
            val query = DnsPacketCodec.parse(buffer, n) ?: continue

            inFlight.acquire()
            scope.launch {
                try {
                    val reply = answer(query)
                    synchronized(output) { output.write(reply) }
                } catch (e: IOException) {
                    // Interface closed while answering — nothing to send it to.
                } finally {
                    inFlight.release()
                }
            }
        }
    }

    private fun answer(q: DnsQuery): ByteArray {
        total.incrementAndGet()
        // Every query type for a blocked name (A, AAAA, HTTPS, …) gets NXDOMAIN.
        if (filter.isBlocked(q.name)) {
            val n = blocked.incrementAndGet()
            if (n % LOG_EVERY_BLOCKED == 0L) Log.i(TAG, "Blocked $n of ${total.get()} lookups so far")
            return DnsPacketCodec.buildResponse(q, DnsPacketCodec.nxdomain(q))
        }
        val maxPayload = DnsPacketCodec.maxPayload(q, mtu)
        val reply = forward(q.dns, maxPayload)
            ?: return DnsPacketCodec.buildResponse(q, DnsPacketCodec.servfail(q))
        // Too big for one packet: tell the client so (TC flag) rather than send half an answer.
        if (reply.size > maxPayload) return DnsPacketCodec.buildResponse(q, DnsPacketCodec.truncated(q))
        return DnsPacketCodec.buildResponse(q, reply)
    }

    /**
     * Asks the upstream server; if it doesn't answer (some networks, e.g. filtered ones, block
     * outside DNS servers), asks the network's own DNS servers instead.
     */
    private fun forward(query: ByteArray, maxPayload: Int): ByteArray? {
        val now = SystemClock.elapsedRealtime()
        if (now >= upstreamDownUntil) {
            ask(query, maxPayload, InetAddress.getByName(upstream()), null)?.let { return it }
            if (upstreamDownUntil <= now) {
                Log.i(TAG, "Upstream DNS not answering — using the network's own DNS for now")
            }
            upstreamDownUntil = now + UPSTREAM_RETRY_MS
        }
        for ((network, server) in networkDns()) {
            ask(query, maxPayload, server, network)?.let { return it }
        }
        return null
    }

    private fun ask(query: ByteArray, maxPayload: Int, server: InetAddress, network: Network?): ByteArray? = runCatching {
        DatagramSocket().use { socket ->
            // Send it around the tunnel, or the query would loop straight back to us.
            check(service.protect(socket)) { "protect() failed" }
            network?.bindSocket(socket)
            socket.soTimeout = UPSTREAM_TIMEOUT_MS
            socket.send(DatagramPacket(query, query.size, server, DNS_PORT))
            // One byte of headroom so an oversized answer is detectable.
            val buf = ByteArray(maxPayload + 1)
            val packet = DatagramPacket(buf, buf.size)
            socket.receive(packet)
            buf.copyOf(packet.length)
        }
    }.onFailure { Log.d(TAG, "DNS server didn't answer: ${it.javaClass.simpleName}") }.getOrNull()
}
