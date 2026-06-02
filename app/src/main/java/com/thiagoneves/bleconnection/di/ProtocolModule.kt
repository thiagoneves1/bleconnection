package com.thiagoneves.bleconnection.di

import com.thiagoneves.bleconnection.data.ble.protocol.StandardPlxProtocol
import com.thiagoneves.bleconnection.domain.protocol.PulseOximeterProtocol
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wires the active [PulseOximeterProtocol] implementation.
 *
 * To add support for another device family, create a new class implementing
 * [PulseOximeterProtocol] and return it here.
 */
@Module
@InstallIn(SingletonComponent::class)
object ProtocolModule {

    @Provides
    @Singleton
    fun providePulseOximeterProtocol(
        standard: StandardPlxProtocol
    ): PulseOximeterProtocol = standard
}

