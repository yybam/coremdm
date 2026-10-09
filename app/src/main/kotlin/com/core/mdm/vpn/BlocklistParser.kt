package com.core.mdm.vpn

/**
 * Parses downloaded blocklists into normalized domains. Pure Kotlin with no
 * Android dependencies, so it can be unit-tested on the JVM.
 *
 * Two line formats are accepted, and a list can mix them:
 *  - domain per line:  `ads.example.com`
 *  - hosts format:     `0.0.0.0 ads.example.com` or `127.0.0.1<TAB>ads.example.com`
 *    (any leading IPv4/IPv6 address is dropped; every hostname after it is kept)
 *
 * `#` starts a comment. Lines that fit neither format, local names such as
 * `localhost`, and anything that isn't a plausible hostname are skipped.
 */
object BlocklistParser {

    private val WHITESPACE = Regex("\\s+")
    private val IPV4       = Regex("""\d{1,3}(\.\d{1,3}){3}""")

    // Names that hosts files map to the machine itself; never real block targets.
    private val LOCAL_HOSTNAMES = setOf(
        "localhost", "localhost.localdomain", "local", "broadcasthost",
        "ip6-localhost", "ip6-loopback", "ip6-localnet", "ip6-mcastprefix",
        "ip6-allnodes", "ip6-allrouters", "ip6-allhosts",
    )

    fun parse(lines: Sequence<String>): Set<String> {
        val domains = HashSet<String>()
        for (line in lines) {
            val tokens = line.trimStart('\uFEFF').substringBefore('#').trim().split(WHITESPACE)
            val hosts = when {
                tokens.size == 1       -> tokens          // domain per line
                isIpAddress(tokens[0]) -> tokens.drop(1)  // hosts format
                else                   -> emptyList()
            }
            for (host in hosts) {
                val domain = cleanDomain(host)
                if (isPlausibleHostname(domain) && !isLocalHostname(domain)) domains += domain
            }
        }
        return domains
    }

    /** Normalization shared with manually added domains. */
    fun cleanDomain(domain: String): String =
        domain.lowercase().trim().removePrefix("www.").trimEnd('.')

    // A ':' never appears in a hostname, so it marks IPv6 (::1, fe80::1%lo0, ...).
    private fun isIpAddress(token: String) = ':' in token || IPV4.matches(token)

    private fun isLocalHostname(domain: String) =
        domain in LOCAL_HOSTNAMES || domain.endsWith(".localhost")

    private fun isPlausibleHostname(host: String): Boolean {
        if (host.length > 253 || '.' !in host) return false
        val labels = host.split('.')
        // An all-numeric last label means an IPv4 address (e.g. the 0.0.0.0 in "0.0.0.0 0.0.0.0").
        if (labels.last().all { it in '0'..'9' }) return false
        return labels.all { label ->
            label.length in 1..63 && !label.startsWith('-') && !label.endsWith('-') &&
                label.all { it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' }
        }
    }
}
