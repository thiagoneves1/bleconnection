package com.thiagoneves.bleconnection.domain.model

/**
 * One pulse oximeter measurement, decoded by the active protocol from raw BLE bytes.
 *
 * @property spo2                Blood oxygen saturation (0.0 - 100.0). Below 90% is hypoxemia.
 * @property pulseRate           Heart rate in bpm, derived from the photoplethysmogram.
 * @property isMeasurementValid  Device-reported quality flag; defaults to true when not provided.
 * @property pulseAmplitudeIndex Optional perfusion indicator; null when not reported.
 * @property timestamp           Wall-clock time the measurement was captured.
 */
data class PulseOximeterData(
    val spo2: Float,
    val pulseRate: Float,
    val isMeasurementValid: Boolean = true,
    val pulseAmplitudeIndex: Float? = null,
    val timestamp: Long = System.currentTimeMillis()
)

