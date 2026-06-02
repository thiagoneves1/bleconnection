package com.thiagoneves.bleconnection.feature.pulseoximeter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thiagoneves.bleconnection.domain.model.BleConnectionState
import com.thiagoneves.bleconnection.domain.model.BleDevice
import com.thiagoneves.bleconnection.domain.usecase.ConnectDeviceUseCase
import com.thiagoneves.bleconnection.domain.usecase.DisconnectDeviceUseCase
import com.thiagoneves.bleconnection.domain.usecase.ObservePulseOximeterUseCase
import com.thiagoneves.bleconnection.domain.usecase.ScanDevicesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** Immutable UI state for the pulse oximeter screen. */
data class PulseOximeterUiState(
    val spo2: Float? = null,
    val pulseRate: Float? = null,
    val isMeasurementValid: Boolean = true,
    val isScanning: Boolean = false,
    val isConnected: Boolean = false,
    val errorMessage: String? = null,
    val devices: List<BleDevice> = emptyList()
)

@HiltViewModel
class PulseOximeterViewModel @Inject constructor(
    private val scanDevicesUseCase: ScanDevicesUseCase,
    private val connectDeviceUseCase: ConnectDeviceUseCase,
    private val observePulseOximeterUseCase: ObservePulseOximeterUseCase,
    private val disconnectDeviceUseCase: DisconnectDeviceUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PulseOximeterUiState())
    val uiState: StateFlow<PulseOximeterUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private var connectionJob: Job? = null
    private var measurementJob: Job? = null

    fun onStartScan() {
        scanJob?.cancel()
        scanJob = scanDevicesUseCase()
            .onStart {
                _uiState.update { it.copy(isScanning = true, devices = emptyList(), errorMessage = null) }
            }
            .onEach { device ->
                _uiState.update { state ->
                    val updated = if (state.devices.none { it.address == device.address }) {
                        state.devices + device
                    } else state.devices
                    state.copy(devices = updated)
                }
            }
            .catch { throwable ->
                _uiState.update { it.copy(isScanning = false, errorMessage = throwable.message ?: "Scan error") }
            }
            .onCompletion { _uiState.update { it.copy(isScanning = false) } }
            .launchIn(viewModelScope)
    }

    /**
     * Cancels the scan job. The cancellation propagates through the cold Flow → triggers
     * `awaitClose` inside [com.thiagoneves.bleconnection.data.ble.BleManager.scanForDevices],
     * which calls `BluetoothLeScanner.stopScan()`. The `onCompletion` operator above then
     * flips `isScanning = false`, so we don't update state imperatively here.
     */
    fun onStopScan() {
        scanJob?.cancel()
        scanJob = null
    }

    fun onDeviceSelected(address: String) {
        scanJob?.cancel()
        scanJob = null

        connectionJob = connectDeviceUseCase(address)
            .onEach { connectionState ->
                when (connectionState) {
                    is BleConnectionState.Connecting -> {
                        _uiState.update { it.copy(isConnected = false, isScanning = false, errorMessage = null) }
                    }
                    is BleConnectionState.Connected -> {
                        _uiState.update { it.copy(isConnected = true, errorMessage = null) }
                        startObservingMeasurements()
                    }
                    is BleConnectionState.Disconnected -> {
                        measurementJob?.cancel()
                        _uiState.update { it.copy(
                            isConnected = false, spo2 = null, pulseRate = null,
                            errorMessage = "Disconnected: ${connectionState.reason}"
                        ) }
                    }
                    is BleConnectionState.Error -> {
                        measurementJob?.cancel()
                        _uiState.update { it.copy(
                            isConnected = false, spo2 = null, pulseRate = null,
                            errorMessage = connectionState.exception.message ?: "Connection error"
                        ) }
                    }
                    is BleConnectionState.Idle, is BleConnectionState.Scanning -> Unit
                }
            }
            .catch { throwable ->
                _uiState.update { it.copy(isConnected = false, errorMessage = throwable.message ?: "Connection failed") }
            }
            .launchIn(viewModelScope)
    }

    fun onDisconnect() {
        measurementJob?.cancel(); measurementJob = null
        connectionJob?.cancel(); connectionJob = null
        disconnectDeviceUseCase()
        _uiState.update { it.copy(isConnected = false, spo2 = null, pulseRate = null, errorMessage = null) }
    }

    fun onRetry() {
        _uiState.update { it.copy(errorMessage = null) }
        onStartScan()
    }

    private fun startObservingMeasurements() {
        measurementJob?.cancel()
        measurementJob = observePulseOximeterUseCase()
            .onEach { data ->
                _uiState.update { it.copy(
                    spo2 = data.spo2,
                    pulseRate = data.pulseRate,
                    isMeasurementValid = data.isMeasurementValid
                ) }
            }
            .catch { throwable ->
                _uiState.update { it.copy(
                    spo2 = null, pulseRate = null,
                    errorMessage = throwable.message ?: "Measurement failed"
                ) }
            }
            .launchIn(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        disconnectDeviceUseCase()
    }
}

