package com.codeisland.buddylite.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class ConnectionState {
    IDLE,
    PERMISSION_BLOCKED,
    SCANNING,
    CONNECTING,
    DISCOVERING_SERVICES,
    REQUESTING_MTU,
    SUBSCRIBING,
    CONNECTED,
    STALE,
    DISCONNECTED;

    val isTransitional: Boolean
        get() = this in setOf(SCANNING, CONNECTING, DISCOVERING_SERVICES, REQUESTING_MTU, SUBSCRIBING)

    val isStable: Boolean
        get() = this in setOf(CONNECTED, STALE, DISCONNECTED)
}
