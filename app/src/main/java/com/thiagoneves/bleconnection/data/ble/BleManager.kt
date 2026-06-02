package com.thiagoneves.bleconnection.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.thiagoneves.bleconnection.domain.model.BleConnectionState
import com.thiagoneves.bleconnection.domain.model.BleDevice
import com.thiagoneves.bleconnection.domain.model.PulseOximeterData
import com.thiagoneves.bleconnection.domain.protocol.ProtocolEvent
import com.thiagoneves.bleconnection.domain.protocol.PulseOximeterProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Protocol-agnostic wrapper around Android's BLE APIs, exposing scan / connect / measurement
 * pipelines as cold [Flow]s. Byte-level decoding is delegated to the injected
 * [PulseOximeterProtocol] (Strategy pattern), so this class never deals with payload formats.
 */
@Singleton
class BleManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter,
    private val protocol: PulseOximeterProtocol
) {

    companion object {
        private const val TAG = "BleManager"

        /** Client Characteristic Configuration Descriptor — write here to enable notifications. */
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    /**
     * Scan for nearby BLE devices.
     *
     * We scan WITHOUT a [android.bluetooth.le.ScanFilter] on purpose: some peripherals
     * (notably ESP32 with the Arduino BLE library) push their 128-bit service UUID into
     * the scan response packet instead of the primary advertising packet, and several
     * Android vendor stacks won't match a service-UUID filter against the scan response.
     * A filtered scan would silently return nothing. We validate the service at connect
     * time instead, inside [BluetoothGattCallback.onServicesDiscovered].
     */
    @SuppressLint("MissingPermission")
    fun scanForDevices(): Flow<BleDevice> = callbackFlow {
        val scanner = bluetoothAdapter.bluetoothLeScanner
            ?: run {
                close(IllegalStateException("Bluetooth is disabled or BLE scanner unavailable"))
                return@callbackFlow
            }

        Log.d(TAG, "startScan — target service ${protocol.serviceUuid}")
        val targetService = ParcelUuid(protocol.serviceUuid)

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val advertisedUuids = result.scanRecord?.serviceUuids.orEmpty()
                val matchesProtocol = advertisedUuids.contains(targetService)
                Log.d(
                    TAG,
                    "scanResult name=${device.name} addr=${device.address} " +
                        "rssi=${result.rssi} match=$matchesProtocol"
                )
                trySend(BleDevice(name = device.name, address = device.address, rssi = result.rssi))
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "onScanFailed errorCode=$errorCode")
                close(IllegalStateException("BLE scan failed with error code: $errorCode"))
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        scanner.startScan(null, settings, scanCallback)

        awaitClose {
            Log.d(TAG, "stopScan — collector cancelled")
            scanner.stopScan(scanCallback)
        }
    }.flowOn(Dispatchers.IO)

    @Volatile
    private var activeGatt: BluetoothGatt? = null

    /** Connect to [address], enable notifications and let the protocol drive the conversation. */
    @SuppressLint("MissingPermission")
    fun connect(address: String): Flow<BleConnectionState> = callbackFlow {
        val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(address)
        trySend(BleConnectionState.Connecting)

        val gattCallback = object : BluetoothGattCallback() {

            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when {
                    newState == BluetoothProfile.STATE_CONNECTED &&
                            status == BluetoothGatt.GATT_SUCCESS -> {
                        gatt.discoverServices()
                    }
                    newState == BluetoothProfile.STATE_DISCONNECTED -> {
                        val reason = if (status == BluetoothGatt.GATT_SUCCESS) {
                            "Device disconnected normally"
                        } else {
                            "Connection lost (GATT status: $status)"
                        }
                        trySend(BleConnectionState.Disconnected(reason))
                        gatt.close()
                        activeGatt = null
                    }
                    else -> {
                        trySend(
                            BleConnectionState.Error(
                                IllegalStateException("GATT error – status: $status, state: $newState")
                            )
                        )
                        gatt.close()
                        activeGatt = null
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    trySend(
                        BleConnectionState.Error(
                            IllegalStateException("Service discovery failed with status: $status")
                        )
                    )
                    gatt.disconnect()
                    return
                }

                val service = gatt.getService(protocol.serviceUuid)
                if (service == null) {
                    trySend(
                        BleConnectionState.Error(
                            IllegalStateException("Service ${protocol.serviceUuid} not found on device")
                        )
                    )
                    gatt.disconnect()
                    return
                }

                val measurementChar = service.getCharacteristic(protocol.measurementCharacteristicUuid)
                if (measurementChar == null) {
                    trySend(
                        BleConnectionState.Error(
                            IllegalStateException(
                                "Characteristic ${protocol.measurementCharacteristicUuid} not found"
                            )
                        )
                    )
                    gatt.disconnect()
                    return
                }

                if (!gatt.setCharacteristicNotification(measurementChar, true)) {
                    trySend(
                        BleConnectionState.Error(
                            IllegalStateException("Failed to enable local notifications")
                        )
                    )
                    gatt.disconnect()
                    return
                }

                measurementChar.getDescriptor(CCCD_UUID)?.let { cccd ->
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(cccd)
                }

                // Let the protocol kick off any handshake required.
                handleProtocolEvent(gatt, service, protocol.onConnected())

                trySend(BleConnectionState.Connected)
            }

            @Deprecated("Deprecated in API 33, kept for minSdk 24 compatibility")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                if (characteristic.uuid != protocol.measurementCharacteristicUuid) return
                val value = characteristic.value ?: return

                when (val event = protocol.onNotification(value)) {
                    is ProtocolEvent.Measurement -> measurementChannel?.trySend(event.data)
                    is ProtocolEvent.SendCommand -> {
                        gatt.getService(protocol.serviceUuid)?.let { service ->
                            handleProtocolEvent(gatt, service, event)
                        }
                    }
                    is ProtocolEvent.Ignore -> Unit
                }
            }
        }

        activeGatt = device.connectGatt(
            context,
            /* autoConnect = */ false,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        )

        awaitClose {
            activeGatt?.disconnect()
            activeGatt?.close()
            activeGatt = null
        }
    }.flowOn(Dispatchers.IO)

    @SuppressLint("MissingPermission")
    private fun handleProtocolEvent(
        gatt: BluetoothGatt,
        service: android.bluetooth.BluetoothGattService,
        event: ProtocolEvent
    ) {
        if (event !is ProtocolEvent.SendCommand) return
        val writeUuid = protocol.writeCharacteristicUuid ?: return
        val writeChar = service.getCharacteristic(writeUuid) ?: return
        writeChar.value = event.payload
        gatt.writeCharacteristic(writeChar)
    }

    @Volatile
    private var measurementChannel: SendChannel<PulseOximeterData>? = null

    /** Cold flow of pulse oximeter measurements emitted by the connected device. */
    fun measurementFlow(): Flow<PulseOximeterData> = callbackFlow {
        measurementChannel = channel
        awaitClose { measurementChannel = null }
    }.flowOn(Dispatchers.IO)

    @SuppressLint("MissingPermission")
    fun disconnect() {
        activeGatt?.disconnect()
        activeGatt?.close()
        activeGatt = null
    }
}

