package com.core.mdm.ui.location

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.core.mdm.service.LocationService
import com.core.mdm.service.UsageStatsCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LocationState(
    val locationPermission: Boolean = false,
    val usageStatsPermission: Boolean = false,
    val trackingServiceRunning: Boolean = false,
)

class LocationViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(LocationState())
    val state = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        val ctx = getApplication<Application>()
        _state.update {
            it.copy(
                locationPermission = ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED,
                usageStatsPermission = UsageStatsCollector.hasPermission(ctx),
            )
        }
    }

    fun openUsageAccessSettings() {
        val ctx = getApplication<Application>()
        ctx.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
