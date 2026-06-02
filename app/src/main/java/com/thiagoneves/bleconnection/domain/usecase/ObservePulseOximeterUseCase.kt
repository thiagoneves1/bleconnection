package com.thiagoneves.bleconnection.domain.usecase

import com.thiagoneves.bleconnection.domain.model.PulseOximeterData
import com.thiagoneves.bleconnection.domain.repository.BleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Observes live pulse oximeter measurements from the connected device. */
class ObservePulseOximeterUseCase @Inject constructor(
    private val repository: BleRepository
) {
    operator fun invoke(): Flow<PulseOximeterData> = repository.pulseOximeterFlow()
}

