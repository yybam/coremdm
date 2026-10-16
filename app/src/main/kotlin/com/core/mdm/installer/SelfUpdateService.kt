package com.core.mdm.installer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.UserManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.core.mdm.R
import com.core.mdm.firebase.DeviceRegistry
import com.core.mdm.policy.DevicePolicyHelper
import com.core.mdm.update.GitHubReleases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the CoreMDM APK from a URL and installs it silently using
 * PackageInstaller as Device Owner. Runs as a foreground service so the
 * download survives while the app is backgrounded.
 *
 * Triggered by:
 *  - FCM  message  type = "install_update"  (MdmFirebaseMessagingService)
 *  - Firestore queue type = "install_update" (MdmCommandService)
 */
class SelfUpdateService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url     = intent?.getStringExtra(EXTRA_URL)     ?: run { stopSelf(); return START_NOT_STICKY }
        val version = intent.getStringExtra(EXTRA_VERSION)  ?: ""

        createNotificationChannel()
        notify(buildNotification("Downloading CORE MDM v$version…", 0))

        scope.launch {
            try {
                val apkFile = downloadApk(url, version)
                verifyApk(apkFile)
                notify(buildNotification("Installing v$version…", -1))

                val helper         = DevicePolicyHelper.getInstance(applicationContext)
                val hadRestriction = helper.hasRestriction(UserManager.DISALLOW_INSTALL_APPS)

                // Persist intent so InstallerReceiver can write Firestore confirmation and
                // restore DISALLOW_INSTALL_APPS even if the process is replaced by the new APK.
                applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putString(KEY_VERSION, version)
                    .putBoolean(KEY_RESTORE, hadRestriction)
                    .apply()

                // Temporarily lift DISALLOW_INSTALL_APPS so the Device Owner PackageInstaller
                // session is not blocked. The policy is restored by InstallerReceiver on
                // completion (success or failure), or by MdmCommandService on next Firestore sync.
                if (hadRestriction) helper.unrestrict(UserManager.DISALLOW_INSTALL_APPS)

                SilentInstaller(applicationContext).installApk(
                    apkFile    = apkFile,
                    label      = "CORE MDM $version",
                    selfUpdate = true,
                ).onFailure { e ->
                    Log.e(TAG, "Install failed: ${e.message}")
                    // Session commit failed — restore restriction immediately and clear prefs.
                    if (hadRestriction) helper.restrict(UserManager.DISALLOW_INSTALL_APPS)
                    applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                        .remove(KEY_VERSION).remove(KEY_RESTORE).apply()
                    DeviceRegistry.writeUpdateConfirmation(
                        applicationContext, version, success = false, error = e.message)
                    notifyError("Install failed: ${e.message}")
                }.onSuccess {
                    Log.i(TAG, "Self-update session committed — system installing v$version…")
                    // InstallerReceiver.onReceive() handles the confirmation write and restriction
                    // restore once the PackageInstaller broadcast arrives.
                }

                apkFile.delete()
            } catch (e: Exception) {
                Log.e(TAG, "Self-update error: ${e.message}")
                notifyError("Update failed: ${e.message}")
            } finally {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Download ──────────────────────────────────────────────────────────────

    private suspend fun downloadApk(url: String, version: String): File = withContext(Dispatchers.IO) {
        val dest = File(cacheDir, "selfupdate.apk")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connect()
        val total = conn.contentLengthLong

        conn.inputStream.use { input ->
            dest.outputStream().use { output ->
                val buf = ByteArray(8_192)
                var done = 0L
                var n: Int
                while (input.read(buf).also { n = it } != -1) {
                    output.write(buf, 0, n)
                    done += n
                    if (total > 0) {
                        val pct = (done * 100 / total).toInt()
                        notify(buildNotification("Downloading CORE MDM v$version…", pct))
                    }
                }
            }
        }
        Log.i(TAG, "APK downloaded to ${dest.absolutePath} (${dest.length()} bytes)")
        dest
    }

    /**
     * Refuses anything that isn't this same app, a higher versionCode, and signed with the same
     * key as the installed copy — whether the URL came from GitHub or was pushed from the console.
     */
    private fun verifyApk(apk: File) {
        val candidate = GitHubReleases.archiveInfo(applicationContext, apk)
            ?: run { apk.delete(); throw IllegalStateException("Unreadable APK") }
        @Suppress("DEPRECATION")
        val installed = packageManager.getPackageInfo(
            packageName,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
            else PackageManager.GET_SIGNATURES
        )
        val problem = when {
            candidate.packageName != packageName ->
                "APK is a different app (${candidate.packageName})"
            GitHubReleases.versionCode(candidate) <= GitHubReleases.versionCode(installed) ->
                "APK is not newer than the installed version"
            GitHubReleases.signatures(candidate) != GitHubReleases.signatures(installed) ->
                "APK is signed with a different key"
            else -> null
        }
        if (problem != null) {
            apk.delete()
            throw IllegalStateException(problem)
        }
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "App Updates", NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) }
            )
        }
    }

    private fun buildNotification(text: String, progressPct: Int) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("CORE MDM Update")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply {
                if (progressPct in 0..100) setProgress(100, progressPct, false)
                else setProgress(0, 0, true)   // indeterminate while installing
            }
            .build()

    private fun notify(notification: android.app.Notification) {
        startForeground(NOTIF_ID, notification)
    }

    private fun notifyError(msg: String) {
        getSystemService(NotificationManager::class.java)?.notify(
            NOTIF_ERROR_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("CORE MDM Update Failed")
                .setContentText(msg)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
        )
    }

    companion object {
        private const val TAG            = "SelfUpdateService"
        const val  CHANNEL_ID            = "mdm_update"
        private const val NOTIF_ID       = 9003
        private const val NOTIF_ERROR_ID = 9004
        const val EXTRA_URL              = "apk_url"
        const val EXTRA_VERSION          = "version"
        private const val PREFS_NAME     = "mdm_update"
        private const val KEY_VERSION    = "pending_version"
        private const val KEY_RESTORE    = "restore_install_restriction"

        fun start(context: Context, apkUrl: String, version: String = "") {
            val intent = Intent(context, SelfUpdateService::class.java).apply {
                putExtra(EXTRA_URL, apkUrl)
                putExtra(EXTRA_VERSION, version)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
