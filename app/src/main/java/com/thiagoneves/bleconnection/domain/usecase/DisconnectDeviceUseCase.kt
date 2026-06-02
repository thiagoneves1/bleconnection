package com.thiagoneves.bleconnection.domain.usecase

import com.thiagoneves.bleconnection.domain.repository.BleRepository
import javax.inject.Inject

/** Disconnects from the currently connected BLE device. */
class DisconnectDeviceUseCase @Inject constructor(
    private val repository: BleRepository
) {
    operator fun invoke() = repository.disconnect()
}

