package com.core.mdm.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.core.mdm.R
import com.core.mdm.firebase.DeviceRegistry
import com.core.mdm.policy.GeofenceManager
import com.google.android.gms.location.*

class LocationService : Service() {

    private var fusedClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startTracking()
        return START_STICKY
    }

    override fun onDestroy() {
        locationCallback?.let { fusedClient?.removeLocationUpdates(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startTracking() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Location permission not granted — stopping")
            stopSelf(); return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_INTERVAL_MS)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                Log.d(TAG, "Location: ${loc.latitude}, ${loc.longitude} ±${loc.accuracy}m")
                DeviceRegistry.writeLocation(applicationContext, loc.latitude, loc.longitude, loc.accuracy)
                GeofenceManager.checkGeofence(applicationContext, loc.latitude, loc.longitude)
            }
        }

        runCatching {
            fusedClient?.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
        }.onFailure {
            Log.e(TAG, "requestLocationUpdates: ${it.message}")
            stopSelf()
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Location Tracking", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("CORE MDM")
            .setContentText("Location tracking active")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    companion object {
        private const val TAG           = "LocationService"
        private const val CHANNEL_ID    = "mdm_location"
        private const val NOTIF_ID      = 9005
        private const val INTERVAL_MS   = 5 * 60 * 1000L
        private const val MIN_INTERVAL_MS = 60 * 1000L

        fun start(context: Context) {
            val intent = Intent(context, LocationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) =
            context.stopService(Intent(context, LocationService::class.java))
    }
}
