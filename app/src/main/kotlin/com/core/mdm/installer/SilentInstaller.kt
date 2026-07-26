package com.core.mdm.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SilentInstaller(private val context: Context) {

    private val installer = context.packageManager.packageInstaller

    // ── Install APK from a local File ─────────────────────────────────────────

    suspend fun installApk(apkFile: File, label: String = apkFile.name): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching<Unit> {
                val params = PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL
                )
                val sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    apkFile.inputStream().use { input ->
                        session.openWrite(apkFile.name, 0, apkFile.length()).use { output ->
                            input.copyTo(output)
                            session.fsync(output)
                        }
                    }
                    session.commit(buildPendingIntent(sessionId, label, InstallerReceiver.ACTION_INSTALL_COMPLETE))
                }
                Log.i("SilentInstaller", "Session $sessionId committed for ${apkFile.name}")
            }
        }

    // ── Uninstall a package silently ──────────────────────────────────────────

    suspend fun uninstallPackage(packageName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching<Unit> {
                val intent = Intent(context, InstallerReceiver::class.java).apply {
                    action = InstallerReceiver.ACTION_UNINSTALL_COMPLETE
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    packageName.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                installer.uninstall(packageName, pi.intentSender)
                Log.i("SilentInstaller", "Uninstall requested: $packageName")
            }
        }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildPendingIntent(sessionId: Int, label: String, action: String): android.content.IntentSender {
        val intent = Intent(context, InstallerReceiver::class.java).apply {
            this.action = action
            putExtra(InstallerReceiver.EXTRA_LABEL, label)
        }
        return PendingIntent.getBroadcast(
            context,
            sessionId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ).intentSender
    }
}
