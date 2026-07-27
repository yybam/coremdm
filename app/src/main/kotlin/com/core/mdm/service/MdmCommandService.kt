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
import com.core.mdm.policy.DevicePolicyHelper
import com.core.mdm.remote.AlarmController
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
    private var authStateListener: FirebaseAuth.AuthStateListener? = null
    private var lastAlarmActive: Boolean? = null

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
                // Enroll first so deviceUid is written to the doc before we open
                // the watchCommands listener — the read rule requires deviceUid == auth.uid.
                EnrollmentManager.enroll(applicationContext) {
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
                                serviceScope.launch(Dispatchers.Main) { applyRemotePolicies(helper, policies) }
                            },
                        )
                    }
                }
            } else {
                commandListener?.remove()
                commandListener = null
            }
        }
        Firebase.auth.addAuthStateListener(authStateListener!!)

        serviceScope.launch {
            while (isActive) {
                delay(60_000L)
                DeviceRegistry.updateLastSeen(applicationContext)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        authStateListener?.let { Firebase.auth.removeAuthStateListener(it) }
        commandListener?.remove()
        commandListener = null
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
        // Content filter VPN
        (policies["filterRunning"] as? Boolean)?.let { running ->
            try {
                val intent = Intent(applicationContext, com.core.mdm.vpn.DnsVpnService::class.java)
                    .setAction(if (running) com.core.mdm.vpn.DnsVpnService.ACTION_START else com.core.mdm.vpn.DnsVpnService.ACTION_STOP)
                applicationContext.startService(intent)
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
        Log.d(TAG, "Remote policies applied (${policies.size} keys)")
    }

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
