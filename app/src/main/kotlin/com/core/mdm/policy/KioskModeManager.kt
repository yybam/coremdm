package com.core.mdm.policy

import android.content.Context

class KioskModeManager(context: Context) {

    private val helper = DevicePolicyHelper.getInstance(context)

    val isDeviceOwner: Boolean get() = helper.isDeviceOwner

    fun setAllowedPackages(packages: List<String>): Boolean {
        if (!helper.isDeviceOwner) return false
        helper.setLockTaskPackages(packages.toTypedArray())
        return true
    }

    fun getAllowedPackages(): List<String> =
        runCatching { helper.dpm.getLockTaskPackages(helper.admin).toList() }
            .getOrDefault(emptyList())

    fun isEnabled(): Boolean = getAllowedPackages().isNotEmpty()

    fun disable(): Boolean {
        if (!helper.isDeviceOwner) return false
        helper.setLockTaskPackages(emptyArray())
        return true
    }
}
