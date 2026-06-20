package com.codeisland.buddylite.ble

import com.codeisland.buddylite.data.model.ConnectionState

class BleStateMachine {

    @Volatile
    var currentState: ConnectionState = ConnectionState.IDLE
        private set

    private val validTransitions: Map<ConnectionState, Set<ConnectionState>> = mapOf(
        ConnectionState.IDLE to setOf(
            ConnectionState.PERMISSION_BLOCKED,
            ConnectionState.SCANNING
        ),
        ConnectionState.PERMISSION_BLOCKED to setOf(
            ConnectionState.IDLE,
            ConnectionState.SCANNING
        ),
        ConnectionState.SCANNING to setOf(
            ConnectionState.IDLE,
            ConnectionState.PERMISSION_BLOCKED,
            ConnectionState.CONNECTING,
            ConnectionState.DISCONNECTED
        ),
        ConnectionState.CONNECTING to setOf(
            ConnectionState.DISCOVERING_SERVICES,
            ConnectionState.DISCONNECTED,
            ConnectionState.SCANNING
        ),
        ConnectionState.DISCOVERING_SERVICES to setOf(
            ConnectionState.REQUESTING_MTU,
            ConnectionState.DISCONNECTED
        ),
        ConnectionState.REQUESTING_MTU to setOf(
            ConnectionState.SUBSCRIBING,
            ConnectionState.DISCONNECTED
        ),
        ConnectionState.SUBSCRIBING to setOf(
            ConnectionState.CONNECTED,
            ConnectionState.DISCONNECTED
        ),
        ConnectionState.CONNECTED to setOf(
            ConnectionState.STALE,
            ConnectionState.DISCONNECTED
        ),
        ConnectionState.STALE to setOf(
            ConnectionState.CONNECTED,
            ConnectionState.DISCONNECTED
        ),
        ConnectionState.DISCONNECTED to setOf(
            ConnectionState.IDLE,
            ConnectionState.SCANNING,
            ConnectionState.PERMISSION_BLOCKED
        )
    )

    @Synchronized
    fun transition(to: ConnectionState): Boolean {
        val allowed = validTransitions[currentState] ?: return false
        if (to !in allowed) return false
        currentState = to
        return true
    }

    @Synchronized
    fun forceState(state: ConnectionState) {
        currentState = state
    }

    fun isConnected(): Boolean = currentState == ConnectionState.CONNECTED
    fun isTransitional(): Boolean = currentState.isTransitional
}
