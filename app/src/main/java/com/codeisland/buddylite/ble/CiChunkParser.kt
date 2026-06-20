package com.codeisland.buddylite.ble

import java.nio.ByteBuffer

data class CiChunk(
    val sequence: Long,
    val index: Int,
    val total: Int,
    val body: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CiChunk) return false
        return sequence == other.sequence &&
            index == other.index &&
            total == other.total &&
            body.contentEquals(other.body)
    }

    override fun hashCode(): Int {
        var result = sequence.hashCode()
        result = 31 * result + index
        result = 31 * result + total
        result = 31 * result + body.contentHashCode()
        return result
    }
}

object CiChunkParser {

    private const val MIN_SIZE = 15
    private const val MAGIC_0: Byte = 0x43
    private const val MAGIC_1: Byte = 0x49
    private const val MAGIC_2: Byte = 0x01
    private const val MAX_TOTAL = 64
    private const val MAX_CHUNK_BODY_BYTES = 120

    fun parse(data: ByteArray): CiChunk? {
        if (data.size < MIN_SIZE) return null
        if (data[0] != MAGIC_0 || data[1] != MAGIC_1 || data[2] != MAGIC_2) return null

        val bodySize = data.size - MIN_SIZE
        if (bodySize > MAX_CHUNK_BODY_BYTES) return null

        val sequence = ByteBuffer.wrap(data, 3, 8).long
        val index = ((data[11].toInt() and 0xFF) shl 8) or (data[12].toInt() and 0xFF)
        val total = ((data[13].toInt() and 0xFF) shl 8) or (data[14].toInt() and 0xFF)

        if (total !in 1..MAX_TOTAL) return null
        if (index < 0 || index >= total) return null

        val body = data.copyOfRange(15, data.size)
        return CiChunk(sequence, index, total, body)
    }
}
