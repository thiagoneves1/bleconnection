package com.thiagoneves.bleconnection.feature.pulseoximeter

import com.thiagoneves.bleconnection.domain.model.BleConnectionState
import com.thiagoneves.bleconnection.domain.model.BleDevice
import com.thiagoneves.bleconnection.domain.model.PulseOximeterData
import com.thiagoneves.bleconnection.domain.usecase.ScanDevicesUseCase
import com.thiagoneves.bleconnection.domain.usecase.ConnectDeviceUseCase
import com.thiagoneves.bleconnection.domain.usecase.ObservePulseOximeterUseCase
import com.thiagoneves.bleconnection.domain.usecase.DisconnectDeviceUseCase
import com.thiagoneves.bleconnection.fake.FakeBleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [PulseOximeterViewModel].
 *
 * Uses [FakeBleRepository] so we control all BLE events and assert the ViewModel's
 * UiState transitions without any Android dependency.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PulseOximeterViewModelTest {

    // Test dispatcher — gives us control over coroutine execution timing
    private val testDispatcher = StandardTestDispatcher()

    // Fake repository — we push events via .emit() and assert UiState
    private val fakeRepository = FakeBleRepository()

    // UseCases wired to the fake
    private lateinit var viewModel: PulseOximeterViewModel

    @Before
    fun setup() {
        // Replace Main dispatcher so viewModelScope runs on the test dispatcher
        Dispatchers.setMain(testDispatcher)

        viewModel = PulseOximeterViewModel(
            scanDevicesUseCase = ScanDevicesUseCase(fakeRepository),
            connectDeviceUseCase = ConnectDeviceUseCase(fakeRepository),
            observePulseOximeterUseCase = ObservePulseOximeterUseCase(fakeRepository),
            disconnectDeviceUseCase = DisconnectDeviceUseCase(fakeRepository)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ─────────────────────────────────────────────────────────────────────
    // INITIAL STATE
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `initial state has default values`() {
        val state = viewModel.uiState.value
        assertNull(state.spo2)
        assertNull(state.pulseRate)
        assertTrue(state.isMeasurementValid)
        assertFalse(state.isScanning)
        assertFalse(state.isConnected)
        assertNull(state.errorMessage)
        assertTrue(state.devices.isEmpty())
    }

    // ─────────────────────────────────────────────────────────────────────
    // SCAN
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `onStartScan sets isScanning and populates device list`() = runTest {
        viewModel.onStartScan()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isScanning)

        // Emit a device from the fake
        val device = BleDevice(name = "Pulse Ox", address = "AA:BB:CC:DD:EE:FF", rssi = -60)
        fakeRepository.scanFlow.emit(device)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.devices.size)
        assertEquals("AA:BB:CC:DD:EE:FF", viewModel.uiState.value.devices[0].address)
    }

    @Test
    fun `duplicate devices are not added`() = runTest {
        viewModel.onStartScan()
        advanceUntilIdle()

        val device = BleDevice(name = "Pulse Ox", address = "AA:BB:CC:DD:EE:FF", rssi = -60)
        fakeRepository.scanFlow.emit(device)
        fakeRepository.scanFlow.emit(device) // duplicate
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.devices.size)
    }

    // ─────────────────────────────────────────────────────────────────────
    // CONNECT
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `onDeviceSelected transitions to connected`() = runTest {
        viewModel.onDeviceSelected("AA:BB:CC:DD:EE:FF")
        advanceUntilIdle()

        fakeRepository.connectionFlow.emit(BleConnectionState.Connecting)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isConnected)

        fakeRepository.connectionFlow.emit(BleConnectionState.Connected)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isConnected)
    }

    @Test
    fun `connection error updates errorMessage`() = runTest {
        viewModel.onDeviceSelected("AA:BB:CC:DD:EE:FF")
        advanceUntilIdle()

        fakeRepository.connectionFlow.emit(
            BleConnectionState.Error(IllegalStateException("GATT 133"))
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isConnected)
        assertEquals("GATT 133", viewModel.uiState.value.errorMessage)
    }

    // ─────────────────────────────────────────────────────────────────────
    // MEASUREMENTS
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `pulse oximeter data updates spo2 and pulseRate`() = runTest {
        // Connect first
        viewModel.onDeviceSelected("AA:BB:CC:DD:EE:FF")
        advanceUntilIdle()
        fakeRepository.connectionFlow.emit(BleConnectionState.Connected)
        advanceUntilIdle()

        // Emit measurement
        fakeRepository.measurementFlow.emit(
            PulseOximeterData(spo2 = 97.5f, pulseRate = 72.0f)
        )
        advanceUntilIdle()

        assertEquals(97.5f, viewModel.uiState.value.spo2)
        assertEquals(72.0f, viewModel.uiState.value.pulseRate)
    }

    @Test
    fun `invalid measurement flag is reflected in state`() = runTest {
        viewModel.onDeviceSelected("AA:BB:CC:DD:EE:FF")
        advanceUntilIdle()
        fakeRepository.connectionFlow.emit(BleConnectionState.Connected)
        advanceUntilIdle()

        fakeRepository.measurementFlow.emit(
            PulseOximeterData(spo2 = 85.0f, pulseRate = 120.0f, isMeasurementValid = false)
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isMeasurementValid)
    }

    // ─────────────────────────────────────────────────────────────────────
    // DISCONNECT
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `onDisconnect resets connection state`() = runTest {
        viewModel.onDeviceSelected("AA:BB:CC:DD:EE:FF")
        advanceUntilIdle()
        fakeRepository.connectionFlow.emit(BleConnectionState.Connected)
        advanceUntilIdle()

        viewModel.onDisconnect()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isConnected)
        assertNull(viewModel.uiState.value.spo2)
        assertNull(viewModel.uiState.value.pulseRate)
        assertTrue(fakeRepository.disconnectCalled)
    }

    // ─────────────────────────────────────────────────────────────────────
    // RETRY
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `onRetry clears error and starts scanning`() = runTest {
        viewModel.onDeviceSelected("AA:BB:CC:DD:EE:FF")
        advanceUntilIdle()
        fakeRepository.connectionFlow.emit(
            BleConnectionState.Error(IllegalStateException("test"))
        )
        advanceUntilIdle()
        assertEquals("test", viewModel.uiState.value.errorMessage)

        viewModel.onRetry()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.isScanning)
    }
}

