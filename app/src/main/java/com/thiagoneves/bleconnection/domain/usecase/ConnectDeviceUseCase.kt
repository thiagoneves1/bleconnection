package com.thiagoneves.bleconnection.domain.usecase

import com.thiagoneves.bleconnection.domain.model.BleConnectionState
import com.thiagoneves.bleconnection.domain.repository.BleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Connects to a BLE device by MAC address and exposes the connection state stream. */
class ConnectDeviceUseCase @Inject constructor(
    private val repository: BleRepository
) {
    operator fun invoke(address: String): Flow<BleConnectionState> = repository.connect(address)
}

