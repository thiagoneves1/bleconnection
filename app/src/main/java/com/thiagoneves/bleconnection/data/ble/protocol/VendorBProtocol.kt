package com.thiagoneves.bleconnection.data.ble.protocol

import android.util.Log
import com.thiagoneves.bleconnection.BuildConfig
import com.thiagoneves.bleconnection.domain.model.PulseOximeterData
import com.thiagoneves.bleconnection.domain.protocol.ProtocolEvent
import com.thiagoneves.bleconnection.domain.protocol.PulseOximeterProtocol
import java.util.UUID

/**
 * Implementation of [PulseOximeterProtocol] for a proprietary pulse oximeter family
 * that uses a challenge-response XOR handshake over a custom GATT service.
 *
 * Handshake sequence:
 * 1. `onConnected()` sends [CHALLENGE_SUBSCRIBE] to initiate.
 * 2. Device acknowledges with `[0x20, 0x02]`.
 * 3. App replies with [CHALLENGE_RESPONSE] (contains the [secret]).
 * 4. Device starts streaming XOR-encrypted measurements.
 *
 * @param secret  XOR key shared with the device (production: injected by the NDA holder).
 */
class VendorBProtocol(
    private val secret: ByteArray
) : PulseOximeterProtocol {

    private companion object {
        const val TAG = "VendorBProtocol"

        /** Initial command: subscribe with protocol version 3. */
        val CHALLENGE_SUBSCRIBE = byteArrayOf(0x01, 0x03, 0x00)

        /** Marker byte the device sends in measurement frames. */
        const val MARKER_MEASUREMENT: Byte = 0x10

        /** Marker byte the device sends in handshake acknowledgment. */
        const val MARKER_HANDSHAKE_ACK: Byte = 0x20
    }

    /** State machine tracking where we are in the handshake. */
    private var handshakeComplete = false

    override val serviceUuid: UUID =
        UUID.fromString("a0001523-1234-5678-9abc-def012345678")

    override val measurementCharacteristicUuid: UUID =
        UUID.fromString("a0001524-1234-5678-9abc-def012345678")

    override val writeCharacteristicUuid: UUID? =
        UUID.fromString("a0001525-1234-5678-9abc-def012345678")

    override fun onConnected(): ProtocolEvent {
        handshakeComplete = false
        debugLog("onConnected -> SendCommand(CHALLENGE_SUBSCRIBE)")
        return ProtocolEvent.SendCommand(CHALLENGE_SUBSCRIBE)
    }

    override fun onNotification(bytes: ByteArray): ProtocolEvent {
        if (bytes.isEmpty()) return ProtocolEvent.Ignore

        return if (handshakeComplete) {
            parseMeasurement(bytes)
        } else {
            handleHandshake(bytes)
        }
    }

    private fun handleHandshake(bytes: ByteArray): ProtocolEvent {
        // Expecting: [0x20, 0x02] as handshake acknowledgment
        if (bytes.size < 2 || bytes[0] != MARKER_HANDSHAKE_ACK) {
            debugLog("handshake: unexpected response ${bytes.toHexString()}")
            return ProtocolEvent.Ignore
        }

        // Acknowledge challenge → send secret response
        handshakeComplete = true
        val response = byteArrayOf(0x02, secret.firstOrNull() ?: 0x00)
        debugLog("handshake: ack received -> SendCommand(challenge_response)")
        return ProtocolEvent.SendCommand(response)
    }

    private fun parseMeasurement(bytes: ByteArray): ProtocolEvent {
        if (bytes.size < 3) {
            debugLog("measurement: too short ${bytes.toHexString()}")
            return ProtocolEvent.Ignore
        }

        val xorKey = secret.firstOrNull()?.toInt() ?: 0
        val decrypted = ByteArray(bytes.size) { i ->
            (bytes[i].toInt() xor xorKey).toByte()
        }

        if (decrypted[0] != MARKER_MEASUREMENT) {
            debugLog("measurement: wrong marker ${bytes.toHexString()}")
            return ProtocolEvent.Ignore
        }

        val spo2 = decrypted[1].toInt() and 0xFF
        val pulseRate = decrypted[2].toInt() and 0xFF

        debugLog("measurement: decrypted spo2=$spo2 pulseRate=$pulseRate")

        return ProtocolEvent.Measurement(
            PulseOximeterData(
                spo2 = spo2.toFloat(),
                pulseRate = pulseRate.toFloat()
            )
        )
    }

    private fun debugLog(message: String) {
        if (!BuildConfig.DEBUG) return
        try {
            Log.d(TAG, message)
        } catch (_: RuntimeException) {
            // Local JVM unit tests use Android stubs where Log may be unavailable.
        }
    }

    private fun ByteArray.toHexString(): String = joinToString(" ") {
        (it.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')
    }
}
