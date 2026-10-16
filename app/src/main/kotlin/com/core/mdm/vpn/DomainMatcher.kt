package com.core.mdm.vpn

/**
 * Answers "is this domain on the list?". Pure Kotlin, no Android, so it can be unit-tested.
 *
 *  - `*.example.com` matches example.com and every subdomain, in either mode.
 *  - `example.com` matches example.com only in [MatchMode.EXACT], and also every subdomain
 *    in [MatchMode.SUBDOMAIN] — unless it's in [exactOnly], which never covers subdomains.
 */
class DomainMatcher(
    entries: Collection<String>,
    private val mode: MatchMode,
    private val exactOnly: Set<String> = emptySet(),
) {

    private val exact    = HashSet<String>()
    private val wildcard = HashSet<String>()

    init {
        for (raw in entries) {
            val e = normalize(raw)
            when {
                e.startsWith("*.") -> wildcard += e.substring(2)
                e.isNotEmpty()     -> exact += e
            }
        }
    }

    val size: Int get() = exact.size + wildcard.size

    fun matches(domain: String): Boolean {
        val d = normalize(domain)
        if (d in exact || d in wildcard) return true
        // Walk up the parents: a.b.example.com → b.example.com → example.com → com
        var dot = d.indexOf('.')
        while (dot != -1) {
            val parent = d.substring(dot + 1)
            if (parent in wildcard) return true
            if (mode == MatchMode.SUBDOMAIN && parent in exact && parent !in exactOnly) return true
            dot = d.indexOf('.', dot + 1)
        }
        return false
    }

    companion object {
        fun normalize(domain: String): String = domain.trim().lowercase().trimEnd('.')
    }
}
