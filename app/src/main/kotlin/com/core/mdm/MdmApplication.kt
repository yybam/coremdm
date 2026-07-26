package com.core.mdm

import android.app.Application
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.core.mdm.policy.DevicePolicyHelper
import com.core.mdm.security.AppLockState
import com.core.mdm.security.PinManager
import com.core.mdm.worker.PolicyEnforcementWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MdmApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Fast path — plain SharedPreferences read, no Keystore.
        // Must stay synchronous so AppLockState is set before the first Activity starts.
        val pinManager = PinManager.getInstance(this)
        if (!pinManager.isPinSet) AppLockState.unlock()

        val helper = DevicePolicyHelper.getInstance(this)
        if (helper.isAdminActive) {
            // Apply baseline restrictions off the main thread immediately on start.
            appScope.launch {
                helper.applyBaselineRestrictions()
                Log.i("MdmApplication", "Baseline restrictions re-applied on process start")
            }
            // Schedule a periodic WorkManager task to re-enforce policies every 6 hours,
            // self-healing any restrictions that were bypassed while the app wasn't running.
            val workRequest = PeriodicWorkRequestBuilder<PolicyEnforcementWorker>(6, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "policy_enforcement",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
