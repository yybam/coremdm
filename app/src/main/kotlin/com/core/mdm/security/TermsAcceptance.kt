package com.core.mdm.security

import android.content.Context

/**
 * The responsibility statement that must be ticked before signing in (app and website).
 * The acceptance time and version are kept on the phone and written to the device record at
 * enrollment, so there's a record of who accepted what, and when.
 */
object TermsAcceptance {
    /** Bump when the wording or the Terms change in a way people must accept again. */
    const val VERSION = "2026-10-16"

    const val STATEMENT =
        "I own this phone or have permission to manage it, and I take full responsibility for it. " +
        "CORE MDM is provided as is: its makers aren't responsible for any problem, loss or damage — " +
        "including problems caused by the app itself. I agree to the Terms of Service and Privacy Policy."

    const val TERMS_URL   = "https://coremdm.org/terms"
    const val PRIVACY_URL = "https://coremdm.org/privacy"

    private const val PREFS        = "mdm_terms"
    private const val KEY_AT       = "accepted_at"
    private const val KEY_VERSION  = "accepted_version"

    fun record(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_AT, System.currentTimeMillis())
            .putString(KEY_VERSION, VERSION)
            .apply()
    }

    /** (acceptedAt millis, version), or null if never accepted on this phone. */
    fun last(context: Context): Pair<Long, String>? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val at = p.getLong(KEY_AT, 0L)
        val v = p.getString(KEY_VERSION, null)
        return if (at > 0 && v != null) at to v else null
    }
}
