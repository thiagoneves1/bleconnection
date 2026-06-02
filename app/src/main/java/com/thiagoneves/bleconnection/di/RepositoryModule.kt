package com.thiagoneves.bleconnection.di

import com.thiagoneves.bleconnection.data.repository.BleRepositoryImpl
import com.thiagoneves.bleconnection.domain.repository.BleRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the [BleRepository] interface to its data-layer implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindBleRepository(impl: BleRepositoryImpl): BleRepository
}

