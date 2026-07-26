package com.core.mdm.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
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
            Log.i("InstallerReceiver", "Install success: $pkg")
            InstallerResult.Success(pkg, label)
        } else {
            Log.w("InstallerReceiver", "Install failed ($status): $pkg — $message")
            InstallerResult.Failure(pkg, message)
        }
        _lastResult.value = result
    }
}
