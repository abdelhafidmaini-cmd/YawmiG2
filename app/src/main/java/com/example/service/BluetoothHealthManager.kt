package com.example.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class DiscoveredBleDevice(
    val address: String,
    val name: String,
    val rssi: Int,
    val family: String // xiaomi_band_10 | honor_band_4 | generic_ble
)

data class BleConnectionState(
    val isBluetoothSupported: Boolean = true,
    val isBluetoothEnabled: Boolean = false,
    val hasPermissions: Boolean = false,
    val isScanning: Boolean = false,
    val discoveredDevices: List<DiscoveredBleDevice> = emptyList(),
    val connectedAddress: String? = null,
    val connectedName: String? = null,
    val connectionStatus: String = "disconnected", // disconnected | connecting | connected | connection_failed
    val batteryLevel: Int? = null,
    val liveHeartRateBpm: Int? = null,
    val statusMessage: String? = null
)

class BluetoothHealthManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(
        BleConnectionState(
            isBluetoothSupported = bluetoothAdapter != null,
            isBluetoothEnabled = bluetoothAdapter?.isEnabled == true,
            hasPermissions = hasRequiredBlePermissions()
        )
    )
    val state: StateFlow<BleConnectionState> = _state.asStateFlow()

    private var activeGatt: BluetoothGatt? = null
    private var onVitalReceived: ((metricType: String, value: Double, unit: String, deviceName: String) -> Unit)? = null

    fun setVitalCallback(callback: (metricType: String, value: Double, unit: String, deviceName: String) -> Unit) {
        onVitalReceived = callback
    }

    fun hasRequiredBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun refreshState() {
        _state.update {
            it.copy(
                isBluetoothSupported = bluetoothAdapter != null,
                isBluetoothEnabled = bluetoothAdapter?.isEnabled == true,
                hasPermissions = hasRequiredBlePermissions()
            )
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device: BluetoothDevice = result.device ?: return
            val rawName = try {
                device.name ?: result.scanRecord?.deviceName
            } catch (e: SecurityException) {
                null
            }
            val displayName = if (!rawName.isNullOrBlank()) rawName else "BLE Health Device (${device.address.takeLast(5)})"
            val serviceUuids = result.scanRecord?.serviceUuids?.map { it.uuid.toString().lowercase() }.orEmpty()

            val family = when {
                displayName.contains("Band 10", ignoreCase = true) ||
                    displayName.contains("Xiaomi", ignoreCase = true) ||
                    displayName.contains("Mi Band", ignoreCase = true) ||
                    serviceUuids.any { it.contains("fe95") } -> "xiaomi_band_10"
                displayName.contains("HONOR", ignoreCase = true) ||
                    displayName.contains("Huawei", ignoreCase = true) ||
                    displayName.contains("CRS-B19", ignoreCase = true) ||
                    serviceUuids.any { it.contains("fe86") } -> "honor_band_4"
                else -> "generic_ble"
            }

            val entry = DiscoveredBleDevice(
                address = device.address,
                name = displayName,
                rssi = result.rssi,
                family = family
            )

            _state.update { current ->
                val updatedList = current.discoveredDevices.toMutableList()
                val idx = updatedList.indexOfFirst { it.address == entry.address }
                if (idx >= 0) {
                    updatedList[idx] = entry
                } else {
                    updatedList.add(entry)
                }
                current.copy(discoveredDevices = updatedList.sortedByDescending { it.rssi })
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _state.update {
                it.copy(
                    isScanning = false,
                    statusMessage = "BLE Scan failed (code $errorCode). Ensure Bluetooth & Location are enabled."
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        refreshState()
        if (bluetoothAdapter == null) {
            _state.update { it.copy(statusMessage = "Bluetooth LE is not supported on this device/emulator.") }
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            _state.update { it.copy(statusMessage = "Bluetooth is turned off. Please enable Bluetooth in system settings.") }
            return
        }
        if (!hasRequiredBlePermissions()) {
            _state.update { it.copy(statusMessage = "Bluetooth permissions are required to scan for wearables.") }
            return
        }
        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            _state.update { it.copy(statusMessage = "Bluetooth LE Scanner is unavailable right now.") }
            return
        }

        try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            _state.update { it.copy(isScanning = true, statusMessage = "Scanning for nearby Bluetooth health devices...") }
            scanner.startScan(null, settings, scanCallback)

            mainHandler.postDelayed({
                stopScan()
            }, 10_000L)
        } catch (e: Exception) {
            _state.update { it.copy(isScanning = false, statusMessage = "Unable to start BLE scan: ${e.localizedMessage}") }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_state.value.isScanning) return
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {
        }
        _state.update {
            it.copy(
                isScanning = false,
                statusMessage = if (it.discoveredDevices.isEmpty()) {
                    "Scan complete. No BLE wearables found nearby."
                } else {
                    "Found ${it.discoveredDevices.size} Bluetooth device(s)."
                }
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: DiscoveredBleDevice) {
        if (!hasRequiredBlePermissions() || bluetoothAdapter == null) {
            _state.update { it.copy(statusMessage = "Missing Bluetooth permissions.") }
            return
        }
        stopScan()
        activeGatt?.close()
        _state.update {
            it.copy(
                connectedAddress = device.address,
                connectedName = device.name,
                connectionStatus = "connecting",
                statusMessage = "Connecting to ${device.name}..."
            )
        }

        try {
            val btDevice = bluetoothAdapter.getRemoteDevice(device.address)
            activeGatt = btDevice.connectGatt(context, false, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        _state.update {
                            it.copy(
                                connectionStatus = "connected",
                                statusMessage = "Connected to ${device.name}. Discovering health services..."
                            )
                        }
                        gatt.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        _state.update {
                            it.copy(
                                connectionStatus = if (status == BluetoothGatt.GATT_SUCCESS) "disconnected" else "connection_failed",
                                batteryLevel = null,
                                liveHeartRateBpm = null,
                                statusMessage = "Disconnected from ${device.name}."
                            )
                        }
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) return
                    // Read standard Battery Service (0x180F -> 0x2A19)
                    val batteryService = gatt.getService(BATTERY_SERVICE_UUID)
                    val batteryChar = batteryService?.getCharacteristic(BATTERY_LEVEL_CHAR_UUID)
                    if (batteryChar != null) {
                        gatt.readCharacteristic(batteryChar)
                    }

                    // Subscribe to standard Heart Rate Service (0x180D -> 0x2A37)
                    val hrService = gatt.getService(HEART_RATE_SERVICE_UUID)
                    val hrChar = hrService?.getCharacteristic(HEART_RATE_MEASUREMENT_CHAR_UUID)
                    if (hrChar != null) {
                        gatt.setCharacteristicNotification(hrChar, true)
                        val descriptor = hrChar.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                        if (descriptor != null) {
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onCharacteristicRead(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int
                ) {
                    if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == BATTERY_LEVEL_CHAR_UUID) {
                        val battery = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                        if (battery != null) {
                            _state.update {
                                it.copy(
                                    batteryLevel = battery,
                                    statusMessage = "${device.name} connected • Battery $battery%"
                                )
                            }
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic
                ) {
                    if (characteristic.uuid == HEART_RATE_MEASUREMENT_CHAR_UUID) {
                        val flag = characteristic.properties
                        val format = if ((flag and 0x01) != 0) {
                            BluetoothGattCharacteristic.FORMAT_UINT16
                        } else {
                            BluetoothGattCharacteristic.FORMAT_UINT8
                        }
                        val bpm = characteristic.getIntValue(format, 1) ?: return
                        if (bpm in 30..240) {
                            _state.update {
                                it.copy(
                                    liveHeartRateBpm = bpm,
                                    statusMessage = "Live Heart Rate from ${device.name}: $bpm bpm"
                                )
                            }
                            onVitalReceived?.invoke("heart_rate", bpm.toDouble(), "bpm", device.name)
                        }
                    }
                }
            })
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    connectionStatus = "connection_failed",
                    statusMessage = "GATT connection failed: ${e.localizedMessage}"
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            activeGatt?.disconnect()
            activeGatt?.close()
        } catch (_: Exception) {
        }
        activeGatt = null
        _state.update {
            it.copy(
                connectedAddress = null,
                connectedName = null,
                connectionStatus = "disconnected",
                batteryLevel = null,
                liveHeartRateBpm = null,
                statusMessage = "Disconnected from wearable."
            )
        }
    }

    companion object {
        val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL_CHAR_UUID: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
