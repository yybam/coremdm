package com.core.mdm.vpn

import android.net.VpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object DnsPacketProcessor {

    private const val DNS_PORT = 53
    private const val SOCKET_TIMEOUT_MS = 3_000

    /**
     * Processes a raw IPv4 packet from the TUN interface.
     * Returns a fully-formed IPv4/UDP/DNS response to write back to the TUN,
     * or null if this packet is not a DNS query we should handle.
     */
    suspend fun process(
        packet: ByteArray,
        blocklist: BlocklistRepository,
        vpnService: VpnService
    ): ByteArray? {
        // ── IPv4 header ───────────────────────────────────────────────────────
        if (packet.size < 20) return null
        val version = (packet[0].toInt() ushr 4) and 0xF
        if (version != 4) return null                          // IPv4 only
        val ihl     = (packet[0].toInt() and 0xF) * 4
        val proto   = packet[9].toInt() and 0xFF
        if (proto != 17) return null                           // UDP only
        if (packet.size < ihl + 8) return null

        val srcIp   = packet.copyOfRange(12, 16)
        val dstIp   = packet.copyOfRange(16, 20)
        val srcPort = packet.u16(ihl)
        val dstPort = packet.u16(ihl + 2)
        if (dstPort != DNS_PORT) return null

        val dnsPayload = packet.copyOfRange(ihl + 8, packet.size)
        val domain     = extractDomain(dnsPayload) ?: return null

        val dnsResponse = if (blocklist.isDomainBlocked(domain)) {
            buildNxDomainResponse(dnsPayload)
        } else {
            forwardDns(dnsPayload, blocklist.upstreamDns, vpnService)
                ?: buildNxDomainResponse(dnsPayload)  // fail-safe: NXDOMAIN if upstream unreachable
        }

        return buildIpUdpPacket(
            srcIp   = dstIp,    // swap: DNS server → client
            dstIp   = srcIp,
            srcPort = dstPort,
            dstPort = srcPort,
            payload = dnsResponse
        )
    }

    // ── DNS parsing ───────────────────────────────────────────────────────────

    fun extractDomain(dns: ByteArray): String? {
        if (dns.size < 13) return null
        var i = 12  // question section starts after 12-byte header
        val sb = StringBuilder()
        while (i < dns.size) {
            val len = dns[i].toInt() and 0xFF
            if (len == 0) break
            if (len > 63 || i + 1 + len >= dns.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(dns, i + 1, len, Charsets.US_ASCII))
            i += 1 + len
        }
        return if (sb.isEmpty()) null else sb.toString().lowercase()
    }

    private fun buildNxDomainResponse(query: ByteArray): ByteArray {
        val response = query.copyOf()
        response[2] = (response[2].toInt() or 0x80).toByte()             // QR = 1 (response)
        response[3] = ((response[3].toInt() and 0xF0.inv()) or 0x03).toByte() // RCODE = 3 NXDOMAIN
        return response
    }

    // ── Upstream DNS forwarding ───────────────────────────────────────────────

    private suspend fun forwardDns(
        query: ByteArray,
        upstreamIp: String,
        vpnService: VpnService
    ): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val socket = DatagramSocket()
            vpnService.protect(socket)   // bypass VPN to avoid routing loop
            socket.soTimeout = SOCKET_TIMEOUT_MS
            val addr   = InetAddress.getByName(upstreamIp)
            socket.send(DatagramPacket(query, query.size, addr, DNS_PORT))
            val buf    = ByteArray(512)
            val recv   = DatagramPacket(buf, buf.size)
            socket.receive(recv)
            socket.close()
            buf.copyOf(recv.length)
        }.getOrNull()
    }

    // ── Packet construction ───────────────────────────────────────────────────

    private fun buildIpUdpPacket(
        srcIp: ByteArray, dstIp: ByteArray,
        srcPort: Int, dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val udpLen = 8 + payload.size
        val ipLen  = 20 + udpLen
        val pkt    = ByteArray(ipLen)

        // IPv4 header (20 bytes)
        pkt[0]  = 0x45.toByte()                 // Version=4, IHL=5
        pkt[2]  = (ipLen shr 8).toByte()
        pkt[3]  = (ipLen and 0xFF).toByte()
        pkt[6]  = 0x40                           // Don't fragment
        pkt[8]  = 64                             // TTL
        pkt[9]  = 0x11.toByte()                  // Protocol = UDP
        srcIp.copyInto(pkt, 12)
        dstIp.copyInto(pkt, 16)
        val cs  = ipChecksum(pkt, 0, 20)
        pkt[10] = (cs shr 8).toByte()
        pkt[11] = (cs and 0xFF).toByte()

        // UDP header (8 bytes)
        pkt[20] = (srcPort shr 8).toByte();  pkt[21] = (srcPort and 0xFF).toByte()
        pkt[22] = (dstPort shr 8).toByte();  pkt[23] = (dstPort and 0xFF).toByte()
        pkt[24] = (udpLen shr 8).toByte();   pkt[25] = (udpLen and 0xFF).toByte()
        // UDP checksum left as 0 (optional for IPv4)

        payload.copyInto(pkt, 28)
        return pkt
    }

    private fun ipChecksum(buf: ByteArray, off: Int, len: Int): Int {
        var sum = 0
        var i = off
        while (i < off + len - 1) {
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (len % 2 == 1) sum += (buf[off + len - 1].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }

    private fun ByteArray.u16(i: Int) =
        ((this[i].toInt() and 0xFF) shl 8) or (this[i + 1].toInt() and 0xFF)
}
