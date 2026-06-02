# Bleconnection — Android BLE Pulse Oximeter Client

Reference implementation of an Android Bluetooth Low Energy client
for medical pulse-oximetry hardware.

It connects to any device that implements the **Bluetooth SIG Pulse Oximeter
Service (PLX, `0x1822`)**, decodes IEEE-11073 SFLOAT measurements, and displays
live SpO₂ and pulse-rate readings with color-coded medical thresholds. The BLE
transport is fully decoupled from the wire protocol via a
`PulseOximeterProtocol` Strategy interface, so adding a new device family is a
single Hilt binding away.

## Stack

Kotlin · Jetpack Compose · Coroutines & Flow · Hilt · Material 3 ·
Accompanist Permissions · JUnit + Turbine for tests.

## Architecture

Clean Architecture in four layers:

- **`domain/`** — pure Kotlin models, repository contract, use cases, protocol
  Strategy interface. No Android imports.
- **`data/`** — `BleManager` (GATT plumbing), `StandardPlxProtocol` (SFLOAT
  decoder), `BleRepositoryImpl` (exponential-backoff retry).
- **`di/`** — Hilt modules (`BleModule`, `RepositoryModule`, `ProtocolModule`).
- **`feature/pulseoximeter/`** — Compose UI + `@HiltViewModel` exposing a
  single `StateFlow<PulseOximeterUiState>`.

```
┌──────────────────────────────────────────────┐
│ feature/pulseoximeter                        │ ← Compose + StateFlow
│   PulseOximeterScreen · PulseOximeterViewModel│
├──────────────────────────────────────────────┤
│ domain                                        │ ← Pure Kotlin
│   model · usecase · repository · protocol    │
├──────────────────────────────────────────────┤
│ data                                          │
│   ble/BleManager (protocol-agnostic)          │
│   ble/protocol/StandardPlxProtocol            │ ← Strategy
│   repository/BleRepositoryImpl                │ ← Retry + backoff
├──────────────────────────────────────────────┤
│ di                                            │
│   BleModule · RepositoryModule · ProtocolModule│ ← Hilt
└──────────────────────────────────────────────┘
```

## BLE highlights

- Cold `Flow` built with `callbackFlow` + `awaitClose` to guarantee
  `BluetoothGatt.close()` on every cancellation path — no leaked connections.
- Strategy pattern (`PulseOximeterProtocol`) decoupling GATT plumbing from the
  wire format. Currently shipping a Bluetooth SIG PLX implementation
  (service `0x1822`, characteristic `0x2A5F`) with a fully documented
  IEEE-11073 SFLOAT decoder.
- Exponential-backoff reconnection (1s → 2s → 4s → 8s → 16s → 30s cap)
  implemented in the data layer, transparent to the ViewModel.
- Scan logic intentionally skips `ScanFilter.setServiceUuid()` to work around
  vendor stacks (Samsung/Exynos) that don't match filters against the scan
  response payload — protocol validation happens at connect time in
  `onServicesDiscovered` instead.

## UI

Single-screen Jetpack Compose feature with:

- Android 12+ split-permission flow (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`)
  with legacy `ACCESS_FINE_LOCATION` fallback for API 24-30.
- Live device list with RSSI.
- Color-coded SpO₂ display: **green ≥ 95 %**, **amber 90-94 %**, **red < 90 %**.
- Low-confidence reading badge when the device flags the measurement as
  unreliable.
- Start / Stop scan buttons — Stop simply cancels the coroutine and lets
  `awaitClose` shut the radio down (centralized cleanup, zero coupling).

## Testing

- `StandardPlxProtocolTest` — exhaustive SFLOAT edge cases (negative
  exponents, special values, optional fields, frame length guards).
- `PulseOximeterViewModelTest` — UI state transitions driven by a
  `FakeBleRepository` (`MutableSharedFlow`), using `StandardTestDispatcher`.
- `FakeBleRepository` — minimal handcrafted test double, no mocking framework.

## Requirements

- Android Studio Hedgehog or newer.
- minSdk 24, targetSdk 34.
- A BLE-capable Android device (emulator BLE is unreliable).
- Any Bluetooth SIG-compliant pulse oximeter, **or** an ESP32 flashed with a
  PLX-compatible firmware that advertises service `0x1822`.

## License

MIT.

