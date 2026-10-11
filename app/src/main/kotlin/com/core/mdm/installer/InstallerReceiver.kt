package com.core.mdm.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.UserManager
import android.util.Log
import com.core.mdm.firebase.DeviceRegistry
import com.core.mdm.policy.DevicePolicyHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class InstallerResult {
    data class Success(val packageName: String, val label: String) : InstallerResult()
    data class Failure(val packageName: String, val message: String) : InstallerResult()
}

class InstallerReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_INSTALL_COMPLETE   = "com.core.mdm.INSTALL_COMPLETE"
        const val ACTION_UNINSTALL_COMPLETE = "com.core.mdm.UNINSTALL_COMPLETE"
        const val EXTRA_LABEL               = "install_label"
        private  const val PREFS_NAME       = "mdm_update"
        private  const val KEY_VERSION      = "pending_version"
        private  const val KEY_RESTORE      = "restore_install_restriction"
        private  const val TAG              = "InstallerReceiver"

        private val _lastResult = MutableStateFlow<InstallerResult?>(null)
        val lastResult: StateFlow<InstallerResult?> = _lastResult.asStateFlow()

        fun clearResult() { _lastResult.value = null }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val status  = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val pkg     = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME) ?: ""
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Unknown error"
        val label   = intent.getStringExtra(EXTRA_LABEL) ?: pkg

        val result = if (status == PackageInstaller.STATUS_SUCCESS) {
            Log.i(TAG, "Install success: $pkg")
            InstallerResult.Success(pkg, label)
        } else {
            Log.w(TAG, "Install failed ($status): $pkg — $message")
            InstallerResult.Failure(pkg, message)
        }
        _lastResult.value = result

        // Self-update: write Firestore confirmation and restore DISALLOW_INSTALL_APPS if needed.
        if (pkg == context.packageName) {
            val prefs         = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val pendingVersion = prefs.getString(KEY_VERSION, "") ?: ""
            val shouldRestore  = prefs.getBoolean(KEY_RESTORE, false)

            // Restore restriction before doing anything else (failure or success).
            if (shouldRestore) {
                runCatching {
                    DevicePolicyHelper.getInstance(context)
                        .restrict(UserManager.DISALLOW_INSTALL_APPS)
                }.onFailure { Log.w(TAG, "restore DISALLOW_INSTALL_APPS: ${it.message}") }
            }
            prefs.edit().remove(KEY_VERSION).remove(KEY_RESTORE).apply()

            if (pendingVersion.isNotEmpty()) {
                DeviceRegistry.writeUpdateConfirmation(
                    context = context,
                    version = pendingVersion,
                    success = status == PackageInstaller.STATUS_SUCCESS,
                    error   = if (status != PackageInstaller.STATUS_SUCCESS) message else null,
                )
            }
        }
    }
}
