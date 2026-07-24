package com.core.mdm.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.core.mdm.policy.DevicePolicyHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PolicyEnforcementWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val helper = DevicePolicyHelper.getInstance(applicationContext)
        if (helper.isAdminActive) {
            helper.applyBaselineRestrictions()
            Log.i("PolicyEnforcementWorker", "Baseline restrictions re-enforced")
        }
        Result.success()
    }
}
