package com.codeisland.buddylite.ble

import android.bluetooth.BluetoothGatt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

sealed interface GattOperation {
    data object DiscoverServices : GattOperation
    data class RequestMtu(val mtu: Int) : GattOperation
    data class SetCharacteristicNotification(
        val characteristic: java.util.UUID,
        val enable: Boolean
    ) : GattOperation
    data class WriteDescriptor(
        val characteristic: java.util.UUID,
        val descriptor: java.util.UUID,
        val value: ByteArray
    ) : GattOperation
}

sealed interface GattResult {
    data object Success : GattResult
    data class Failure(val message: String) : GattResult
}

class GattOperationQueue(
    private val operationTimeoutMs: Long = 5_000L
) {
    private val mutex = Mutex()
    private val operationChannel = Channel<Pair<GattOperation, kotlinx.coroutines.CompletableDeferred<GattResult>>>(
        capacity = Channel.UNLIMITED
    )

    private var gatt: BluetoothGatt? = null
    private var pendingResult: kotlinx.coroutines.CompletableDeferred<GattResult>? = null

    fun bind(gatt: BluetoothGatt) {
        this.gatt = gatt
    }

    fun unbind() {
        pendingResult?.complete(GattResult.Failure("GATT unbound"))
        pendingResult = null
        gatt = null
    }

    suspend fun execute(operation: GattOperation): GattResult = mutex.withLock {
        val gatt = this.gatt ?: return GattResult.Failure("GATT not bound")

        when (operation) {
            is GattOperation.SetCharacteristicNotification -> {
                // Synchronous on Android — no callback
                val service = gatt.services.firstOrNull { s ->
                    s.characteristics.any { it.uuid == operation.characteristic }
                } ?: return GattResult.Failure("Service not found")
                val characteristic = service.characteristics.firstOrNull { it.uuid == operation.characteristic }
                    ?: return GattResult.Failure("Characteristic not found")
                val ok = gatt.setCharacteristicNotification(characteristic, operation.enable)
                return if (ok) GattResult.Success
                else GattResult.Failure("setCharacteristicNotification returned false")
            }
            else -> {
                // Asynchronous — wait for callback
                val deferred = kotlinx.coroutines.CompletableDeferred<GattResult>()
                pendingResult = deferred

                val success = when (operation) {
                    is GattOperation.DiscoverServices -> gatt.discoverServices()
                    is GattOperation.RequestMtu -> gatt.requestMtu(operation.mtu)
                    is GattOperation.WriteDescriptor -> {
                        val service = gatt.services.firstOrNull { s ->
                            s.characteristics.any { it.uuid == operation.characteristic }
                        } ?: return GattResult.Failure("Service not found")
                        val characteristic = service.characteristics.firstOrNull { it.uuid == operation.characteristic }
                            ?: return GattResult.Failure("Characteristic not found")
                        val descriptor = characteristic.descriptors.firstOrNull { it.uuid == operation.descriptor }
                            ?: return GattResult.Failure("Descriptor not found")
                        descriptor.value = operation.value
                        gatt.writeDescriptor(descriptor)
                    }
                    // handled above
                    is GattOperation.SetCharacteristicNotification -> false
                }

                if (!success) {
                    pendingResult = null
                    return GattResult.Failure("GATT operation returned false: $operation")
                }

                val result = withTimeoutOrNull(operationTimeoutMs) { deferred.await() }
                    ?: GattResult.Failure("Operation timeout: $operation")
                pendingResult = null
                result
            }
        }
    }

    fun onGattCallbackSuccess() {
        pendingResult?.complete(GattResult.Success)
        pendingResult = null
    }

    fun onGattCallbackFailure(status: Int) {
        pendingResult?.complete(GattResult.Failure("GATT status: $status"))
        pendingResult = null
    }
}
