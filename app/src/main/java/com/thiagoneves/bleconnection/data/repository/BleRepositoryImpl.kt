package com.thiagoneves.bleconnection.data.repository

import android.bluetooth.BluetoothAdapter
import com.thiagoneves.bleconnection.data.ble.BleManager
import com.thiagoneves.bleconnection.domain.model.BleConnectionState
import com.thiagoneves.bleconnection.domain.model.BleDevice
import com.thiagoneves.bleconnection.domain.model.PulseOximeterData
import com.thiagoneves.bleconnection.domain.repository.BleRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Data-layer implementation of [BleRepository].
 *
 * Adds exponential backoff retry on disconnects: 1s, 2s, 4s, 8s, 16s, then capped at 30s.
 * Retries stop if Bluetooth is disabled or the connection fails with a hard error.
 * Keeping retry logic here (not in the ViewModel) means it survives Activity recreation
 * and any consumer of the repository (foreground service, widget…) gets it for free.
 */
@Singleton
class BleRepositoryImpl @Inject constructor(
    private val bleManager: BleManager,
    private val bluetoothAdapter: BluetoothAdapter
) : BleRepository {

    companion object {
        private const val INITIAL_RETRY_DELAY_MS = 1_000L
        private const val MAX_RETRY_DELAY_MS = 30_000L
        private const val BACKOFF_MULTIPLIER = 2
    }

    override fun scanDevices(): Flow<BleDevice> = bleManager.scanForDevices()

    override fun connect(address: String): Flow<BleConnectionState> = flow {
        var retryAttempt = 0
        var shouldRetry = true

        while (shouldRetry) {
            if (!bluetoothAdapter.isEnabled) {
                emit(
                    BleConnectionState.Error(
                        IllegalStateException("Bluetooth is disabled. Cannot reconnect.")
                    )
                )
                break
            }

            if (retryAttempt > 0) {
                val delayMs = min(
                    INITIAL_RETRY_DELAY_MS * Math.pow(
                        BACKOFF_MULTIPLIER.toDouble(),
                        (retryAttempt - 1).toDouble()
                    ).toLong(),
                    MAX_RETRY_DELAY_MS
                )

                emit(BleConnectionState.Connecting)
                delay(delayMs)

                if (!bluetoothAdapter.isEnabled) {
                    emit(
                        BleConnectionState.Error(
                            IllegalStateException("Bluetooth was disabled during reconnection wait.")
                        )
                    )
                    break
                }
            }

            var lastState: BleConnectionState = BleConnectionState.Idle
            bleManager.connect(address).collect { state ->
                emit(state)
                lastState = state
            }

            shouldRetry = when (lastState) {
                is BleConnectionState.Disconnected -> { retryAttempt++; true }
                else -> false
            }
        }
    }

    override fun pulseOximeterFlow(): Flow<PulseOximeterData> = bleManager.measurementFlow()

    /**
     * Disconnect from the active device.
     *
     * NOTE: callers should cancel any in-flight [connect] collection BEFORE calling this,
     * otherwise the retry loop will interpret the user-initiated disconnect as link loss.
     */
    override fun disconnect() = bleManager.disconnect()
}

