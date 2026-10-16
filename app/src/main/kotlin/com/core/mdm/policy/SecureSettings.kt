package com.core.mdm.policy

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Global system settings the MDM needs to control, written through WRITE_SECURE_SETTINGS.
 *
 * That permission is in Android's protected tier: it can't be granted from the Settings UI, so
 * it is given once per device during setup, over ADB, in the same session that sets Device
 * Owner (`adb shell pm grant com.core.mdm android.permission.WRITE_SECURE_SETTINGS`). Without
 * it every write here would throw SecurityException, so [isGranted] is checked first and each
 * write is wrapped — a phone that never got the grant keeps working, it just can't apply these.
 *
 * The reason it matters: aggressive OEM power saving suspends background services, which kills
 * MdmCommandService and makes the phone stop responding to remote commands.
 */
object SecureSettings {
    private const val TAG = "SecureSettings"

    val isGranted: (Context) -> Boolean = { context ->
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * Stops the OS from suspending CoreMDM in the background. Applied after enrollment and at
     * boot, so the command service survives.
     */
    fun applyServiceKeepAlive(context: Context): Boolean {
        if (!isGranted(context)) {
            Log.i(TAG, "WRITE_SECURE_SETTINGS not granted — skipping keep-alive settings")
            return false
        }
        var ok = true
        // Battery saver off, and don't let it switch itself on at a threshold
        ok = putGlobal(context, "low_power", 0) && ok
        ok = putGlobal(context, "low_power_trigger_level", 0) && ok
        // OEM "super/ultra" power saving modes, which suspend background services outright
        listOf("super_power_saving", "ultra_powersaving_mode", "sem_power_saving_mode").forEach {
            putGlobal(context, it, 0)   // not present on every ROM; failure is fine
        }
        // App standby / adaptive battery bucketing
        ok = putGlobal(context, "app_standby_enabled", 0) && ok
        ok = putGlobal(context, "adaptive_battery_management_enabled", 0) && ok
        return ok
    }

    /** Keeps the device awake on the charger during provisioning. */
    fun setStayAwakeWhileCharging(context: Context, stayAwake: Boolean): Boolean =
        putGlobal(context, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, if (stayAwake) 7 else 0)

    /** Turns off the "unknown sources" install warning path — Device Owner installs bypass it. */
    fun setDevelopmentSettings(context: Context, enabled: Boolean): Boolean =
        putGlobal(context, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, if (enabled) 1 else 0)

    fun putGlobal(context: Context, key: String, value: Int): Boolean = write {
        Settings.Global.putInt(context.contentResolver, key, value)
    }

    fun putGlobal(context: Context, key: String, value: String): Boolean = write {
        Settings.Global.putString(context.contentResolver, key, value)
    }

    fun putSecure(context: Context, key: String, value: Int): Boolean = write {
        Settings.Secure.putInt(context.contentResolver, key, value)
    }

    fun putSecure(context: Context, key: String, value: String): Boolean = write {
        Settings.Secure.putString(context.contentResolver, key, value)
    }

    fun getGlobal(context: Context, key: String, default: Int = 0): Int =
        runCatching { Settings.Global.getInt(context.contentResolver, key, default) }.getOrDefault(default)

    /** Android 12+ reports whether the OS is currently restricting our background work. */
    fun isBackgroundRestricted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return runCatching { am.isBackgroundRestricted }.getOrDefault(false)
    }

    private inline fun write(block: () -> Boolean): Boolean =
        runCatching { block() }.getOrElse {
            // SecurityException when the grant is missing; IllegalArgumentException for keys
            // this ROM doesn't have. Neither should take the app down.
            Log.w(TAG, "Setting write failed: ${it.message}")
            false
        }
}
