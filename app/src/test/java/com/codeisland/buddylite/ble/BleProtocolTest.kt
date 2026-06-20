package com.codeisland.buddylite.ble

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.le.AdvertiseSettings
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class BleProtocolTest {

    @Test
    fun uuidsMatchBuddyProtocol() {
        assertEquals(UUID.fromString("0000beef-0000-1000-8000-00805f9b34fb"), BleProtocol.serviceUuid)
        assertEquals(UUID.fromString("0000beef-0001-1000-8000-00805f9b34fb"), BleProtocol.writeCharacteristicUuid)
        assertEquals(UUID.fromString("0000beef-0002-1000-8000-00805f9b34fb"), BleProtocol.notifyCharacteristicUuid)
    }

    @Test
    fun cccDescriptorValuesMatchProtocolBytes() {
        assertArrayEquals(byteArrayOf(0x01, 0x00), BleProtocol.cccEnableNotificationValue)
        assertArrayEquals(byteArrayOf(0x02, 0x00), BleProtocol.cccEnableIndicationValue)
        assertArrayEquals(byteArrayOf(0x00, 0x00), BleProtocol.cccDisableValue)
    }

    @Test
    fun hostUplinkDeliveryModeForDescriptorValueMapsKnownValues() {
        assertEquals(
            HostUplinkDeliveryMode.NOTIFICATION,
            BleProtocol.hostUplinkDeliveryModeForDescriptorValue(byteArrayOf(0x01, 0x00))
        )
        assertEquals(
            HostUplinkDeliveryMode.INDICATION,
            BleProtocol.hostUplinkDeliveryModeForDescriptorValue(byteArrayOf(0x02, 0x00))
        )
        assertNull(BleProtocol.hostUplinkDeliveryModeForDescriptorValue(byteArrayOf(0x00, 0x00)))
        assertNull(BleProtocol.hostUplinkDeliveryModeForDescriptorValue(null))
        assertNull(BleProtocol.hostUplinkDeliveryModeForDescriptorValue(byteArrayOf(0x03, 0x00)))
    }

    @Test
    fun cccDescriptorValueForMapsDeliveryModesToProtocolBytes() {
        assertArrayEquals(
            byteArrayOf(0x01, 0x00),
            BleProtocol.cccDescriptorValueFor(HostUplinkDeliveryMode.NOTIFICATION)
        )
        assertArrayEquals(
            byteArrayOf(0x02, 0x00),
            BleProtocol.cccDescriptorValueFor(HostUplinkDeliveryMode.INDICATION)
        )
        assertArrayEquals(
            byteArrayOf(0x00, 0x00),
            BleProtocol.cccDescriptorValueFor(null)
        )
    }

    @Test
    fun createGattServiceBuildsBuddyService() {
        val service = BleProtocol.createGattService()

        assertEquals(BleProtocol.serviceUuid, service.uuid)
        assertEquals(BluetoothGattService.SERVICE_TYPE_PRIMARY, service.type)
        assertEquals(2, service.characteristics.size)

        val writeCharacteristic = service.getCharacteristic(BleProtocol.writeCharacteristicUuid)
        val notifyCharacteristic = service.getCharacteristic(BleProtocol.notifyCharacteristicUuid)

        assertNotNull(writeCharacteristic)
        assertNotNull(notifyCharacteristic)
        assertTrue(
            writeCharacteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        )
        assertTrue(
            writeCharacteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        )
        assertEquals(BluetoothGattCharacteristic.PERMISSION_WRITE, writeCharacteristic.permissions)
        assertTrue(
            notifyCharacteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
        )
        assertTrue(
            notifyCharacteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
        )

        val cccDescriptor = notifyCharacteristic.getDescriptor(BleProtocol.cccDescriptorUuid)
        assertNotNull(cccDescriptor)
        assertEquals(
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
            cccDescriptor.permissions
        )
    }

    @Test
    fun createAdvertiseSettingsBuildsConnectableLowLatencySettings() {
        val settings = BleProtocol.createAdvertiseSettings()

        assertTrue(settings.isConnectable)
        assertEquals(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY, settings.mode)
        assertEquals(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM, settings.txPowerLevel)
    }
}
