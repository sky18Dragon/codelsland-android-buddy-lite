package com.codeisland.buddylite.ble

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.le.AdvertiseSettings
import java.util.UUID

object BleProtocol {
    val serviceUuid: UUID = UUID.fromString("0000beef-0000-1000-8000-00805f9b34fb")
    val writeCharacteristicUuid: UUID = UUID.fromString("0000beef-0001-1000-8000-00805f9b34fb")
    val notifyCharacteristicUuid: UUID = UUID.fromString("0000beef-0002-1000-8000-00805f9b34fb")
    val cccDescriptorUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val advertisedName = "Buddy"

    const val maxToolNameBytes = 17
    const val maxWorkspaceNameBytes = 18
    const val maxMessagePreviewBytes = 16

    const val brightnessFrameMarker = 0xFE
    const val orientationFrameMarker = 0xFD
    const val workspaceFrameMarker = 0xFC
    const val messagePreviewFrameMarker = 0xFB

    const val approveCurrentPermissionMarker = 0xF0
    const val denyCurrentPermissionMarker = 0xF1
    const val skipCurrentQuestionMarker = 0xF2

    const val minBrightnessPercent = 10
    const val maxBrightnessPercent = 100
    const val defaultBrightnessPercent = 70

    const val firmwareInactivityTimeoutMs = 60_000L
    const val demoCycleMs = 8_000L

    val cccEnableNotificationValue: ByteArray = byteArrayOf(0x01, 0x00)
    val cccEnableIndicationValue: ByteArray = byteArrayOf(0x02, 0x00)
    val cccDisableValue: ByteArray = byteArrayOf(0x00, 0x00)

    fun createGattService(): BluetoothGattService {
        val service = BluetoothGattService(
            serviceUuid,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        val writeCharacteristic = BluetoothGattCharacteristic(
            writeCharacteristicUuid,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        val notifyCharacteristic = BluetoothGattCharacteristic(
            notifyCharacteristicUuid,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                BluetoothGattCharacteristic.PROPERTY_INDICATE,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        notifyCharacteristic.addDescriptor(
            BluetoothGattDescriptor(
                cccDescriptorUuid,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            )
        )

        service.addCharacteristic(writeCharacteristic)
        service.addCharacteristic(notifyCharacteristic)
        return service
    }

    fun createAdvertiseSettings(): AdvertiseSettings {
        return AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()
    }

    fun hostUplinkDeliveryModeForDescriptorValue(value: ByteArray?): HostUplinkDeliveryMode? {
        return when {
            value == null || value.contentEquals(cccDisableValue) -> null
            value.contentEquals(cccEnableNotificationValue) -> HostUplinkDeliveryMode.NOTIFICATION
            value.contentEquals(cccEnableIndicationValue) -> HostUplinkDeliveryMode.INDICATION
            else -> null
        }
    }

    fun cccDescriptorValueFor(mode: HostUplinkDeliveryMode?): ByteArray {
        return when (mode) {
            HostUplinkDeliveryMode.NOTIFICATION -> cccEnableNotificationValue.copyOf()
            HostUplinkDeliveryMode.INDICATION -> cccEnableIndicationValue.copyOf()
            null -> cccDisableValue.copyOf()
        }
    }
}

enum class HostUplinkDeliveryMode {
    NOTIFICATION,
    INDICATION,
}
