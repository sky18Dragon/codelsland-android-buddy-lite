package com.codeisland.buddylite.ble

import com.codeisland.buddylite.data.model.AgentStatusCode
import com.codeisland.buddylite.data.model.Mascot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class BuddyFrameParserTest {

    @Test
    fun emptyPayloadReturnsNull() {
        assertNull(BuddyFrameParser.parse(ByteArray(0)))
    }

    @Test
    fun parsesAgentFrame() {
        val payload = agentFrame(
            mascotId = Mascot.CLAUDE.wireId,
            statusId = AgentStatusCode.RUNNING.wireId,
            toolName = "Bash"
        )

        val command = BuddyFrameParser.parse(payload)

        assertTrue(command is IncomingCommand.AgentFrame)
        command as IncomingCommand.AgentFrame
        assertEquals(Mascot.CLAUDE.wireId, command.mascotId)
        assertEquals(AgentStatusCode.RUNNING.wireId, command.statusId)
        assertEquals("Bash", command.toolName)
    }

    @Test
    fun parsesWorkspaceFrame() {
        val name = "repo"
        val bytes = name.toByteArray(StandardCharsets.UTF_8)
        val payload = byteArrayOf(BleProtocol.workspaceFrameMarker.toByte(), bytes.size.toByte()) + bytes

        val command = BuddyFrameParser.parse(payload)

        assertTrue(command is IncomingCommand.WorkspaceFrame)
        assertEquals(name, (command as IncomingCommand.WorkspaceFrame).workspaceName)
    }

    @Test
    fun parsesMessagePreviewFrame() {
        val text = "approve?"
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        val payload = byteArrayOf(
            BleProtocol.messagePreviewFrameMarker.toByte(),
            1,
            3,
            (0x80 or bytes.size).toByte()
        ) + bytes

        val command = BuddyFrameParser.parse(payload)

        assertTrue(command is IncomingCommand.MessagePreviewFrame)
        command as IncomingCommand.MessagePreviewFrame
        assertEquals(1, command.index)
        assertEquals(3, command.total)
        assertEquals(true, command.isUser)
        assertEquals(text, command.text)
    }

    @Test
    fun parsesBrightnessFrame() {
        val command = BuddyFrameParser.parse(
            byteArrayOf(BleProtocol.brightnessFrameMarker.toByte(), 42)
        )

        assertEquals(IncomingCommand.Brightness(42), command)
    }

    @Test
    fun parsesOrientationFrame() {
        assertEquals(
            IncomingCommand.Orientation(1),
            BuddyFrameParser.parse(byteArrayOf(BleProtocol.orientationFrameMarker.toByte(), 1))
        )
        assertEquals(
            IncomingCommand.Orientation(0),
            BuddyFrameParser.parse(byteArrayOf(BleProtocol.orientationFrameMarker.toByte(), 7))
        )
    }

    @Test
    fun unknownMascotIdDefaultsToCodexInModelMapping() {
        val command = BuddyFrameParser.parse(agentFrame(0xFF, AgentStatusCode.IDLE.wireId, "Read"))

        assertTrue(command is IncomingCommand.AgentFrame)
        assertEquals(Mascot.CODEX, Mascot.fromWireId((command as IncomingCommand.AgentFrame).mascotId))
    }

    @Test
    fun unknownStatusIdDefaultsToIdleInModelMapping() {
        val command = BuddyFrameParser.parse(agentFrame(Mascot.CODEX.wireId, 0xFF, "Read"))

        assertTrue(command is IncomingCommand.AgentFrame)
        assertEquals(AgentStatusCode.IDLE, AgentStatusCode.fromWireId((command as IncomingCommand.AgentFrame).statusId))
    }

    @Test
    fun toolNameLongerThanSeventeenBytesIsClamped() {
        val tool = "abcdefghijklmnopqXYZ"
        val command = BuddyFrameParser.parse(agentFrame(Mascot.CODEX.wireId, AgentStatusCode.RUNNING.wireId, tool))

        assertTrue(command is IncomingCommand.AgentFrame)
        assertEquals("abcdefghijklmnopq", (command as IncomingCommand.AgentFrame).toolName)
    }

    @Test
    fun workspaceNameLongerThanEighteenBytesIsClamped() {
        val workspace = "abcdefghijklmnopqrXYZ"
        val bytes = workspace.toByteArray(StandardCharsets.UTF_8)
        val payload = byteArrayOf(BleProtocol.workspaceFrameMarker.toByte(), bytes.size.toByte()) + bytes

        val command = BuddyFrameParser.parse(payload)

        assertTrue(command is IncomingCommand.WorkspaceFrame)
        assertEquals("abcdefghijklmnopqr", (command as IncomingCommand.WorkspaceFrame).workspaceName)
    }

    @Test
    fun messageTextLongerThanSixteenBytesIsClamped() {
        val text = "abcdefghijklmnopXYZ"
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        val payload = byteArrayOf(
            BleProtocol.messagePreviewFrameMarker.toByte(),
            0,
            1,
            bytes.size.toByte()
        ) + bytes

        val command = BuddyFrameParser.parse(payload)

        assertTrue(command is IncomingCommand.MessagePreviewFrame)
        assertEquals("abcdefghijklmnop", (command as IncomingCommand.MessagePreviewFrame).text)
    }

    @Test
    fun brightnessIsClampedToAllowedRange() {
        assertEquals(
            IncomingCommand.Brightness(BleProtocol.minBrightnessPercent),
            BuddyFrameParser.parse(byteArrayOf(BleProtocol.brightnessFrameMarker.toByte(), 0))
        )
        assertEquals(
            IncomingCommand.Brightness(BleProtocol.maxBrightnessPercent),
            BuddyFrameParser.parse(byteArrayOf(BleProtocol.brightnessFrameMarker.toByte(), 0xFF.toByte()))
        )
    }

    @Test
    fun invalidUtf8DecodesToNullString() {
        val payload = byteArrayOf(
            Mascot.CODEX.wireId.toByte(),
            AgentStatusCode.RUNNING.wireId.toByte(),
            2,
            0xC3.toByte(),
            0x28
        )

        val command = BuddyFrameParser.parse(payload)

        assertTrue(command is IncomingCommand.AgentFrame)
        assertNull((command as IncomingCommand.AgentFrame).toolName)
    }

    @Test
    fun malformedFramesReturnNull() {
        assertNull(BuddyFrameParser.parse(byteArrayOf(1)))
        assertNull(BuddyFrameParser.parse(byteArrayOf(1, 2)))
        assertNull(BuddyFrameParser.parse(byteArrayOf(BleProtocol.brightnessFrameMarker.toByte())))
        assertNull(BuddyFrameParser.parse(byteArrayOf(BleProtocol.orientationFrameMarker.toByte())))
        assertNull(BuddyFrameParser.parse(byteArrayOf(BleProtocol.workspaceFrameMarker.toByte())))
        assertNull(BuddyFrameParser.parse(byteArrayOf(BleProtocol.messagePreviewFrameMarker.toByte(), 0, 1)))
    }

    private fun agentFrame(mascotId: Int, statusId: Int, toolName: String): ByteArray {
        val bytes = toolName.toByteArray(StandardCharsets.UTF_8)
        return byteArrayOf(mascotId.toByte(), statusId.toByte(), bytes.size.toByte()) + bytes
    }
}
