package com.codeisland.buddylite.ble

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.codeisland.buddylite.BuddyLiteApp
import com.codeisland.buddylite.MainActivity
import com.codeisland.buddylite.data.BuddyRepository
import com.codeisland.buddylite.data.NotificationController

class BuddyPeripheralService : Service() {
    private lateinit var repository: BuddyRepository
    private val mainHandler = Handler(Looper.getMainLooper())

    private var bluetoothManager: BluetoothManager? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var connectedDevice: BluetoothDevice? = null
    private var subscribedDeviceAddress: String? = null
    private var subscribedDeliveryMode: HostUplinkDeliveryMode? = null

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> {
                    repository.logDiagnostic("INFO", "ble-peripheral", "手机蓝牙已开启，重新启动 Buddy 外设")
                    startPeripheral()
                }
                BluetoothAdapter.STATE_OFF,
                BluetoothAdapter.STATE_TURNING_OFF -> {
                    repository.onPeripheralBluetoothOff("手机蓝牙已关闭，Buddy 外设离线")
                    stopPeripheral(updateRepository = false)
                    refreshNotification()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = (application as BuddyLiteApp).locator.repository
        bluetoothManager = getSystemService(BluetoothManager::class.java)
        createNotificationChannel()
        registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        startForeground(
            NotificationController.SYNC_NOTIFICATION_ID,
            buildNotification("Buddy 正在启动")
        )
        startPeripheral()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_APPROVE -> notifyHostControl(BleProtocol.approveCurrentPermissionMarker)
            ACTION_DENY -> notifyHostControl(BleProtocol.denyCurrentPermissionMarker)
            ACTION_SKIP -> notifyHostControl(BleProtocol.skipCurrentQuestionMarker)
            ACTION_FOCUS -> notifyHostFocusRequest()
            else -> startPeripheral()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { unregisterReceiver(bluetoothStateReceiver) }
        stopPeripheral()
        super.onDestroy()
    }

    private fun startPeripheral() {
        repository.onPeripheralStarting("手机正在检查 Buddy BLE 外设能力")

        if (!hasRequiredPermissions()) {
            stopPeripheral(updateRepository = false)
            repository.onPeripheralPermissionRequired("缺少权限: ${missingPermissions().joinToString()}")
            refreshNotification()
            return
        }

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            stopPeripheral(updateRepository = false)
            repository.onPeripheralUnsupported("手机不支持 BLE")
            refreshNotification()
            return
        }

        val manager = bluetoothManager ?: run {
            stopPeripheral(updateRepository = false)
            repository.onPeripheralUnsupported("手机无法获取 BluetoothManager")
            refreshNotification()
            return
        }

        val adapter = manager.adapter ?: run {
            stopPeripheral(updateRepository = false)
            repository.onPeripheralUnsupported("手机不支持蓝牙")
            refreshNotification()
            return
        }

        if (!adapter.isEnabled) {
            stopPeripheral(updateRepository = false)
            repository.onPeripheralBluetoothOff("手机蓝牙未开启")
            refreshNotification()
            return
        }

        val bleAdvertiser = adapter.bluetoothLeAdvertiser ?: run {
            stopPeripheral(updateRepository = false)
            repository.onPeripheralUnsupported("手机不支持 BLE 广播")
            refreshNotification()
            return
        }

        if (!adapter.isMultipleAdvertisementSupported) {
            repository.logDiagnostic(
                "WARN",
                "ble-peripheral",
                "手机报告 isMultipleAdvertisementSupported=false，仍继续尝试启动广播"
            )
        }

        advertiser = bleAdvertiser
        openGattServer(manager)
        startAdvertising(bleAdvertiser)
    }

    private fun openGattServer(manager: BluetoothManager) {
        if (gattServer != null) return

        gattServer = manager.openGattServer(this, gattServerCallback)
        val server = gattServer
        if (server == null) {
            repository.onPeripheralError("Buddy GATT Server 打开失败")
            refreshNotification()
            return
        }

        val service = BleProtocol.createGattService()
        notifyCharacteristic = service.getCharacteristic(BleProtocol.notifyCharacteristicUuid)
        server.addService(service)
        repository.logDiagnostic("INFO", "ble-peripheral", "Buddy GATT Server 已启动")
    }

    private fun startAdvertising(bleAdvertiser: BluetoothLeAdvertiser) {
        runCatching { bleAdvertiser.stopAdvertising(advertiseCallback) }

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(BleProtocol.serviceUuid))
            .build()

        bleAdvertiser.startAdvertising(
            BleProtocol.createAdvertiseSettings(),
            data,
            advertiseCallback
        )
    }

    fun stopPeripheral(updateRepository: Boolean = true) {
        runCatching { advertiser?.stopAdvertising(advertiseCallback) }
        advertiser = null
        connectedDevice = null
        subscribedDeviceAddress = null
        subscribedDeliveryMode = null
        notifyCharacteristic = null
        gattServer?.close()
        gattServer = null
        if (updateRepository) {
            repository.onMacSubscriptionChanged(false)
            repository.onPeripheralDisconnected("Buddy 外设已停止")
        }
    }

    fun notifyHostControl(marker: Int) {
        notifyHostPayload(
            payload = byteArrayOf(marker.toByte()),
            successMessage = when (marker) {
                BleProtocol.approveCurrentPermissionMarker -> "手机已发送批准指令"
                BleProtocol.denyCurrentPermissionMarker -> "手机已发送拒绝指令"
                BleProtocol.skipCurrentQuestionMarker -> "手机已发送跳过指令"
                else -> "手机已发送控制指令"
            }
        )
    }

    fun notifyHostFocusRequest() {
        val mascot = repository.dashboardState.value.mascot
        notifyHostPayload(
            payload = byteArrayOf(mascot.wireId.toByte()),
            successMessage = "手机已请求聚焦 ${mascot.title}"
        )
    }

    private fun notifyHostPayload(
        payload: ByteArray,
        successMessage: String,
        remainingRetries: Int = MAX_NOTIFY_RETRIES,
    ) {
        val device = connectedDevice
        val mode = subscribedDeliveryMode
        val server = gattServer
        val characteristic = notifyCharacteristic

        if (device == null || server == null || characteristic == null || mode == null) {
            repository.logDiagnostic("WARN", "ble-uplink", "上行未发送：Mac 未连接或未订阅")
            return
        }

        @Suppress("DEPRECATION")
        run {
            characteristic.value = payload
        }

        val confirm = mode == HostUplinkDeliveryMode.INDICATION
        val sent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            server.notifyCharacteristicChanged(device, characteristic, confirm, payload) ==
                BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            server.notifyCharacteristicChanged(device, characteristic, confirm)
        }

        if (sent) {
            repository.logDiagnostic("INFO", "ble-uplink", successMessage)
            return
        }

        if (remainingRetries > 0) {
            mainHandler.postDelayed(
                {
                    notifyHostPayload(
                        payload = payload,
                        successMessage = successMessage,
                        remainingRetries = remainingRetries - 1
                    )
                },
                NOTIFY_RETRY_DELAY_MS
            )
        } else {
            repository.logDiagnostic("ERROR", "ble-uplink", "上行发送失败：Gatt notify 返回失败")
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            repository.onPeripheralAdvertising("Buddy 广播已启动，等待 Mac 连接")
            refreshNotification()
        }

        override fun onStartFailure(errorCode: Int) {
            repository.onPeripheralError("Buddy 广播启动失败: $errorCode")
            refreshNotification()
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    repository.onPeripheralConnected(device.safeName())
                    refreshNotification()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (connectedDevice?.address == device.address) {
                        connectedDevice = null
                    }
                    if (subscribedDeviceAddress == device.address) {
                        subscribedDeviceAddress = null
                        subscribedDeliveryMode = null
                        repository.onMacSubscriptionChanged(false)
                    }
                    repository.onPeripheralDisconnected("Mac 已断开，Buddy 继续等待连接")
                    refreshNotification()
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            val status = if (characteristic.uuid == BleProtocol.writeCharacteristicUuid) {
                val command = BuddyFrameParser.parse(value)
                if (command != null) {
                    repository.onIncomingCommand(command)
                    BluetoothGatt.GATT_SUCCESS
                } else {
                    repository.logDiagnostic("WARN", "ble-downlink", "收到无法解析的 Buddy 帧: ${value.size} bytes")
                    BluetoothGatt.GATT_FAILURE
                }
            } else {
                BluetoothGatt.GATT_FAILURE
            }

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, status, offset, null)
            }
            refreshNotification()
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor,
        ) {
            val value = if (descriptor.uuid == BleProtocol.cccDescriptorUuid) {
                BleProtocol.cccDescriptorValueFor(
                    if (device.address == subscribedDeviceAddress) subscribedDeliveryMode else null
                )
            } else {
                null
            }
            gattServer?.sendResponse(
                device,
                requestId,
                if (value == null) BluetoothGatt.GATT_FAILURE else BluetoothGatt.GATT_SUCCESS,
                offset,
                value
            )
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            val status = if (descriptor.uuid == BleProtocol.cccDescriptorUuid) {
                val mode = BleProtocol.hostUplinkDeliveryModeForDescriptorValue(value)
                val cccStatus = when {
                    mode != null -> {
                        subscribedDeviceAddress = device.address
                        subscribedDeliveryMode = mode
                        repository.onMacSubscriptionChanged(true)
                        BluetoothGatt.GATT_SUCCESS
                    }
                    value.contentEquals(BleProtocol.cccDisableValue) -> {
                        subscribedDeviceAddress = null
                        subscribedDeliveryMode = null
                        repository.onMacSubscriptionChanged(false)
                        BluetoothGatt.GATT_SUCCESS
                    }
                    else -> {
                        repository.logDiagnostic("WARN", "ble-gatt", "收到无效 CCC 描述符值")
                        BluetoothGatt.GATT_FAILURE
                    }
                }
                if (cccStatus == BluetoothGatt.GATT_SUCCESS) {
                    @Suppress("DEPRECATION")
                    run {
                        descriptor.value = value
                    }
                }
                cccStatus
            } else {
                BluetoothGatt.GATT_FAILURE
            }

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, status, offset, null)
            }
            refreshNotification()
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return missingPermissions().isEmpty()
    }

    private fun missingPermissions(): List<String> {
        return buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE)) add("BLUETOOTH_ADVERTISE")
                if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) add("BLUETOOTH_CONNECT")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (!hasPermission(Manifest.permission.POST_NOTIFICATIONS)) add("POST_NOTIFICATIONS")
            }
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun BluetoothDevice.safeName(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        ) {
            address
        } else {
            @Suppress("DEPRECATION")
            name?.takeIf { it.isNotBlank() } ?: address
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            NotificationController.CHANNEL_SYNC,
            "Buddy 同步",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Buddy BLE 外设前台服务"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun refreshNotification() {
        val state = repository.dashboardState.value
        val label = state.connectedDeviceName?.let { "已连接 $it" } ?: state.peripheralState.label
        getSystemService(NotificationManager::class.java)
            ?.notify(NotificationController.SYNC_NOTIFICATION_ID, buildNotification(label))
    }

    private fun buildNotification(content: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NotificationController.CHANNEL_SYNC)
            .setContentTitle("Buddy")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        const val ACTION_APPROVE = "com.codeisland.buddylite.APPROVE"
        const val ACTION_DENY = "com.codeisland.buddylite.DENY"
        const val ACTION_SKIP = "com.codeisland.buddylite.SKIP"
        const val ACTION_FOCUS = "com.codeisland.buddylite.FOCUS"

        private const val MAX_NOTIFY_RETRIES = 2
        private const val NOTIFY_RETRY_DELAY_MS = 120L

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, BuddyPeripheralService::class.java)
            )
        }

        fun approve(context: Context) {
            startAction(context, ACTION_APPROVE)
        }

        fun deny(context: Context) {
            startAction(context, ACTION_DENY)
        }

        fun skip(context: Context) {
            startAction(context, ACTION_SKIP)
        }

        fun focus(context: Context) {
            startAction(context, ACTION_FOCUS)
        }

        private fun startAction(context: Context, action: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, BuddyPeripheralService::class.java).setAction(action)
            )
        }
    }
}
