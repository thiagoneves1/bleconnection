package com.thiagoneves.bleconnection.domain.usecase

import com.thiagoneves.bleconnection.domain.model.BleDevice
import com.thiagoneves.bleconnection.domain.repository.BleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Starts a BLE scan and emits each discovered device. */
class ScanDevicesUseCase @Inject constructor(
    private val repository: BleRepository
) {
    operator fun invoke(): Flow<BleDevice> = repository.scanDevices()
}

