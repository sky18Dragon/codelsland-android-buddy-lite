package com.codeisland.buddylite.ble

import com.codeisland.buddylite.data.model.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleStateMachineTest {

    @Test
    fun `P12 - valid transitions succeed`() {
        val sm = BleStateMachine()
        assertEquals(ConnectionState.IDLE, sm.currentState)

        val path = listOf(
            ConnectionState.SCANNING,
            ConnectionState.CONNECTING,
            ConnectionState.DISCOVERING_SERVICES,
            ConnectionState.REQUESTING_MTU,
            ConnectionState.SUBSCRIBING,
            ConnectionState.CONNECTED
        )
        for (state in path) {
            assertTrue("Transition to $state should succeed", sm.transition(state))
        }
    }

    @Test
    fun `P12 - invalid transitions rejected`() {
        val sm = BleStateMachine()

        // IDLE cannot jump directly to CONNECTED
        assertFalse(sm.transition(ConnectionState.CONNECTED))

        // SCANNING cannot jump directly to CONNECTED (must go through CONNECTING)
        sm.forceState(ConnectionState.SCANNING)
        assertFalse(sm.transition(ConnectionState.CONNECTED))

        // CONNECTED cannot go back to SCANNING (must disconnect first)
        sm.forceState(ConnectionState.CONNECTED)
        assertFalse(sm.transition(ConnectionState.SCANNING))
    }

    @Test
    fun `connected to stale to disconnected flow`() {
        val sm = BleStateMachine()
        sm.forceState(ConnectionState.CONNECTED)

        assertTrue(sm.transition(ConnectionState.STALE))
        assertEquals(ConnectionState.STALE, sm.currentState)

        assertTrue(sm.transition(ConnectionState.DISCONNECTED))
        assertEquals(ConnectionState.DISCONNECTED, sm.currentState)
    }

    @Test
    fun `stale can return to connected`() {
        val sm = BleStateMachine()
        sm.forceState(ConnectionState.STALE)
        assertTrue(sm.transition(ConnectionState.CONNECTED))
    }

    @Test
    fun `disconnected can go to idle or scanning`() {
        val sm = BleStateMachine()
        sm.forceState(ConnectionState.DISCONNECTED)

        assertTrue(sm.transition(ConnectionState.IDLE))
        sm.forceState(ConnectionState.DISCONNECTED)
        assertTrue(sm.transition(ConnectionState.SCANNING))
    }

    @Test
    fun `forceState bypasses validation`() {
        val sm = BleStateMachine()
        sm.forceState(ConnectionState.CONNECTED)
        assertEquals(ConnectionState.CONNECTED, sm.currentState)
        // But transition still validates
        assertFalse(sm.transition(ConnectionState.DISCOVERING_SERVICES))
    }

    @Test
    fun `isConnected returns true only for CONNECTED`() {
        val sm = BleStateMachine()
        sm.forceState(ConnectionState.CONNECTED)
        assertTrue(sm.isConnected())

        sm.forceState(ConnectionState.STALE)
        assertFalse(sm.isConnected())
    }

    @Test
    fun `isTransitional returns true for intermediate states`() {
        val sm = BleStateMachine()
        for (state in ConnectionState.entries) {
            sm.forceState(state)
            assertEquals(state.isTransitional, sm.isTransitional())
        }
    }
}
