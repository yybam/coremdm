package com.core.mdm.policy

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlin.math.*

object GeofenceManager {
    private const val TAG         = "GeofenceManager"
    private const val GEOFENCE_ID = "mdm_primary"
    private const val PREFS_NAME  = "mdm_geofence"
    private const val COOLDOWN_MS = 10 * 60 * 1000L

    fun register(
        context: Context,
        lat: Double, lng: Double,
        radiusMeters: Float,
        action: String,
        message: String = "",
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Location permission not granted — geofence not registered")
            return
        }
        saveConfig(context, lat, lng, radiusMeters, action, message)

        val geofence = Geofence.Builder()
            .setRequestId(GEOFENCE_ID)
            .setCircularRegion(lat, lng, radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofence(geofence)
            .build()

        runCatching {
            LocationServices.getGeofencingClient(context)
                .addGeofences(request, buildPendingIntent(context))
                .addOnSuccessListener { Log.i(TAG, "Geofence set: $lat,$lng r=${radiusMeters}m") }
                .addOnFailureListener { Log.e(TAG, "Geofence add failed: ${it.message}") }
        }.onFailure { Log.e(TAG, "register geofence: ${it.message}") }
    }

    fun unregister(context: Context) {
        // Wipe all config including cooldown so a re-enable triggers immediately.
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        runCatching {
            LocationServices.getGeofencingClient(context)
                .removeGeofences(listOf(GEOFENCE_ID))
                .addOnSuccessListener { Log.i(TAG, "Geofence removed") }
                .addOnFailureListener { Log.e(TAG, "Geofence remove: ${it.message}") }
        }
    }

    fun checkGeofence(context: Context, lat: Double, lng: Double) {
        val prefs  = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val fLat   = prefs.getString("lat", null)?.toDoubleOrNull()   ?: return
        val fLng   = prefs.getString("lng", null)?.toDoubleOrNull()   ?: return
        val radius = prefs.getFloat("radius", 200f)
        if (haversineMeters(lat, lng, fLat, fLng) > radius) {
            // Cooldown: only trigger once every 10 minutes to avoid repeated actions while outside the fence.
            val now      = System.currentTimeMillis()
            val lastFire = prefs.getLong("lastTriggerAt", 0L)
            if (now - lastFire < COOLDOWN_MS) return
            prefs.edit().putLong("lastTriggerAt", now).apply()
            val action  = prefs.getString("action", "lock")    ?: "lock"
            val message = prefs.getString("message", "")       ?: ""
            Log.w(TAG, "Geofence exit detected — action=$action")
            GeofenceReceiver.handleExit(context, action, message)
        }
    }

    private fun saveConfig(
        context: Context,
        lat: Double, lng: Double, radius: Float, action: String, message: String,
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString("lat", lat.toString())
            .putString("lng", lng.toString())
            .putFloat("radius", radius)
            .putString("action", action)
            .putString("message", message)
            .apply()
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GeofenceReceiver::class.java)
            .setAction(GeofenceReceiver.ACTION_GEOFENCE_EVENT)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R    = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a    = sin(dLat / 2).pow(2) +
                   cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return R * 2 * asin(sqrt(a))
    }
}
