package com.core.mdm.provisioning

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.core.mdm.policy.DevicePolicyHelper

/*
 * QR-code setup (tap the welcome screen 6 times on a freshly reset phone, then scan the code from
 * the website installer). From Android 12, setup refuses to finish unless the device-owner app
 * answers these two screens; ADB setup never calls them. Neither shows any UI.
 */

/** Asked which kind of setup we want — always a fully managed phone (device owner). */
class ProvisioningModeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setResult(RESULT_OK, Intent().putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_MODE,
                DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE,
            ))
        } else {
            setResult(RESULT_OK)
        }
        finish()
    }
}

/** Called once we're device owner, before setup finishes — apply the baseline policy here. */
class PolicyComplianceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread {
            runCatching { DevicePolicyHelper.getInstance(applicationContext).applyBaselineRestrictions() }
                .onFailure { Log.e("Provisioning", "Baseline restrictions failed", it) }
            runOnUiThread {
                setResult(RESULT_OK)
                finish()
            }
        }.start()
    }
}
