package com.core.mdm.ui.telemetry

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.core.mdm.telemetry.DeviceMetrics
import com.core.mdm.telemetry.TelemetryCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TelemetryUiState(
    val metrics: DeviceMetrics? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

class TelemetryViewModel(application: Application) : AndroidViewModel(application) {

    private val collector = TelemetryCollector(application)

    private val _state = MutableStateFlow(TelemetryUiState())
    val state: StateFlow<TelemetryUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { collector.collect() }.fold(
                onSuccess = { m -> _state.update { it.copy(metrics = m, isLoading = false) } },
                onFailure = { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
            )
        }
    }
}
