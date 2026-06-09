package com.thiagoneves.bleconnection.data.ble.protocol

import android.util.Log
import com.thiagoneves.bleconnection.BuildConfig
import com.thiagoneves.bleconnection.domain.model.PulseOximeterData
import com.thiagoneves.bleconnection.domain.protocol.ProtocolEvent
import com.thiagoneves.bleconnection.domain.protocol.PulseOximeterProtocol
import java.util.UUID
import javax.inject.Inject

/**
 * Implementation of [PulseOximeterProtocol] for the Bluetooth SIG Pulse Oximeter Service
 * (PLX, 0x1822). Works with any device that follows the spec.
 *
 * Streams come on PLX Continuous Measurement (0x2A5F) as IEEE-11073 SFLOAT values.
 * No handshake required — the device starts emitting as soon as notifications are enabled.
 */
class StandardPlxProtocol @Inject constructor() : PulseOximeterProtocol {

    private companion object {
        const val TAG = "StandardPlxProtocol"
    }

    override val serviceUuid: UUID =
        UUID.fromString("00001822-0000-1000-8000-00805f9b34fb")

    override val measurementCharacteristicUuid: UUID =
        UUID.fromString("00002a5f-0000-1000-8000-00805f9b34fb")

    override val writeCharacteristicUuid: UUID? = null

    override fun onConnected(): ProtocolEvent = ProtocolEvent.Ignore

    override fun onNotification(bytes: ByteArray): ProtocolEvent {
        // Minimum valid frame: 1 flags + 2 spo2 + 2 pulseRate
        if (bytes.size < 5) return ProtocolEvent.Ignore
        debugLog("PLX payload=${bytes.toHexString()} size=${bytes.size}")
        return ProtocolEvent.Measurement(parsePlxContinuousMeasurement(bytes))
    }

    /**
     * Parses a PLX Continuous Measurement frame.
     *
     * Frame layout (per SIG PLX spec):
     *  - Byte 0: flags (bit0 Fast, bit1 Slow, bit2 MeasurementStatus,
     *            bit3 DeviceSensorStatus, bit4 PulseAmplitudeIndex)
     *  - Bytes 1-2: SpO2 (SFLOAT, %)
     *  - Bytes 3-4: Pulse Rate (SFLOAT, bpm)
     *  - Optional fields follow in flag order.
     */
    private fun parsePlxContinuousMeasurement(value: ByteArray): PulseOximeterData {
        val flags = value[0].toInt() and 0xFF
        val hasMeasurementStatus    = (flags and 0x04) != 0
        val hasDeviceSensorStatus   = (flags and 0x08) != 0
        val hasPulseAmplitudeIndex  = (flags and 0x10) != 0
        val hasSpo2PrFast           = (flags and 0x01) != 0
        val hasSpo2PrSlow           = (flags and 0x02) != 0

        val spo2 = parseSfloat(value, offset = 1, label = "SpO2")
        val pulseRate = parseSfloat(value, offset = 3, label = "PulseRate")

        var offset = 5
        if (hasSpo2PrFast) offset += 4
        if (hasSpo2PrSlow) offset += 4

        var isValid = true
        if (hasMeasurementStatus && offset + 1 < value.size) {
            val statusLow = value[offset].toInt() and 0xFF
            // Bit 6 = Early Estimated Data → treat as low confidence.
            val earlyEstimated = (statusLow and 0x40) != 0
            isValid = !earlyEstimated
            offset += 2
        }

        if (hasDeviceSensorStatus) offset += 3

        var perfusion: Float? = null
        if (hasPulseAmplitudeIndex && offset + 1 < value.size) {
            perfusion = parseSfloat(value, offset, label = "PAI")
        }

        return PulseOximeterData(
            spo2 = spo2,
            pulseRate = pulseRate,
            isMeasurementValid = isValid,
            pulseAmplitudeIndex = perfusion
        )
    }

    /**
     * Decodes an IEEE-11073-20601 16-bit SFLOAT starting at [offset].
     *
     * Layout: signed 4-bit exponent (top) | signed 12-bit mantissa (bottom), little-endian.
     * Value = mantissa × 10^exponent. Special values (NaN, ±Inf, NRes, Reserved) → Float.NaN.
     */
    private fun parseSfloat(bytes: ByteArray, offset: Int, label: String): Float {
        if (offset + 1 >= bytes.size) return Float.NaN

        val raw16 = (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8)

        when (raw16) {
            0x07FE -> return logSpecialSfloat(label, raw16, "+INFINITY")
            0x07FF -> return logSpecialSfloat(label, raw16, "NaN")
            0x0800 -> return logSpecialSfloat(label, raw16, "NRes")
            0x0801 -> return logSpecialSfloat(label, raw16, "Reserved")
            0x0802 -> return logSpecialSfloat(label, raw16, "-INFINITY")
        }

        var exponent = (raw16 shr 12) and 0x0F
        var mantissa = raw16 and 0x0FFF

        if (exponent >= 0x08) exponent -= 0x10
        if (mantissa >= 0x0800) mantissa -= 0x1000

        val result = (mantissa.toDouble() * Math.pow(10.0, exponent.toDouble())).toFloat()
        debugLog(
            "SFLOAT $label raw=${raw16.toHex16()} exponent=$exponent " +
                    "mantissa=$mantissa value=$result"
        )
        return result
    }

    private fun logSpecialSfloat(label: String, raw16: Int, special: String): Float {
        debugLog("SFLOAT $label raw=${raw16.toHex16()} special=$special -> invalid reading")
        return Float.NaN
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

    private fun Int.toHex16(): String = "0x" + toString(16).uppercase().padStart(4, '0')
}

