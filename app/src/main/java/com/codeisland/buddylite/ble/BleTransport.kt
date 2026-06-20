package com.codeisland.buddylite.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.codeisland.buddylite.data.model.ConnectionState
import com.codeisland.buddylite.protocol.TransportEvent
import com.codeisland.buddylite.protocol.TransportLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.min

class BleTransport(
    private val context: Context
) : TransportLayer {

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("6D951BA3-8F41-4C45-9D8A-12085E0D7A10")
        val NOTIFY_CHAR_UUID: UUID = UUID.fromString("25C1B67B-E903-4A0C-8A78-3EE8AB7317B7")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val MTU_TARGET = 517
        private const val MTU_FALLBACK = 185
        private const val MTU_MINIMUM = 135
        private const val MAX_RECONNECT_BACKOFF_MS = 30_000L
        private const val BASE_RECONNECT_MS = 1_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val stateMachine = BleStateMachine()
    private val gattQueue = GattOperationQueue()
    private val chunkReassembler = CiChunkReassembler()
    private val diagnostics = BleDiagnostics { level, tag, msg ->
        _events.tryEmit(TransportEvent.Error("[$tag] $msg"))
    }

    private val _events = MutableSharedFlow<TransportEvent>(replay = 0, extraBufferCapacity = 32)
    override val events: Flow<TransportEvent> = _events

    @Volatile
    override var isRunning: Boolean = false
        private set

    private var bluetoothGatt: BluetoothGatt? = null
    private var reconnectAttempt = 0
    private var reconnectJob: kotlinx.coroutines.Job? = null

    private val btStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                        BluetoothAdapter.STATE_OFF -> {
                            diagnostics.log("INFO", "system", "Bluetooth turned off")
                            _events.tryEmit(TransportEvent.BluetoothOff)
                            cleanup()
                        }
                        BluetoothAdapter.STATE_ON -> {
                            diagnostics.log("INFO", "system", "Bluetooth turned on")
                            _events.tryEmit(TransportEvent.BluetoothOn)
                            if (isRunning) startScan()
                        }
                    }
                }
            }
        }
    }

    init {
        context.registerReceiver(
            btStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        )
    }

    override fun start() {
        if (!hasBluetoothPermission()) {
            stateMachine.forceState(ConnectionState.PERMISSION_BLOCKED)
            _events.tryEmit(
                TransportEvent.PermissionDenied(getMissingPermissions())
            )
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            stateMachine.forceState(ConnectionState.DISCONNECTED)
            _events.tryEmit(TransportEvent.BluetoothOff)
            return
        }

        isRunning = true
        _events.tryEmit(TransportEvent.Started)
        startScan()
    }

    override fun stop() {
        isRunning = false
        reconnectJob?.cancel()
        cleanup()
        stateMachine.forceState(ConnectionState.IDLE)
    }

    private fun startScan() {
        stateMachine.transition(ConnectionState.SCANNING)
        diagnostics.scanStarted()

        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            _events.tryEmit(TransportEvent.Error("BLE scanner unavailable", null))
            return
        }

        try {
            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build()
            scanner.startScan(listOf(filter), settings, scanCallback)
        } catch (e: SecurityException) {
            _events.tryEmit(TransportEvent.Error("Scan permission denied: ${e.message}", e))
        }
    }

    private fun stopScan() {
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: SecurityException) {}
        diagnostics.scanStopped()
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice, name: String) {
        stopScan()
        reconnectAttempt = 0
        stateMachine.transition(ConnectionState.CONNECTING)
        diagnostics.connecting(device.address)
        _events.tryEmit(TransportEvent.Connecting)

        bluetoothGatt = device.connectGatt(
            context,
            false,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        )
        gattQueue.bind(bluetoothGatt!!)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = result.scanRecord?.deviceName ?: device.name ?: "CodeIsland Mac"
            diagnostics.deviceDiscovered(name, device.address, result.rssi)

            if (stateMachine.currentState == ConnectionState.SCANNING) {
                _events.tryEmit(TransportEvent.Discovered(name, device.address))
                connect(device, name)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            diagnostics.error("ble-scan", "Scan failed with code $errorCode")
            _events.tryEmit(TransportEvent.Error("BLE scan failed: code $errorCode", null))
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        diagnostics.connected(gatt.device.name ?: "Unknown")
                        stateMachine.transition(ConnectionState.DISCOVERING_SERVICES)
                        _events.tryEmit(
                            TransportEvent.Connected(gatt.device.name ?: "CodeIsland Mac")
                        )
                        scope.launch { executeConnectSequence() }
                    } else {
                        diagnostics.error("ble-connect", "Connection failed with status $status")
                        handleDisconnect()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    diagnostics.disconnected()
                    handleDisconnect()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                diagnostics.servicesDiscovered(gatt.services.size)
                gattQueue.onGattCallbackSuccess()
            } else {
                gattQueue.onGattCallbackFailure(status)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                diagnostics.mtuNegotiated(mtu)
                gattQueue.onGattCallbackSuccess()
            } else {
                gattQueue.onGattCallbackFailure(status)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gattQueue.onGattCallbackSuccess()
            } else {
                gattQueue.onGattCallbackFailure(status)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid != NOTIFY_CHAR_UUID) return
            diagnostics.notificationReceived(value.size)

            val chunk = CiChunkParser.parse(value)
            if (chunk == null) {
                diagnostics.chunkParseError("Parse returned null, size=${value.size}")
                return
            }

            when (val result = chunkReassembler.accept(chunk)) {
                is ReassemblyResult.Complete -> {
                    _events.tryEmit(TransportEvent.SummaryReceived(result.body))
                }
                is ReassemblyResult.RejectedStale -> { /* expected for retransmitted chunks */ }
                is ReassemblyResult.RejectedCorrupt -> {
                    diagnostics.chunkParseError("Corrupt chunk: seq=${chunk.sequence} idx=${chunk.index}")
                }
                is ReassemblyResult.RejectedDuplicate -> { /* ignore */ }
                is ReassemblyResult.Pending -> { /* waiting for more chunks */ }
            }
        }
    }

    private suspend fun executeConnectSequence() {
        // Step 1: Discover services
        val serviceResult = gattQueue.execute(GattOperation.DiscoverServices)
        if (serviceResult is GattResult.Failure) {
            _events.tryEmit(TransportEvent.Error("Service discovery: ${serviceResult.message}", null))
            disconnectAndRetry()
            return
        }

        // Step 2: Request MTU (target 517, fallback 185, minimum 135)
        stateMachine.transition(ConnectionState.REQUESTING_MTU)
        val mtuResult = gattQueue.execute(GattOperation.RequestMtu(MTU_TARGET))
        var mtu = when (mtuResult) {
            is GattResult.Success -> MTU_TARGET
            is GattResult.Failure -> {
                // Try fallback
                diagnostics.log("INFO", "ble-mtu", "MTU $MTU_TARGET failed, trying $MTU_FALLBACK")
                val fallbackResult = gattQueue.execute(GattOperation.RequestMtu(MTU_FALLBACK))
                when (fallbackResult) {
                    is GattResult.Success -> MTU_FALLBACK
                    is GattResult.Failure -> MTU_MINIMUM
                }
            }
        }

        if (mtu < MTU_MINIMUM) {
            _events.tryEmit(TransportEvent.Error("MTU too small: $mtu (minimum: $MTU_MINIMUM)", null))
            disconnectAndRetry()
            return
        }
        _events.tryEmit(TransportEvent.MtuNegotiated(mtu))

        // Step 3: Enable notification (synchronous on Android)
        stateMachine.transition(ConnectionState.SUBSCRIBING)
        val notifResult = gattQueue.execute(
            GattOperation.SetCharacteristicNotification(NOTIFY_CHAR_UUID, enable = true)
        )
        if (notifResult is GattResult.Failure) {
            _events.tryEmit(TransportEvent.Error("Enable notification: ${notifResult.message}", null))
            disconnectAndRetry()
            return
        }

        // Step 4: Write CCCD descriptor (asynchronous)
        val cccdResult = gattQueue.execute(
            GattOperation.WriteDescriptor(
                characteristic = NOTIFY_CHAR_UUID,
                descriptor = CCCD_UUID,
                value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            )
        )
        if (cccdResult is GattResult.Failure) {
            _events.tryEmit(TransportEvent.Error("Write CCCD: ${cccdResult.message}", null))
            disconnectAndRetry()
            return
        }

        // Connected and subscribed — reset backoff on success
        diagnostics.subscribed()
        reconnectAttempt = 0
        stateMachine.transition(ConnectionState.CONNECTED)
        _events.tryEmit(TransportEvent.Subscribed)
    }

    private fun handleDisconnect() {
        gattQueue.unbind()
        stateMachine.forceState(ConnectionState.DISCONNECTED)
        _events.tryEmit(TransportEvent.Disconnected)
        closeGatt()
        if (isRunning) scheduleReconnect()
    }

    private fun disconnectAndRetry() {
        gattQueue.unbind()
        stateMachine.forceState(ConnectionState.DISCONNECTED)
        closeGatt()
        if (isRunning) scheduleReconnect()
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val delayMs = min(
                BASE_RECONNECT_MS * (1L shl reconnectAttempt.coerceAtMost(5)),
                MAX_RECONNECT_BACKOFF_MS
            ) + (Math.random() * 500).toLong()
            diagnostics.log("INFO", "reconnect", "Reconnect in ${delayMs}ms (attempt ${reconnectAttempt + 1})")
            delay(delayMs)
            reconnectAttempt++
            if (isRunning) startScan()
        }
    }

    private fun cleanup() {
        reconnectJob?.cancel()
        chunkReassembler.reset()
        gattQueue.unbind()
        closeGatt()
        try { stopScan() } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    private fun hasBluetoothPermission(): Boolean {
        val required = getRequiredPermissions()
        return required.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getMissingPermissions(): List<String> {
        return getRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getRequiredPermissions(): List<String> {
        return buildList {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
    }
}
