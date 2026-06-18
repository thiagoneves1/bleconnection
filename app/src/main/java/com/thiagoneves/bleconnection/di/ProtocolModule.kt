package com.thiagoneves.bleconnection.di

import com.thiagoneves.bleconnection.data.ble.protocol.VendorBProtocol
import com.thiagoneves.bleconnection.domain.protocol.PulseOximeterProtocol
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wires the active [PulseOximeterProtocol] implementation.
 *
 * Currently active: [VendorBProtocol] — proprietary challenge-response XOR handshake.
 * To switch back to the Bluetooth SIG standard, swap the body below to return
 * `StandardPlxProtocol()` instead.
 */
@Module
@InstallIn(SingletonComponent::class)
object ProtocolModule {

    @Provides
    @Singleton
    fun providePulseOximeterProtocol(): PulseOximeterProtocol {
        // Demo secret — in production this would come from secure config/NDA holder.
        val secret = byteArrayOf(0xAA.toByte())
        return VendorBProtocol(secret)
    }
}

