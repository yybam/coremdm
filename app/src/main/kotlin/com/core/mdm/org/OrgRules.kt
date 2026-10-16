package com.core.mdm.org

/**
 * An organization's rules, merged into a phone's own settings. Pure Kotlin, no Android.
 *
 * organizations/{orgId}/public/profile.rules = { key: { mode: "locked" | "default", value: bool } }
 * A key that isn't there is "open": the phone's own admin decides.
 *
 *  - locked:  the org's value, whatever the phone's admin set.
 *  - default: the org's value until the phone's admin sets the key themselves.
 *
 * App sections use the key "section:<id>" and live in the blockedSections list; a phone's admin
 * turning off a section the org blocks by default is recorded in sectionsAllowed.
 *
 * The website applies exactly the same rules (policies.js, effectivePolicy/effectiveSection) —
 * keep the two in step.
 */
object OrgRules {

    const val LOCKED  = "locked"
    const val DEFAULT = "default"
    const val SECTION_PREFIX = "section:"
    const val KEY_BLOCKED_SECTIONS = "blockedSections"
    const val KEY_SECTIONS_ALLOWED = "sectionsAllowed"

    data class Rule(val mode: String, val value: Boolean)

    /** Reads the `rules` map of an org profile, dropping anything malformed. */
    fun parse(raw: Map<*, *>?): Map<String, Rule> =
        raw.orEmpty().mapNotNull { (k, v) ->
            val key = k as? String ?: return@mapNotNull null
            val m = v as? Map<*, *> ?: return@mapNotNull null
            val mode = m["mode"] as? String
            if (mode != LOCKED && mode != DEFAULT) return@mapNotNull null
            key to Rule(mode, m["value"] == true)
        }.toMap()

    /** The phone's settings with the org's rules applied. */
    fun merge(policies: Map<String, Any>, rules: Map<String, Rule>): Map<String, Any> {
        if (rules.isEmpty()) return policies
        val out = policies.toMutableMap()
        val sections = stringList(policies[KEY_BLOCKED_SECTIONS]).toMutableSet()
        val allowed  = stringList(policies[KEY_SECTIONS_ALLOWED]).toSet()
        var sectionsTouched = false

        for ((key, rule) in rules) {
            if (key.startsWith(SECTION_PREFIX)) {
                val id = key.removePrefix(SECTION_PREFIX)
                when {
                    rule.mode == LOCKED && rule.value  -> sections += id
                    rule.mode == LOCKED                -> sections -= id
                    rule.mode == DEFAULT && rule.value && id !in allowed -> sections += id
                }
                sectionsTouched = true
                continue
            }
            when (rule.mode) {
                LOCKED  -> out[key] = rule.value
                DEFAULT -> if (key !in policies) out[key] = rule.value
            }
        }
        if (sectionsTouched) out[KEY_BLOCKED_SECTIONS] = sections.toList()
        return out
    }

    fun isLocked(rules: Map<String, Rule>, key: String) = rules[key]?.mode == LOCKED

    fun sectionKey(id: String) = SECTION_PREFIX + id

    private fun stringList(v: Any?): List<String> = (v as? List<*>)?.filterIsInstance<String>().orEmpty()
}
