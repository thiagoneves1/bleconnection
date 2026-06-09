package com.thiagoneves.bleconnection.data.ble.protocol

import com.thiagoneves.bleconnection.domain.protocol.ProtocolEvent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for [StandardPlxProtocol] — focuses on the SFLOAT IEEE-11073 parser.
 *
 * These tests validate that raw BLE bytes are correctly decoded into SpO2/PulseRate
 * values WITHOUT any Bluetooth hardware.
 */
class StandardPlxProtocolTest {

    private lateinit var protocol: StandardPlxProtocol

    @Before
    fun setup() {
        protocol = StandardPlxProtocol()
    }

    // ─────────────────────────────────────────────────────────────────────
    // onConnected
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `onConnected returns Ignore for standard PLX`() {
        val event = protocol.onConnected()
        assertEquals(ProtocolEvent.Ignore, event)
    }

    // ─────────────────────────────────────────────────────────────────────
    // SFLOAT PARSING — basic cases
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `parses SpO2 97 and PulseRate 72 with exponent 0`() {
        // SFLOAT: exponent=0, mantissa=97  → raw16 = 0x0061  → bytes: 0x61, 0x00
        // SFLOAT: exponent=0, mantissa=72  → raw16 = 0x0048  → bytes: 0x48, 0x00
        // Flags: 0x00 (no optional fields)
        val bytes = byteArrayOf(
            0x00,               // flags: no optional fields
            0x61, 0x00,         // SpO2 = 97.0  (mantissa 97, exponent 0)
            0x48, 0x00          // PR   = 72.0  (mantissa 72, exponent 0)
        )

        val event = protocol.onNotification(bytes)
        assertTrue(event is ProtocolEvent.Measurement)
        val data = (event as ProtocolEvent.Measurement).data

        assertEquals(97.0f, data.spo2, 0.01f)
        assertEquals(72.0f, data.pulseRate, 0.01f)
        assertTrue(data.isMeasurementValid)
    }

    @Test
    fun `parses SpO2 97_5 with negative exponent (SFLOAT decimal)`() {
        // 97.5 = mantissa 975 × 10^(-1)
        // mantissa 975 = 0x3CF (12 bits), exponent -1 = 0xF (4-bit two's complement)
        // raw16 = (0xF << 12) | 0x3CF = 0xF3CF
        // Little-endian bytes: 0xCF, 0xF3
        //
        // PulseRate = 68.0 → mantissa 68, exp 0 → raw16 = 0x0044
        val bytes = byteArrayOf(
            0x00,                         // flags
            0xCF.toByte(), 0xF3.toByte(), // SpO2 = 97.5
            0x44, 0x00                    // PR = 68.0
        )

        val event = protocol.onNotification(bytes)
        assertTrue(event is ProtocolEvent.Measurement)
        val data = (event as ProtocolEvent.Measurement).data

        assertEquals(97.5f, data.spo2, 0.01f)
        assertEquals(68.0f, data.pulseRate, 0.01f)
    }

    @Test
    fun `parses SpO2 100`() {
        // 100 = mantissa 100, exp 0 → raw16 = 0x0064
        val bytes = byteArrayOf(
            0x00,
            0x64, 0x00,     // SpO2 = 100
            0x50, 0x00      // PR = 80
        )

        val event = protocol.onNotification(bytes)
        val data = (event as ProtocolEvent.Measurement).data

        assertEquals(100.0f, data.spo2, 0.01f)
        assertEquals(80.0f, data.pulseRate, 0.01f)
    }

    // ─────────────────────────────────────────────────────────────────────
    // OPTIONAL FIELDS
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `parses frame with Pulse Amplitude Index present`() {
        // flags bit 4 set → PAI present at end
        // No Fast, no Slow, no MeasurementStatus, no DeviceSensorStatus
        // PAI = 2.5 → mantissa 25, exp -1 → raw16 = 0xF019
        val bytes = byteArrayOf(
            0x10,               // flags: bit4 = PAI present
            0x61, 0x00,         // SpO2 = 97
            0x48, 0x00,         // PR = 72
            0x19, 0xF0.toByte() // PAI = 2.5 (mantissa=25, exp=-1 → 25*10^-1)
        )

        val event = protocol.onNotification(bytes)
        val data = (event as ProtocolEvent.Measurement).data

        assertEquals(97.0f, data.spo2, 0.01f)
        assertEquals(72.0f, data.pulseRate, 0.01f)
        assertNotNull(data.pulseAmplitudeIndex)
        assertEquals(2.5f, data.pulseAmplitudeIndex!!, 0.01f)
    }

    @Test
    fun `skips Fast and Slow fields correctly`() {
        // flags: bit0 (Fast) + bit1 (Slow) + bit4 (PAI) = 0x13
        // Total: flags(1) + SpO2(2) + PR(2) + Fast(4) + Slow(4) + PAI(2) = 15 bytes
        val bytes = byteArrayOf(
            0x13,               // flags: Fast + Slow + PAI
            0x61, 0x00,         // SpO2 = 97
            0x48, 0x00,         // PR = 72
            // Fast SpO2+PR (4 bytes — skipped)
            0x00, 0x00, 0x00, 0x00,
            // Slow SpO2+PR (4 bytes — skipped)
            0x00, 0x00, 0x00, 0x00,
            // PAI
            0x19, 0xF0.toByte()
        )

        val event = protocol.onNotification(bytes)
        val data = (event as ProtocolEvent.Measurement).data

        assertEquals(97.0f, data.spo2, 0.01f)
        assertEquals(2.5f, data.pulseAmplitudeIndex!!, 0.01f)
    }

    // ─────────────────────────────────────────────────────────────────────
    // EDGE CASES
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `returns Ignore for frame shorter than 5 bytes`() {
        val bytes = byteArrayOf(0x00, 0x61, 0x00, 0x48) // only 4 bytes
        val event = protocol.onNotification(bytes)
        assertEquals(ProtocolEvent.Ignore, event)
    }

    @Test
    fun `SFLOAT NaN value returns Float NaN`() {
        // SFLOAT NaN = 0x07FF → bytes: 0xFF, 0x07
        val bytes = byteArrayOf(
            0x00,
            0xFF.toByte(), 0x07,    // SpO2 = NaN
            0x48, 0x00              // PR = 72
        )

        val event = protocol.onNotification(bytes)
        val data = (event as ProtocolEvent.Measurement).data

        assertTrue(data.spo2.isNaN())
        assertEquals(72.0f, data.pulseRate, 0.01f)
    }

    @Test
    fun `SFLOAT special values return Float NaN`() {
        val specialValues = listOf(
            0x07FE, // +INFINITY
            0x07FF, // NaN
            0x0800, // NRes
            0x0801, // Reserved
            0x0802  // -INFINITY
        )

        specialValues.forEach { raw ->
            val bytes = byteArrayOf(
                0x00,
                (raw and 0xFF).toByte(),
                ((raw shr 8) and 0xFF).toByte(),
                0x48,
                0x00
            )

            val event = protocol.onNotification(bytes)
            val data = (event as ProtocolEvent.Measurement).data

            assertTrue("Expected 0x${raw.toString(16)} to decode as NaN", data.spo2.isNaN())
            assertEquals(72.0f, data.pulseRate, 0.01f)
        }
    }
}

