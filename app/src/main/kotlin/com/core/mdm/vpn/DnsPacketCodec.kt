package com.core.mdm.vpn

/** A UDP DNS query read off the TUN interface. */
class DnsQuery(
    val ipVersion: Int,
    val srcAddr: ByteArray,
    val dstAddr: ByteArray,
    val srcPort: Int,
    val dstPort: Int,
    /** The DNS message itself (UDP payload). */
    val dns: ByteArray,
    /** Lowercase question name, e.g. "www.example.com". */
    val name: String,
    /** Question type: 1 = A, 28 = AAAA, 65 = HTTPS, … */
    val type: Int,
    /** Offset in [dns] just past the question section. */
    val questionEnd: Int,
)

/**
 * Reads DNS queries out of raw IPv4/IPv6 packets and wraps DNS answers back into packets.
 * Pure Kotlin, no Android, so it can be unit-tested.
 */
object DnsPacketCodec {

    private const val DNS_PORT  = 53
    private const val UDP       = 17
    private const val RCODE_SERVFAIL = 2
    private const val RCODE_NXDOMAIN = 3

    // ── Reading ───────────────────────────────────────────────────────────────

    /** Returns the query in [packet], or null if it isn't a single-question UDP DNS query to port 53. */
    fun parse(packet: ByteArray, length: Int): DnsQuery? {
        if (length < 1) return null
        return when ((packet[0].toInt() ushr 4) and 0xF) {
            4    -> parseIpv4(packet, length)
            6    -> parseIpv6(packet, length)
            else -> null
        }
    }

    private fun parseIpv4(p: ByteArray, length: Int): DnsQuery? {
        if (length < 20) return null
        val ihl = (p[0].toInt() and 0xF) * 4
        if (ihl < 20 || length < ihl + 8) return null
        if (p[9].toInt() and 0xFF != UDP) return null
        // Fragments (more-fragments flag or a non-zero offset) aren't worth reassembling for DNS.
        if (p.u16(6) and 0x3FFF != 0) return null
        return parseUdp(p, length, ihl, 4, p.copyOfRange(12, 16), p.copyOfRange(16, 20))
    }

    private fun parseIpv6(p: ByteArray, length: Int): DnsQuery? {
        if (length < 48) return null
        // No extension headers — a plain DNS query never carries any.
        if (p[6].toInt() and 0xFF != UDP) return null
        return parseUdp(p, length, 40, 6, p.copyOfRange(8, 24), p.copyOfRange(24, 40))
    }

    private fun parseUdp(
        p: ByteArray, length: Int, udpOff: Int, version: Int, src: ByteArray, dst: ByteArray,
    ): DnsQuery? {
        val srcPort = p.u16(udpOff)
        val dstPort = p.u16(udpOff + 2)
        if (dstPort != DNS_PORT) return null
        val udpLen = p.u16(udpOff + 4)
        val end = minOf(length, udpOff + udpLen)
        if (end <= udpOff + 8) return null
        val dns = p.copyOfRange(udpOff + 8, end)
        val q = parseQuestion(dns) ?: return null
        return DnsQuery(version, src, dst, srcPort, dstPort, dns, q.first, q.second, q.third)
    }

    /** (name, type, offset past the question) — or null if this isn't a plain query. */
    private fun parseQuestion(dns: ByteArray): Triple<String, Int, Int>? {
        if (dns.size < 17) return null
        if (dns[2].toInt() and 0x80 != 0) return null          // QR set: a response, not a query
        if (dns.u16(4) != 1) return null                       // exactly one question
        val sb = StringBuilder()
        var i = 12
        while (true) {
            if (i >= dns.size) return null
            val len = dns[i].toInt() and 0xFF
            if (len == 0) { i++; break }
            if (len > 63 || i + 1 + len > dns.size) return null // also rejects compression pointers
            if (sb.isNotEmpty()) sb.append('.')
            for (k in i + 1..i + len) sb.append((dns[k].toInt() and 0xFF).toChar())
            i += 1 + len
        }
        if (i + 4 > dns.size) return null
        val type = dns.u16(i)
        return Triple(sb.toString().lowercase(), type, i + 4)
    }

    // ── DNS answers ───────────────────────────────────────────────────────────

    /** "No such domain" for the query's question. */
    fun nxdomain(q: DnsQuery): ByteArray = errorResponse(q, RCODE_NXDOMAIN, truncated = false)

    /** "Server failure" — the client retries later instead of caching a wrong answer. */
    fun servfail(q: DnsQuery): ByteArray = errorResponse(q, RCODE_SERVFAIL, truncated = false)

    /** An empty answer with the TC (truncated) flag, for answers too big for one packet. */
    fun truncated(q: DnsQuery): ByteArray = errorResponse(q, 0, truncated = true)

    // Header + question only: drops any EDNS record so the answer is self-consistent.
    private fun errorResponse(q: DnsQuery, rcode: Int, truncated: Boolean): ByteArray {
        val r = q.dns.copyOf(q.questionEnd)
        val opcodeAndRd = r[2].toInt() and 0x79                       // keep OPCODE and RD
        r[2] = (0x80 or opcodeAndRd or (if (truncated) 0x02 else 0)).toByte() // QR=1, AA=0
        r[3] = (0x80 or rcode).toByte()                                  // RA=1, Z=0, RCODE
        r[4] = 0; r[5] = 1                                               // QDCOUNT = 1
        for (k in 6 until 12) r[k] = 0                                   // AN/NS/AR = 0
        return r
    }

    // ── Wrapping an answer into a packet back to the asker ────────────────────

    /** Largest DNS answer that fits in one packet of [mtu] bytes for this query's IP version. */
    fun maxPayload(q: DnsQuery, mtu: Int): Int = mtu - (if (q.ipVersion == 4) 20 else 40) - 8

    fun buildResponse(q: DnsQuery, payload: ByteArray): ByteArray =
        if (q.ipVersion == 4) buildIpv4(q, payload) else buildIpv6(q, payload)

    private fun buildIpv4(q: DnsQuery, payload: ByteArray): ByteArray {
        val udpLen = 8 + payload.size
        val total  = 20 + udpLen
        val p = ByteArray(total)
        p[0] = 0x45                                  // version 4, IHL 5
        p.put16(2, total)
        p[6] = 0x40                                  // don't fragment
        p[8] = 64                                    // TTL
        p[9] = UDP.toByte()
        q.dstAddr.copyInto(p, 12)                    // swapped: we answer as the DNS server
        q.srcAddr.copyInto(p, 16)
        p.put16(10, checksum(p, 0, 20, 0))
        writeUdp(p, 20, q, payload)
        p.put16(26, udpChecksum(p, 20, udpLen, p.copyOfRange(12, 16), p.copyOfRange(16, 20)))
        return p
    }

    private fun buildIpv6(q: DnsQuery, payload: ByteArray): ByteArray {
        val udpLen = 8 + payload.size
        val p = ByteArray(40 + udpLen)
        p[0] = 0x60                                  // version 6
        p.put16(4, udpLen)                           // payload length
        p[6] = UDP.toByte()                          // next header
        p[7] = 64                                    // hop limit
        q.dstAddr.copyInto(p, 8)
        q.srcAddr.copyInto(p, 24)
        writeUdp(p, 40, q, payload)
        // Mandatory over IPv6 — a zero checksum gets the packet dropped.
        p.put16(46, udpChecksum(p, 40, udpLen, q.dstAddr, q.srcAddr))
        return p
    }

    private fun writeUdp(p: ByteArray, off: Int, q: DnsQuery, payload: ByteArray) {
        p.put16(off, q.dstPort)
        p.put16(off + 2, q.srcPort)
        p.put16(off + 4, 8 + payload.size)
        payload.copyInto(p, off + 8)
    }

    // ── Checksums ─────────────────────────────────────────────────────────────

    private fun udpChecksum(p: ByteArray, off: Int, len: Int, src: ByteArray, dst: ByteArray): Int {
        // Pseudo-header: source, destination, protocol, UDP length.
        var sum = sum16(src, 0, src.size) + sum16(dst, 0, dst.size) + UDP + len
        sum += sum16(p, off, len)
        val cs = fold(sum).inv() and 0xFFFF
        return if (cs == 0) 0xFFFF else cs           // 0 means "no checksum" in UDP
    }

    private fun checksum(p: ByteArray, off: Int, len: Int, initial: Int): Int =
        fold(initial + sum16(p, off, len)).inv() and 0xFFFF

    private fun sum16(b: ByteArray, off: Int, len: Int): Int {
        var sum = 0
        var i = off
        while (i < off + len - 1) {
            sum += ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (len % 2 == 1) sum += (b[off + len - 1].toInt() and 0xFF) shl 8
        return fold(sum)
    }

    private fun fold(s: Int): Int {
        var sum = s
        while (sum ushr 16 != 0) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum
    }

    private fun ByteArray.u16(i: Int) =
        ((this[i].toInt() and 0xFF) shl 8) or (this[i + 1].toInt() and 0xFF)

    private fun ByteArray.put16(i: Int, v: Int) {
        this[i] = (v shr 8).toByte(); this[i + 1] = (v and 0xFF).toByte()
    }
}
