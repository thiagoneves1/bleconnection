package com.thiagoneves.bleconnection.domain.model

/**
 * BLE peripheral discovered during scanning.
 *
 * @property name    Advertised device name; may be null.
 * @property address MAC address — unique identifier used for connection.
 * @property rssi    Signal strength in dBm (closer to 0 = nearer).
 */
data class BleDevice(
    val name: String?,
    val address: String,
    val rssi: Int
)

