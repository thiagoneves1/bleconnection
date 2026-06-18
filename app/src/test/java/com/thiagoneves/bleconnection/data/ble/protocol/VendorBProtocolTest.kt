package com.thiagoneves.bleconnection.data.ble.protocol

import com.thiagoneves.bleconnection.domain.protocol.ProtocolEvent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for [VendorBProtocol] — verifies the challenge-response XOR handshake and
 * measurement parsing WITHOUT any Bluetooth hardware.
 *
 * These tests mirror the article's core claim:
 * "parser tests don't need Bluetooth at all."
 */
class VendorBProtocolTest {

    private lateinit var protocol: VendorBProtocol
    private val secret = byteArrayOf(0xAA.toByte())

    @Before
    fun setup() {
        protocol = VendorBProtocol(secret)
    }

    // ─────────────────────────────────────────────────────────────────────
    // onConnected
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `onConnected returns SendCommand with CHALLENGE_SUBSCRIBE`() {
        val event = protocol.onConnected()
        assertTrue(event is ProtocolEvent.SendCommand)
        val send = event as ProtocolEvent.SendCommand
        assertArrayEquals(byteArrayOf(0x01, 0x03, 0x00), send.payload)
    }

    // ─────────────────────────────────────────────────────────────────────
    // HANDSHAKE
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `handshake ack returns SendCommand with challenge response`() {
        protocol.onConnected()
        val event = protocol.onNotification(byteArrayOf(0x20, 0x02))
        assertTrue(event is ProtocolEvent.SendCommand)
        val send = event as ProtocolEvent.SendCommand
        assertArrayEquals(byteArrayOf(0x02, 0xAA.toByte()), send.payload)
    }

    @Test
    fun `handshake ignores invalid ack`() {
        protocol.onConnected()
        val event = protocol.onNotification(byteArrayOf(0xFF.toByte(), 0x00))
        assertEquals(ProtocolEvent.Ignore, event)
    }

    @Test
    fun `handshake ignores empty frame`() {
        protocol.onConnected()
        val event = protocol.onNotification(byteArrayOf())
        assertEquals(ProtocolEvent.Ignore, event)
    }

    // ─────────────────────────────────────────────────────────────────────
    // MEASUREMENT PARSING — happy path
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `parses valid measurement frame`() {
        completeHandshake()

        // SpO2=97, PulseRate=72
        // Plaintext: [0x10, 97, 72] → XOR with 0xAA → encrypted: [0xBA, 0xCB, 0xE2]
        val frame = byteArrayOf(
            (0x10 xor 0xAA).toByte(),
            (97 xor 0xAA).toByte(),
            (72 xor 0xAA).toByte()
        )

        val event = protocol.onNotification(frame)
        assertTrue(event is ProtocolEvent.Measurement)
        val data = (event as ProtocolEvent.Measurement).data

        assertEquals(97.0f, data.spo2, 0.01f)
        assertEquals(72.0f, data.pulseRate, 0.01f)
    }

    @Test
    fun `parses SpO2 100 and pulseRate 80`() {
        completeHandshake()

        val frame = byteArrayOf(
            (0x10 xor 0xAA).toByte(),
            (100 xor 0xAA).toByte(),
            (80 xor 0xAA).toByte()
        )

        val event = protocol.onNotification(frame)
        val data = (event as ProtocolEvent.Measurement).data

        assertEquals(100.0f, data.spo2, 0.01f)
        assertEquals(80.0f, data.pulseRate, 0.01f)
    }

    // ─────────────────────────────────────────────────────────────────────
    // MEASUREMENT PARSING — edge cases
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `vendorB parser rejects malformed packet`() {
        completeHandshake()
        val event = protocol.onNotification(byteArrayOf(0xFF.toByte(), 0xFF.toByte()))
        assertEquals(ProtocolEvent.Ignore, event)
    }

    @Test
    fun `rejects frame shorter than 3 bytes`() {
        completeHandshake()
        assertEquals(ProtocolEvent.Ignore, protocol.onNotification(byteArrayOf(0x10)))
        assertEquals(ProtocolEvent.Ignore, protocol.onNotification(byteArrayOf(0x10, 0x00)))
    }

    @Test
    fun `rejects frame with wrong marker`() {
        completeHandshake()
        val event = protocol.onNotification(byteArrayOf(0x00, 0x61, 0x48))
        assertEquals(ProtocolEvent.Ignore, event)
    }

    @Test
    fun `rejects empty frame after handshake`() {
        completeHandshake()
        assertEquals(ProtocolEvent.Ignore, protocol.onNotification(byteArrayOf()))
    }

    // ─────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Drives the protocol through the handshake so subsequent calls enter
     * measurement parsing mode.
     */
    private fun completeHandshake() {
        protocol.onConnected()
        protocol.onNotification(byteArrayOf(0x20, 0x02))
        // After this, handshakeComplete = true
    }
}
