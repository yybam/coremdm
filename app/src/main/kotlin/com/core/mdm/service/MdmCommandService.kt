package com.core.mdm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.ktx.Firebase
import com.core.mdm.R
import com.core.mdm.content.GuardPolicy
import com.core.mdm.firebase.DeviceRegistry
import com.core.mdm.firebase.DeviceRemoval
import com.core.mdm.firebase.EnrollmentManager
import com.core.mdm.firebase.LocalSettingsSync
import com.core.mdm.firebase.RemotePolicies
import com.core.mdm.policy.AppBlocker
import com.core.mdm.policy.AppInventory
import com.core.mdm.policy.AppPolicyManager
import com.core.mdm.policy.AppTimeLimits
import com.core.mdm.policy.DevicePolicyHelper
import com.core.mdm.policy.GeofenceManager
import com.core.mdm.policy.InstallGuard
import com.core.mdm.policy.PolicyEvents
import com.core.mdm.policy.RemoteModeState
import com.core.mdm.policy.ScheduledLockdownManager
import com.core.mdm.policy.SecureSettings
import com.core.mdm.installer.SelfUpdateService
import com.core.mdm.remote.AlarmController
import com.core.mdm.ui.message.MessageAlertActivity
import com.core.mdm.update.AppUpdater
import com.core.mdm.vpn.DnsVpnService
import com.core.mdm.vpn.FilterSettings
import com.core.mdm.ws.WsCommandClient
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
    private var packageReceiver: BroadcastReceiver? = null
    private var lastAlarmActive: Boolean? = null
    private var lastAppliedPolicies: Map<String, Any>? = null
    // Previous per-app hide list, so a package removed from the web list gets un-hidden.
    private var lastBlockedApps: Set<String> = emptySet()

    private val appPolicy by lazy {
        AppPolicyManager(applicationContext, DevicePolicyHelper.getInstance(applicationContext))
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())

        val helper = DevicePolicyHelper.getInstance(applicationContext)
        RemoteModeState.init(applicationContext)
        RemotePolicies.init(applicationContext)
        GuardPolicy.init(applicationContext)
        // OEM power saving suspends background services, which is what stops this one from
        // receiving commands. Needs the one-time WRITE_SECURE_SETTINGS grant; no-ops without it.
        SecureSettings.applyServiceKeepAlive(applicationContext)
        registerPackageReceiver()

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
        WsCommandClient.start(applicationContext)

        serviceScope.launch {
            var ticks = 0
            while (isActive) {
                delay(60_000L)
                DeviceRegistry.updateLastSeen(applicationContext)
                writeDeviceStatus()
                // Daily app allowances are checked every minute so an app is paused within a
                // minute of running out, and released again after midnight.
                AppTimeLimits.enforce(applicationContext, lastAppliedPolicies.orEmpty())
                // If enrollment failed or a listener was dropped by an error, keep
                // retrying — otherwise web console commands would silently stop arriving.
                if ((commandListener == null || queueListener == null) &&
                        Firebase.auth.currentUser != null) {
                    launch(Dispatchers.Main) { connectCommandListener(helper) }
                }
                // Every 30 min: refresh the app list for the website and check GitHub for a
                // CORE MDM update (the updater itself only hits the network every 6 h).
                if (++ticks % 30 == 0) {
                    reportDeviceInfo()
                    AppUpdater.checkAndInstall(applicationContext)
                }
            }
        }
        serviceScope.launch {
            delay(90_000L)   // let enrollment settle, then check for an update once per start
            AppUpdater.checkAndInstall(applicationContext)
        }

        // Collect usage stats less frequently — once every 15 minutes is plenty.
        serviceScope.launch {
            while (isActive) {
                delay(15 * 60_000L)
                val stats = UsageStatsCollector.collect(applicationContext)
                if (stats.isNotEmpty()) DeviceRegistry.writeUsageStats(applicationContext, stats)
            }
        }
    }

    /** Must be called on the main thread; commandListener is only touched there. */
    private fun connectCommandListener(helper: DevicePolicyHelper) {
        // Enroll first so deviceUid is written to the doc before we open
        // the watchCommands listener — the read rule requires deviceUid == auth.uid.
        EnrollmentManager.enroll(applicationContext) {
            DeviceRegistry.updateLastSeen(applicationContext) // immediate; don't wait for first 60s tick
            // Refresh the installed-app inventory and version shown in the web console.
            serviceScope.launch { reportDeviceInfo() }
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
                    onDeviceRemoved = { DeviceRemoval.handle(applicationContext) },
                    onPoliciesChange = { policies ->
                        // The snapshot listener also fires for this service's own
                        // lastSeen writes every 60s — only re-apply (and only tell the
                        // UI to re-read) when the policy set actually changed.
                        if (policies != lastAppliedPolicies) {
                            lastAppliedPolicies = policies
                            serviceScope.launch(Dispatchers.Main) {
                                applyRemotePolicies(helper, policies)
                                PolicyEvents.notifyRemoteChange()
                                DeviceRegistry.writePolicyAck(applicationContext)
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
            "check_update" -> serviceScope.launch {
                AppUpdater.checkAndInstall(applicationContext, force = true)
            }
            "show_message" -> {
                val title = (data["title"] as? String) ?: "Message from Administrator"
                val text  = (data["text"]  as? String) ?: ""
                applicationContext.startActivity(
                    android.content.Intent(applicationContext, MessageAlertActivity::class.java)
                        .putExtra(MessageAlertActivity.EXTRA_TITLE, title)
                        .putExtra(MessageAlertActivity.EXTRA_TEXT, text)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                  android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            }
            else -> Log.w(TAG, "Unknown queued command type: $type")
        }
    }

    private fun writeDeviceStatus() {
        val battery  = DeviceStatusCollector.getBatteryLevel(applicationContext)
        val charging = DeviceStatusCollector.isCharging(applicationContext)
        val network  = DeviceStatusCollector.getNetworkInfo(applicationContext)
        DeviceRegistry.writeDeviceStatus(applicationContext, battery, charging, network)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        packageReceiver?.let { runCatching { unregisterReceiver(it) } }
        authStateListener?.let { Firebase.auth.removeAuthStateListener(it) }
        commandListener?.remove()
        commandListener = null
        queueListener?.remove()
        queueListener = null
        WsCommandClient.stop()
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
        // While the DNS filter is on, uninstall stays blocked (FilterLockdown) whatever the console says.
        (policies["mdmUninstallProtected"]  as? Boolean)?.let {
            if (it || !FilterSettings.getInstance(applicationContext).enabled) {
                helper.setSelfUninstallBlocked(it)
            }
        }
        (policies["appsControlBlocked"]     as? Boolean)?.let { helper.setAppsControlBlocked(it) }
        (policies["allCallsBlocked"]        as? Boolean)?.let { helper.setAllCallsBlocked(it) }
        (policies["hotspotBlocked"]         as? Boolean)?.let { helper.setTetheringBlocked(it) }
        // Private DNS
        (policies["privateDnsHost"] as? String)?.let { host ->
            if (host.isNotEmpty()) helper.setPrivateDnsHostname(host) else helper.clearPrivateDns()
        }
        // Dummy browser VPN — intercepts all traffic and returns DNS/connection errors.
        // MDM app itself is excluded so Firestore stays connected.
        (policies["dummyBrowserEnabled"] as? Boolean)?.let { enabled ->
            try {
                applicationContext.startService(
                    Intent(applicationContext, com.core.mdm.vpn.DummyBrowserVpnService::class.java)
                        .setAction(if (enabled) com.core.mdm.vpn.DummyBrowserVpnService.ACTION_START
                                   else         com.core.mdm.vpn.DummyBrowserVpnService.ACTION_STOP)
                )
            } catch (e: Exception) {
                Log.w(TAG, "Dummy browser toggle: ${e.message}")
            }
        }
        // Content filter VPN. On a Device Owner device, registering ourselves as the always-on
        // VPN (FilterLockdown) pre-authorizes the tunnel — no on-device consent tap — so the
        // console toggle works on its own. Without Device Owner the service still needs the
        // one-time VPN consent from the app.
        (policies["filterRunning"] as? Boolean)?.let { running ->
            if (running != FilterSettings.getInstance(applicationContext).enabled ||
                    running != DnsVpnService.isRunning.value) {
                helper.setContentFilterVpnEnabled(running)
                try {
                    val intent = Intent(applicationContext, DnsVpnService::class.java)
                        .setAction(if (running) DnsVpnService.ACTION_START else DnsVpnService.ACTION_STOP)
                    if (running && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        applicationContext.startForegroundService(intent)
                    } else {
                        applicationContext.startService(intent)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Filter toggle: ${e.message}")
                }
            }
        }
        // App blocking (WhatsApp, social media, AI chats, individually blocked apps), screen
        // guard (in-app sections, WebView allow-list), daily time limits, installer allow-list.
        AppBlocker.apply(applicationContext, policies)
        GuardPolicy.apply(applicationContext, policies)
        AppTimeLimits.enforce(applicationContext, policies)
        InstallGuard.apply(applicationContext, policies)
        // Content filter details and hidden apps set from the website
        LocalSettingsSync.apply(applicationContext, policies)
        (policies["webviewDisabled"] as? Boolean)?.let { helper.setWebViewDisabled(it) }
        RemoteModeState.set(applicationContext, policies["remoteOnly"] as? Boolean ?: false)
        serviceScope.launch { reportDeviceInfo() }
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
        // Per-app HIDE list from the Installed Apps tab (removed from the launcher): hide the
        // listed packages, and un-hide any dropped from the list since last time. (Per-app
        // *suspend* — "blockedPackages" / "suspendedApps" — is handled by AppBlocker above.)
        (policies["blockedApps"] as? List<*>)?.let { list ->
            val desired = list.filterIsInstance<String>().toSet()
            (lastBlockedApps - desired).forEach { appPolicy.unhidePackage(it) }
            desired.forEach { if (isInstalled(it)) appPolicy.hidePackage(it) }
            lastBlockedApps = desired
        }
        // ── Parental controls ─────────────────────────────────────────────────
        (policies["emailBlocked"] as? Boolean)?.let { helper.setEmailAppsHidden(it) }
        (policies["smsBlocked"]   as? Boolean)?.let { helper.setSmsBlocked(it) }

        // Screen time: when enabled, schedule daily lock/unlock alarms from the stored times.
        val screenTimeEnabled = policies["screenTimeEnabled"] as? Boolean
        val screenTimeStart   = policies["screenTimeStart"]   as? String ?: "07:00"
        val screenTimeEnd     = policies["screenTimeEnd"]     as? String ?: "21:00"
        if (screenTimeEnabled != null) {
            com.core.mdm.policy.ScreenTimeScheduler.save(applicationContext, screenTimeEnabled, screenTimeStart, screenTimeEnd)
            if (screenTimeEnabled) {
                com.core.mdm.policy.ScreenTimeScheduler.schedule(applicationContext, screenTimeStart, screenTimeEnd)
            } else {
                com.core.mdm.policy.ScreenTimeScheduler.cancel(applicationContext)
            }
        }

        // Location tracking
        (policies["locationTrackingEnabled"] as? Boolean)?.let { enabled ->
            if (enabled) {
                helper.grantMdmPermissions()
                LocationService.start(applicationContext)
            } else {
                LocationService.stop(applicationContext)
                DeviceRegistry.clearLocation(applicationContext)
            }
        }

        // Geofencing
        (policies["geofence"] as? Map<String, Any>)?.let { gf ->
            val enabled = gf["enabled"] as? Boolean ?: false
            if (enabled) {
                val lat     = (gf["lat"]    as? Number)?.toDouble() ?: return@let
                val lng     = (gf["lng"]    as? Number)?.toDouble() ?: return@let
                val radius  = ((gf["radius"] as? Number)?.toFloat() ?: 200f)
                val action  = (gf["action"] as? String) ?: "lock"
                val message = (gf["message"] as? String) ?: ""
                GeofenceManager.register(applicationContext, lat, lng, radius, action, message)
            } else {
                GeofenceManager.unregister(applicationContext)
            }
        }

        // Scheduled lockdown
        val lockdownEnabled = policies["scheduledLockdownEnabled"] as? Boolean
        val lockdownStart   = policies["scheduledLockdownStart"]   as? String ?: "07:00"
        val lockdownEnd     = policies["scheduledLockdownEnd"]     as? String ?: "22:00"
        if (lockdownEnabled != null) {
            if (lockdownEnabled) ScheduledLockdownManager.schedule(applicationContext, lockdownEnd, lockdownStart)
            else                 ScheduledLockdownManager.cancel(applicationContext)
        }

        Log.d(TAG, "Remote policies applied (${policies.size} keys)")
    }

    /** New installs: re-apply app blocks (a blocked app installed later must be suspended too),
     *  and refresh the website's app list. */
    private fun registerPackageReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                serviceScope.launch {
                    if (intent.action == Intent.ACTION_PACKAGE_ADDED && !replacing) {
                        AppBlocker.reapply(applicationContext)
                        // "Only chosen apps can install apps"
                        InstallGuard.sweep(applicationContext)
                    }
                    reportDeviceInfo()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        registerReceiver(receiver, filter)
        packageReceiver = receiver
    }

    private fun reportDeviceInfo() {
        runCatching {
            @Suppress("DEPRECATION")
            val info = packageManager.getPackageInfo(packageName, 0)
            @Suppress("DEPRECATION")
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
            DeviceRegistry.reportDeviceInfo(applicationContext, AppInventory.collect(applicationContext), info.versionName ?: "", code,
                guardEnabled = GuardPolicy.isEnabled(applicationContext),
                usageAccess  = AppTimeLimits.hasUsageAccess(applicationContext),
                timeUsage    = AppTimeLimits.todayUsage(applicationContext,
                    AppTimeLimits.readLimits(lastAppliedPolicies.orEmpty())).associate { it.pkg to it.usedMinutes })
        }.onFailure { Log.w(TAG, "reportDeviceInfo: ${it.message}") }
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
