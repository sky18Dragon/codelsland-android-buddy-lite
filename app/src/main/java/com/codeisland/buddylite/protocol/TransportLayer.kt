package com.codeisland.buddylite.protocol

sealed interface TransportEvent {
    data object Started : TransportEvent
    data class Discovered(val name: String, val address: String) : TransportEvent
    data object Connecting : TransportEvent
    data class Connected(val name: String) : TransportEvent
    data class MtuNegotiated(val mtu: Int) : TransportEvent
    data object Subscribed : TransportEvent
    data class SummaryReceived(val jsonBytes: ByteArray) : TransportEvent
    data object Stale : TransportEvent
    data object Disconnected : TransportEvent
    data class Error(val message: String, val cause: Throwable? = null) : TransportEvent
    data object BluetoothOff : TransportEvent
    data object BluetoothOn : TransportEvent
    data class PermissionDenied(val permissions: List<String>) : TransportEvent
}

interface TransportLayer {
    val events: kotlinx.coroutines.flow.Flow<TransportEvent>
    fun start()
    fun stop()
    val isRunning: Boolean
}
