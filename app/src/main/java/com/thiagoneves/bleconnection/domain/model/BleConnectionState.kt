package com.thiagoneves.bleconnection.domain.model

/** All possible states of the BLE connection lifecycle. */
sealed class BleConnectionState {
    data object Idle : BleConnectionState()
    data object Scanning : BleConnectionState()
    data object Connecting : BleConnectionState()
    data object Connected : BleConnectionState()
    data class Disconnected(val reason: String) : BleConnectionState()
    data class Error(val exception: Throwable) : BleConnectionState()
}

