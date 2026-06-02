package com.thiagoneves.bleconnection.domain.protocol

import com.thiagoneves.bleconnection.domain.model.PulseOximeterData
import java.util.UUID

/**
 * Strategy interface that abstracts how the app talks to a specific pulse oximeter family
 * (e.g. Bluetooth SIG standard PLX, or a proprietary protocol).
 *
 * `BleManager` stays protocol-agnostic: it only knows scan → connect → enable notifications.
 * The meaning of the bytes is delegated to a `PulseOximeterProtocol` implementation injected
 * by Hilt.
 */
interface PulseOximeterProtocol {

    /** GATT service UUID hosting the oximeter data. */
    val serviceUuid: UUID

    /** Characteristic that streams measurements via NOTIFY. */
    val measurementCharacteristicUuid: UUID

    /** Characteristic the protocol writes commands to, or null if it never writes. */
    val writeCharacteristicUuid: UUID?

    /** Called after notifications are enabled. Lets the protocol kick off a handshake. */
    fun onConnected(): ProtocolEvent

    /** Called for every notification on [measurementCharacteristicUuid]. */
    fun onNotification(bytes: ByteArray): ProtocolEvent
}

