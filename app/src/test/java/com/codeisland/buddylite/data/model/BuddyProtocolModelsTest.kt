package com.codeisland.buddylite.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BuddyProtocolModelsTest {

    @Test
    fun mascotFromWireIdReturnsAllValidMascots() {
        val expected = listOf(
            Mascot.CLAUDE,
            Mascot.CODEX,
            Mascot.GEMINI,
            Mascot.CURSOR,
            Mascot.COPILOT,
            Mascot.TRAE,
            Mascot.QODER,
            Mascot.DROID,
            Mascot.CODEBUDDY,
            Mascot.STEPFUN,
            Mascot.OPENCODE,
            Mascot.QWEN,
            Mascot.ANTIGRAVITY,
            Mascot.WORKBUDDY,
            Mascot.HERMES,
            Mascot.KIMI
        )

        expected.forEachIndexed { index, mascot ->
            assertEquals(mascot, Mascot.fromWireId(index))
        }
    }

    @Test
    fun mascotFromWireIdReturnsCodexForUnknownId() {
        assertEquals(Mascot.CODEX, Mascot.fromWireId(0xFF))
        assertEquals(Mascot.CODEX, Mascot.fromWireId(-1))
    }

    @Test
    fun agentStatusCodeFromWireIdReturnsAllValidStatuses() {
        val expected = listOf(
            AgentStatusCode.IDLE,
            AgentStatusCode.PROCESSING,
            AgentStatusCode.RUNNING,
            AgentStatusCode.WAITING_APPROVAL,
            AgentStatusCode.WAITING_QUESTION
        )

        expected.forEachIndexed { index, status ->
            assertEquals(status, AgentStatusCode.fromWireId(index))
        }
    }

    @Test
    fun agentStatusCodeFromWireIdReturnsIdleForUnknownId() {
        assertEquals(AgentStatusCode.IDLE, AgentStatusCode.fromWireId(0xFF))
        assertEquals(AgentStatusCode.IDLE, AgentStatusCode.fromWireId(-1))
    }

    @Test
    fun screenOrientationFromWireValueMapsOnlyOneToDown() {
        assertEquals(ScreenOrientation.UP, ScreenOrientation.fromWireValue(0))
        assertEquals(ScreenOrientation.DOWN, ScreenOrientation.fromWireValue(1))
        assertEquals(ScreenOrientation.UP, ScreenOrientation.fromWireValue(2))
        assertEquals(ScreenOrientation.UP, ScreenOrientation.fromWireValue(-1))
    }
}
