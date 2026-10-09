package com.core.mdm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.ktx.Firebase
import com.core.mdm.R
import com.core.mdm.firebase.DeviceRegistry
import com.core.mdm.firebase.EnrollmentManager
import com.core.mdm.policy.AppPolicyManager
import com.core.mdm.policy.DevicePolicyHelper
import com.core.mdm.policy.PolicyEvents
import com.core.mdm.installer.SelfUpdateService
import com.core.mdm.remote.AlarmController
import com.core.mdm.remote.UpdateNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MdmCommandService : Service() {

    private val serviceScope  = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var commandListener: ListenerRegistration? = null
    private var queueListener: ListenerRegistration? = null
    private var authStateListener: FirebaseAuth.AuthStateListener? = null
    private var lastAlarmActive: Boolean? = null
    private var lastAppliedPolicies: Map<String, Any>? = null
    // Previous per-app enforcement, so a package removed from the web list gets un-blocked.
    private var lastBlockedApps: Set<String> = emptySet()
    private var lastSuspendedApps: Set<String> = emptySet()
    private var lastSocialBlocked: Boolean? = null

    private val appPolicy by lazy {
        AppPolicyManager(applicationContext, DevicePolicyHelper.getInstance(applicationContext))
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())

        val helper = DevicePolicyHelper.getInstance(applicationContext)

        // Firebase Auth restores its session asynchronously on startup.
        // We must wait for auth to be ready before querying currentUser,
        // otherwise watchCommands() returns null and no commands are received.
        authStateListener = FirebaseAuth.AuthStateListener { fbAuth ->
            if (fbAuth.currentUser != null) {
                connectCommandListener(helper)
            } else {
                commandListener?.remove()
                commandListener = null
                queueListener?.remove()
                queueListener = null
            }
        }
        Firebase.auth.addAuthStateListener(authStateListener!!)

        serviceScope.launch {
            while (isActive) {
                delay(60_000L)
                DeviceRegistry.updateLastSeen(applicationContext)
                // If enrollment failed or a listener was dropped by an error, keep
                // retrying — otherwise web console commands would silently stop arriving.
                if ((commandListener == null || queueListener == null) &&
                        Firebase.auth.currentUser != null) {
                    launch(Dispatchers.Main) { connectCommandListener(helper) }
                }
            }
        }
    }

    /** Must be called on the main thread; commandListener is only touched there. */
    private fun connectCommandListener(helper: DevicePolicyHelper) {
        // Enroll first so deviceUid is written to the doc before we open
        // the watchCommands listener — the read rule requires deviceUid == auth.uid.
        EnrollmentManager.enroll(applicationContext) {
            DeviceRegistry.updateLastSeen(applicationContext) // immediate; don't wait for first 60s tick
            // Refresh the installed-app inventory shown in the web console.
            serviceScope.launch { DeviceRegistry.updateInstalledApps(applicationContext) }
            if (queueListener == null) {
                queueListener = DeviceRegistry.watchCommandQueue(
                    context = applicationContext,
                    onCommand = { type, ref, data ->
                        DeviceRegistry.claimCommand(ref) { claimed ->
                            if (claimed) runQueuedCommand(type, data, helper)
                        }
                    },
                    onError = {
                        queueListener?.remove()
                        queueListener = null
                    },
                )
            }
            if (commandListener == null) {
                commandListener = DeviceRegistry.watchCommands(
                    context = applicationContext,
                    onAlarmChange = { alarmActive ->
                        if (alarmActive == lastAlarmActive) return@watchCommands
                        lastAlarmActive = alarmActive
                        Log.d(TAG, "Alarm state: $alarmActive")
                        if (alarmActive) AlarmController.playAlarm(applicationContext)
                        else AlarmController.stopAlarm()
                    },
                    onLockCommand = {
                        Log.d(TAG, "Remote lock command")
                        helper.lockNow()
                    },
                    onWipeCommand = {
                        Log.d(TAG, "Remote wipe command")
                        helper.wipeDevice(includeExternal = false)
                    },
                    onRebootCommand = {
                        Log.d(TAG, "Remote reboot command")
                        helper.reboot()
                    },
                    onFullLockdownCommand = {
                        Log.d(TAG, "Full lockdown command")
                        serviceScope.launch(Dispatchers.Main) { applyFullLockdown(helper) }
                    },
                    onPoliciesChange = { policies ->
                        // The snapshot listener also fires for this service's own
                        // lastSeen writes every 60s — only re-apply (and only tell the
                        // UI to re-read) when the policy set actually changed.
                        if (policies != lastAppliedPolicies) {
                            lastAppliedPolicies = policies
                            serviceScope.launch(Dispatchers.Main) {
                                applyRemotePolicies(helper, policies)
                                PolicyEvents.notifyRemoteChange()
                            }
                        }
                    },
                    onError = {
                        // Firestore drops a listener after an error; clear it so the
                        // heartbeat loop re-enrolls and re-attaches.
                        commandListener?.remove()
                        commandListener = null
                    },
                )
            }
        }
    }

    /** Dispatches a queued command that this service just claimed (state == executed). */
    private fun runQueuedCommand(type: String, data: Map<String, Any>, helper: DevicePolicyHelper) {
        Log.i(TAG, "Executing queued command: $type")
        when (type) {
            "lock"     -> helper.lockNow()
            "reboot"   -> helper.reboot()
            "wipe"     -> helper.wipeDevice(includeExternal = false)
            "lockdown" -> serviceScope.launch(Dispatchers.Main) { applyFullLockdown(helper) }
            "update_notify" -> UpdateNotifier.show(
                applicationContext,
                version     = (data["version"] as? String) ?: "New",
                downloadUrl = (data["url"] as? String) ?: UpdateNotifier.INSTALLER_URL,
            )
            "install_update" -> {
                val url = (data["url"] as? String) ?: ""
                if (url.isNotEmpty()) SelfUpdateService.start(
                    applicationContext,
                    apkUrl  = url,
                    version = (data["version"] as? String) ?: "",
                )
            }
            else       -> Log.w(TAG, "Unknown queued command type: $type")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        authStateListener?.let { Firebase.auth.removeAuthStateListener(it) }
        commandListener?.remove()
        commandListener = null
        queueListener?.remove()
        queueListener = null
        serviceScope.cancel()
        AlarmController.stopAlarm()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun applyFullLockdown(helper: DevicePolicyHelper) {
        listOf(
            android.os.UserManager.DISALLOW_SAFE_BOOT,
            android.os.UserManager.DISALLOW_FACTORY_RESET,
            android.os.UserManager.DISALLOW_DEBUGGING_FEATURES,
            android.os.UserManager.DISALLOW_ADD_USER,
            android.os.UserManager.DISALLOW_INSTALL_APPS,
            android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
            android.os.UserManager.DISALLOW_UNINSTALL_APPS,
            android.os.UserManager.DISALLOW_CONFIG_WIFI,
            android.os.UserManager.DISALLOW_CONFIG_BLUETOOTH,
            android.os.UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS,
        ).forEach { key -> helper.restrict(key) }
        helper.setStatusBarDisabled(true)
        helper.setScreenCaptureDisabled(true)
        helper.setCameraDisabled(true)
        Log.i(TAG, "Full lockdown applied")
    }

    @Suppress("UNCHECKED_CAST")
    private fun applyRemotePolicies(helper: DevicePolicyHelper, policies: Map<String, Any>) {
        // Anti-bypass
        (policies["safeBootBlocked"]     as? Boolean)?.let { helper.setRestriction(android.os.UserManager.DISALLOW_SAFE_BOOT, it) }
        (policies["factoryResetBlocked"] as? Boolean)?.let { helper.setRestriction(android.os.UserManager.DISALLOW_FACTORY_RESET, it) }
        (policies["debuggingBlocked"]    as? Boolean)?.let { helper.setRestriction(android.os.UserManager.DISALLOW_DEBUGGING_FEATURES, it) }
        (policies["addUserBlocked"]      as? Boolean)?.let { helper.setRestriction(android.os.UserManager.DISALLOW_ADD_USER, it) }
        (policies["userSwitchBlocked"]   as? Boolean)?.let { helper.setRestriction(android.os.UserManager.DISALLOW_USER_SWITCH, it) }
        // App restrictions
        (policies["installAppsBlocked"]    as? Boolean)?.let { helper.setRestriction(android.os.UserManager.DISALLOW_INSTALL_APPS, it) }
        (policies["uninstallAppsBlocked"]  as? Boolean)?.let { helper.setRestriction(android.os.UserManager.DISALLOW_UNINSTALL_APPS, it) }
        (policies["unknownSourcesBlocked"] as? Boolean)?.let { helper.setRestriction(android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES, it) }
        (policies["playStoreHidden"]       as? Boolean)?.let { helper.setPlayStoreHidden(it) }
        (policies["browsersHidden"]        as? Boolean)?.let { helper.setBrowsersHidden(it) }
        (policies["cameraDisabled"]        as? Boolean)?.let { helper.setCameraDisabled(it) }
        (policies["screenCaptureDisabled"] as? Boolean)?.let { helper.setScreenCaptureDisabled(it) }
        // Hardware & Settings
        (policies["wifiConfigBlocked"]      as? Boolean)?.let { helper.setRestriction(android.os.UserManager.DISALLOW_CONFIG_WIFI, it) }
        (policies["mobileNetworksBlocked"]  as? Boolean)?.let { helper.setRestriction(android.os.UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS, it) }
        (policies["bluetoothConfigBlocked"] as? Boolean)?.let { helper.setRestriction(android.os.UserManager.DISALLOW_CONFIG_BLUETOOTH, it) }
        (policies["vpnBlocked"]             as? Boolean)?.let { helper.setRestriction(android.os.UserManager.DISALLOW_CONFIG_VPN, it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            (policies["networkResetBlocked"] as? Boolean)?.let { helper.setRestriction(android.os.UserManager.DISALLOW_NETWORK_RESET, it) }
        }
        (policies["statusBarDisabled"] as? Boolean)?.let { helper.setStatusBarDisabled(it) }
        // Hardware restrictions
        (policies["usbTransferBlocked"]     as? Boolean)?.let { helper.setUsbFileTransferBlocked(it) }
        (policies["bluetoothDisabled"]      as? Boolean)?.let { helper.setBluetoothDisabled(it) }
        (policies["outgoingCallsBlocked"]   as? Boolean)?.let { helper.setOutgoingCallsBlocked(it) }
        (policies["physicalMediaBlocked"]   as? Boolean)?.let { helper.setPhysicalMediaBlocked(it) }
        (policies["mdmUninstallProtected"]  as? Boolean)?.let { helper.setSelfUninstallBlocked(it) }
        // Private DNS
        (policies["privateDnsHost"] as? String)?.let { host ->
            if (host.isNotEmpty()) helper.setPrivateDnsHostname(host) else helper.clearPrivateDns()
        }
        // Content filter VPN. On a Device Owner device, register ourselves as the always-on
        // VPN — this pre-authorizes the tunnel (no on-device consent tap) and the system
        // starts the service for us, so the console toggle works on its own. When turning
        // off, also send STOP to tear the tunnel down immediately. On non-Device-Owner
        // devices, fall back to starting the service directly (still needs manual consent).
        (policies["filterRunning"] as? Boolean)?.let { running ->
            val handled = helper.setContentFilterVpnEnabled(running)
            try {
                if (!handled) {
                    val intent = Intent(applicationContext, com.core.mdm.vpn.DnsVpnService::class.java)
                        .setAction(if (running) com.core.mdm.vpn.DnsVpnService.ACTION_START else com.core.mdm.vpn.DnsVpnService.ACTION_STOP)
                    applicationContext.startService(intent)
                } else if (!running) {
                    applicationContext.startService(
                        Intent(applicationContext, com.core.mdm.vpn.DnsVpnService::class.java)
                            .setAction(com.core.mdm.vpn.DnsVpnService.ACTION_STOP))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Filter toggle: ${e.message}")
            }
        }
        // Kiosk packages
        (policies["kioskPackages"] as? List<*>)?.let { list ->
            val pkgs = list.filterIsInstance<String>().toTypedArray()
            try { helper.setLockTaskPackages(pkgs) } catch (e: Exception) {
                Log.w(TAG, "Kiosk packages: ${e.message}")
            }
        }
        // Stronger lock-screen PIN: enforce a numeric password of at least the configured
        // length (admin picks 4 / 6 / 8 from the console). Also used as the minimum for the
        // in-app PIN so both stay in step.
        (policies["pinMinLength"] as? Number)?.toInt()?.let { len ->
            if (len in 4..16) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    @Suppress("DEPRECATION")
                    runCatching {
                        helper.dpm.setPasswordQuality(
                            helper.admin,
                            android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_NUMERIC
                        )
                    }
                }
                helper.setPasswordMinimumLength(len)
                com.core.mdm.security.PinManager.getInstance(applicationContext)
                    .setRequiredMinLength(len)
            }
        }
        // Block social media: suspend a preset list of social/chat apps in one toggle.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            (policies["socialMediaBlocked"] as? Boolean)?.let { blocked ->
                if (blocked != lastSocialBlocked) {
                    lastSocialBlocked = blocked
                    val present = SOCIAL_PACKAGES.filter { isInstalled(it) }.toTypedArray()
                    if (present.isNotEmpty()) {
                        if (blocked) appPolicy.suspendPackages(present)
                        else appPolicy.unsuspendPackages(present)
                    }
                }
            }
        }
        // Per-app HIDE list from the Installed Apps tab (removed from the launcher): hide the
        // listed packages, and un-hide any dropped from the list since last time.
        (policies["blockedApps"] as? List<*>)?.let { list ->
            val desired = list.filterIsInstance<String>().toSet()
            (lastBlockedApps - desired).forEach { appPolicy.unhidePackage(it) }
            desired.forEach { if (isInstalled(it)) appPolicy.hidePackage(it) }
            lastBlockedApps = desired
        }
        // Per-app BLOCK list (suspended — visible but can't open): suspend the listed packages,
        // and un-suspend any dropped from the list.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            (policies["suspendedApps"] as? List<*>)?.let { list ->
                val desired = list.filterIsInstance<String>().toSet()
                val toUnsuspend = (lastSuspendedApps - desired).filter { isInstalled(it) }
                if (toUnsuspend.isNotEmpty()) appPolicy.unsuspendPackages(toUnsuspend.toTypedArray())
                val toSuspend = desired.filter { isInstalled(it) }
                if (toSuspend.isNotEmpty()) appPolicy.suspendPackages(toSuspend.toTypedArray())
                lastSuspendedApps = desired
            }
        }
        Log.d(TAG, "Remote policies applied (${policies.size} keys)")
    }

    private fun isInstalled(pkg: String): Boolean = runCatching {
        packageManager.getApplicationInfo(pkg, 0); true
    }.getOrDefault(false)

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "MDM Remote Control", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Keeps remote control active"; setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("CORE MDM")
            .setContentText("Remote monitoring active")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    companion object {
        private const val TAG       = "MdmCommandService"
        private const val CHANNEL_ID = "mdm_command_service"
        private const val NOTIF_ID   = 9001

        // Suspended together by the "Block Social Media" toggle in the web console.
        private val SOCIAL_PACKAGES = arrayOf(
            "com.whatsapp",                 // WhatsApp
            "com.facebook.katana",          // Facebook
            "com.instagram.android",        // Instagram
            "com.zhiliaoapp.musically",     // TikTok
            "com.snapchat.android",         // Snapchat
            "com.twitter.android",          // X / Twitter
            "com.facebook.orca",            // Messenger
        )

        fun start(context: Context) {
            val intent = Intent(context, MdmCommandService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) = context.stopService(Intent(context, MdmCommandService::class.java))
    }
}
