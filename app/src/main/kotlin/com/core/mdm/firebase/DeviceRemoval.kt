package com.core.mdm.firebase

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.core.mdm.service.MdmCommandService

/**
 * The owner deleted this device from their account on the website. Sign out and stop the
 * command service so the device doesn't re-enroll itself; policies already applied stay in
 * place until someone signs in again.
 */
object DeviceRemoval {
    fun handle(context: Context) {
        Log.i("DeviceRemoval", "Device removed from account on the website — signing out")
        val app = context.applicationContext
        Handler(Looper.getMainLooper()).post {
            EnrollmentManager.forgetEnrollment(app)
            AuthRepository.signOut()
            MdmCommandService.stop(app)
        }
    }
}
