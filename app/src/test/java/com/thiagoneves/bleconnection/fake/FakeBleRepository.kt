package com.thiagoneves.bleconnection.fake

import com.thiagoneves.bleconnection.domain.model.BleConnectionState
import com.thiagoneves.bleconnection.domain.model.BleDevice
import com.thiagoneves.bleconnection.domain.model.PulseOximeterData
import com.thiagoneves.bleconnection.domain.repository.BleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Fake implementation of [BleRepository] for unit testing.
 *
 * Uses [MutableSharedFlow] so tests can imperatively emit values
 * without relying on any real BLE hardware.
 */
class FakeBleRepository : BleRepository {

    // Controllable flows — tests call .emit() to push values
    val scanFlow = MutableSharedFlow<BleDevice>()
    val connectionFlow = MutableSharedFlow<BleConnectionState>()
    val measurementFlow = MutableSharedFlow<PulseOximeterData>()

    // Tracking flag — tests assert disconnect was called
    var disconnectCalled = false
        private set

    override fun scanDevices(): Flow<BleDevice> = scanFlow

    override fun connect(address: String): Flow<BleConnectionState> = connectionFlow

    override fun pulseOximeterFlow(): Flow<PulseOximeterData> = measurementFlow

    override fun disconnect() {
        disconnectCalled = true
    }

    /** Reset tracking state between tests */
    fun reset() {
        disconnectCalled = false
    }
}

