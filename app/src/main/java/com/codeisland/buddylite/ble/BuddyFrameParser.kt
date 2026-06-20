package com.codeisland.buddylite.ble

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

sealed interface IncomingCommand {
    data class AgentFrame(
        val mascotId: Int,
        val statusId: Int,
        val toolName: String?,
    ) : IncomingCommand

    data class WorkspaceFrame(val workspaceName: String?) : IncomingCommand

    data class MessagePreviewFrame(
        val index: Int,
        val total: Int,
        val isUser: Boolean,
        val text: String?,
    ) : IncomingCommand

    data class Brightness(val percent: Int) : IncomingCommand
    data class Orientation(val wireValue: Int) : IncomingCommand
}

object BuddyFrameParser {
    fun parse(payload: ByteArray): IncomingCommand? {
        if (payload.isEmpty()) return null

        val marker = payload.unsignedByteAt(0)
        if (marker == BleProtocol.brightnessFrameMarker && payload.size >= 2) {
            return IncomingCommand.Brightness(
                percent = payload.unsignedByteAt(1)
                    .coerceIn(BleProtocol.minBrightnessPercent, BleProtocol.maxBrightnessPercent)
            )
        }
        if (marker == BleProtocol.brightnessFrameMarker) return null

        if (marker == BleProtocol.orientationFrameMarker && payload.size >= 2) {
            return IncomingCommand.Orientation(
                wireValue = if (payload.unsignedByteAt(1) == 1) 1 else 0
            )
        }
        if (marker == BleProtocol.orientationFrameMarker) return null

        if (marker == BleProtocol.workspaceFrameMarker && payload.size >= 2) {
            val declaredLength = payload.unsignedByteAt(1)
            val availableLength = (payload.size - WORKSPACE_HEADER_BYTES).coerceAtLeast(0)
            val workspaceLength = minOf(
                declaredLength,
                availableLength,
                BleProtocol.maxWorkspaceNameBytes
            )
            return IncomingCommand.WorkspaceFrame(
                workspaceName = decodeString(
                    payload = payload,
                    offset = WORKSPACE_HEADER_BYTES,
                    length = workspaceLength
                )
            )
        }
        if (marker == BleProtocol.workspaceFrameMarker) return null

        if (marker == BleProtocol.messagePreviewFrameMarker && payload.size >= 4) {
            val flagLength = payload.unsignedByteAt(3)
            val declaredLength = flagLength and TEXT_LENGTH_MASK
            val availableLength = (payload.size - MESSAGE_PREVIEW_HEADER_BYTES).coerceAtLeast(0)
            val textLength = minOf(
                declaredLength,
                availableLength,
                BleProtocol.maxMessagePreviewBytes
            )
            return IncomingCommand.MessagePreviewFrame(
                index = payload.unsignedByteAt(1),
                total = payload.unsignedByteAt(2),
                isUser = (flagLength and USER_MESSAGE_FLAG) != 0,
                text = decodeString(
                    payload = payload,
                    offset = MESSAGE_PREVIEW_HEADER_BYTES,
                    length = textLength
                )
            )
        }
        if (marker == BleProtocol.messagePreviewFrameMarker) return null

        if (payload.size < AGENT_HEADER_BYTES) return null

        val declaredToolLength = payload.unsignedByteAt(2)
        val availableLength = (payload.size - AGENT_HEADER_BYTES).coerceAtLeast(0)
        val toolLength = minOf(
            declaredToolLength,
            availableLength,
            BleProtocol.maxToolNameBytes
        )

        return IncomingCommand.AgentFrame(
            mascotId = payload.unsignedByteAt(0),
            statusId = payload.unsignedByteAt(1),
            toolName = decodeString(
                payload = payload,
                offset = AGENT_HEADER_BYTES,
                length = toolLength
            )
        )
    }

    private fun decodeString(payload: ByteArray, offset: Int, length: Int): String? {
        if (length <= 0) return null
        if (offset < 0 || offset + length > payload.size) return null

        return try {
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(payload, offset, length))
                .toString()
                .trim()
                .ifBlank { null }
        } catch (_: CharacterCodingException) {
            null
        }
    }

    private fun ByteArray.unsignedByteAt(index: Int): Int {
        return this[index].toInt() and 0xFF
    }

    private const val AGENT_HEADER_BYTES = 3
    private const val WORKSPACE_HEADER_BYTES = 2
    private const val MESSAGE_PREVIEW_HEADER_BYTES = 4
    private const val USER_MESSAGE_FLAG = 0x80
    private const val TEXT_LENGTH_MASK = 0x7F
}
