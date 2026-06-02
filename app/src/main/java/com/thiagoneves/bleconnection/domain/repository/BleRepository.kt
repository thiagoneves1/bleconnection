package com.thiagoneves.bleconnection.domain.repository

import com.thiagoneves.bleconnection.domain.model.BleConnectionState
import com.thiagoneves.bleconnection.domain.model.BleDevice
import com.thiagoneves.bleconnection.domain.model.PulseOximeterData
import kotlinx.coroutines.flow.Flow

/**
 * Domain contract for all BLE pulse oximeter operations.
 *
 * Implementations live in the data layer (`BleRepositoryImpl`). ViewModels and use cases
 * depend on this interface so the BLE plumbing can be swapped or faked in tests.
 */
interface BleRepository {

    /** Cold flow that scans for nearby devices; stops on cancel via `awaitClose`. */
    fun scanDevices(): Flow<BleDevice>

    /**
     * Connect to [address] and emit connection-state transitions.
     * Implementations may apply exponential backoff retry on disconnects.
     */
    fun connect(address: String): Flow<BleConnectionState>

    /** Live measurements from the connected device. */
    fun pulseOximeterFlow(): Flow<PulseOximeterData>

    /** Disconnect from the active device and release GATT resources. */
    fun disconnect()
}

