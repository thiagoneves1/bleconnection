package com.thiagoneves.bleconnection.feature.pulseoximeter

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.shouldShowRationale
import com.thiagoneves.bleconnection.domain.model.BleDevice

/** Root composable: permission gate → scan + device list → live SpO2/PR display. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun PulseOximeterScreen(
    viewModel: PulseOximeterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val blePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val permissionState = rememberMultiplePermissionsState(permissions = blePermissions)

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            val result = snackbarHostState.showSnackbar(
                message = message, actionLabel = "Retry", withDismissAction = true
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.onRetry()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Pulse Oximeter") }) },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                !permissionState.allPermissionsGranted -> PermissionRequestContent(
                    shouldShowRationale = permissionState.permissions.any { it.status.shouldShowRationale },
                    onRequestPermissions = { permissionState.launchMultiplePermissionRequest() }
                )
                uiState.isConnected -> ConnectedContent(
                    spo2 = uiState.spo2,
                    pulseRate = uiState.pulseRate,
                    isMeasurementValid = uiState.isMeasurementValid,
                    onDisconnect = { viewModel.onDisconnect() }
                )
                else -> ScanningContent(
                    isScanning = uiState.isScanning,
                    devices = uiState.devices,
                    onStartScan = { viewModel.onStartScan() },
                    onStopScan = { viewModel.onStopScan() },
                    onDeviceSelected = { address -> viewModel.onDeviceSelected(address) }
                )
            }
        }
    }
}

@Composable
private fun PermissionRequestContent(
    shouldShowRationale: Boolean,
    onRequestPermissions: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (shouldShowRationale) {
                "Bluetooth permissions are required to scan for and connect to your pulse oximeter. " +
                    "Please grant them to continue."
            } else {
                "This app needs Bluetooth permissions to find and connect to your pulse oximeter."
            },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequestPermissions) { Text("Grant Permissions") }
    }
}

@Composable
private fun ScanningContent(
    isScanning: Boolean,
    devices: List<BleDevice>,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onDeviceSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(onClick = onStartScan, enabled = !isScanning) {
            Text(if (isScanning) "Scanning..." else "Start Scan")
        }
        Spacer(modifier = Modifier.width(12.dp))
        OutlinedButton(onClick = onStopScan, enabled = isScanning) {
            Text("Stop")
        }
        if (isScanning) {
            Spacer(modifier = Modifier.width(16.dp))
            CircularProgressIndicator(modifier = Modifier.height(24.dp).width(24.dp))
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items = devices, key = { it.address }) { device ->
            DeviceCard(device = device, onConnect = { onDeviceSelected(device.address) })
        }
    }
}

@Composable
private fun DeviceCard(device: BleDevice, onConnect: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: "Unknown Device",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Signal: ${device.rssi} dBm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onConnect) { Text("Connect") }
        }
    }
}

/**
 * Displays live SpO2 (color-coded ≥95 green, 90-94 amber, <90 red) and Pulse Rate.
 */
@Composable
private fun ConnectedContent(
    spo2: Float?,
    pulseRate: Float?,
    isMeasurementValid: Boolean,
    onDisconnect: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "SpO2",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            val spo2Color = when {
                spo2 == null -> MaterialTheme.colorScheme.primary
                spo2 >= 95f  -> Color(0xFF4CAF50)
                spo2 >= 90f  -> Color(0xFFFFC107)
                else         -> Color(0xFFF44336)
            }

            Text(
                text = if (spo2 != null) "${spo2.toInt()}" else "--",
                fontSize = 96.sp,
                fontWeight = FontWeight.Bold,
                color = spo2Color
            )
            Text(
                text = "%",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isMeasurementValid) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "⚠ Low confidence reading",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFFC107)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Pulse Rate",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (pulseRate != null) "${pulseRate.toInt()}" else "--",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "BPM",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(onClick = onDisconnect) { Text("Disconnect") }
        }
    }
}

