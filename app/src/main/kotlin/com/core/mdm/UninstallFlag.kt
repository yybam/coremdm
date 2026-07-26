package com.core.mdm

/** Set to true before clearing admin privileges to trigger self-uninstall from onDisabled(). */
object UninstallFlag {
    @Volatile var pending = false
}
