package com.thiagoneves.bleconnection.domain.protocol

import com.thiagoneves.bleconnection.domain.model.PulseOximeterData

/** Result of a protocol interpreting one BLE notification. */
sealed class ProtocolEvent {

    /** Bytes were decoded into a usable measurement. */
    data class Measurement(val data: PulseOximeterData) : ProtocolEvent()

    /** Protocol wants the manager to write [payload] to the device (e.g. handshake step). */
    data class SendCommand(val payload: ByteArray) : ProtocolEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SendCommand) return false
            return payload.contentEquals(other.payload)
        }
        override fun hashCode(): Int = payload.contentHashCode()
    }

    /** Bytes were consumed internally; nothing to forward or send back. */
    object Ignore : ProtocolEvent()
}

