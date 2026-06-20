package com.codeisland.buddylite.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CiChunkParserTest {

    @Test
    fun `P1 - returns null for data smaller than 15 bytes`() {
        for (size in 0..14) {
            val data = ByteArray(size) { it.toByte() }
            assertNull("Should reject ${size}-byte array", CiChunkParser.parse(data))
        }
    }

    @Test
    fun `P2 - rejects invalid magic bytes`() {
        val valid = makeChunk(sequence = 1, index = 0, total = 1, body = byteArrayOf(1))
        // Only bytes 0-2 matter for magic
        for (pos in 0..2) {
            for (bad in 0..255) {
                if (bad == valid[pos].toInt() and 0xFF) continue
                val mutated = valid.clone()
                mutated[pos] = bad.toByte()
                assertNull("Should reject magic byte $pos = 0x${bad.toString(16)}", CiChunkParser.parse(mutated))
            }
        }
    }

    @Test
    fun `P3 - rejects total outside range 1-64`() {
        assertNull(CiChunkParser.parse(makeChunk(sequence = 1, index = 0, total = 0, body = byteArrayOf(1))))
        assertNull(CiChunkParser.parse(makeChunk(sequence = 1, index = 0, total = 65, body = byteArrayOf(1))))
        assertNull(CiChunkParser.parse(makeChunk(sequence = 1, index = 0, total = 255, body = byteArrayOf(1))))
    }

    @Test
    fun `P4 - rejects index greater than or equal to total`() {
        assertNull(CiChunkParser.parse(makeChunk(sequence = 1, index = 3, total = 3, body = byteArrayOf(1))))
        assertNull(CiChunkParser.parse(makeChunk(sequence = 1, index = 5, total = 3, body = byteArrayOf(1))))
    }

    @Test
    fun `accepts valid chunk`() {
        val body = "hello world".toByteArray()
        val chunk = CiChunkParser.parse(makeChunk(sequence = 42, index = 1, total = 3, body = body))
        assertNotNull(chunk)
        assertEquals(42L, chunk!!.sequence)
        assertEquals(1, chunk.index)
        assertEquals(3, chunk.total)
        assertArrayEquals(body, chunk.body)
    }

    @Test
    fun `handles index 0 correctly`() {
        val chunk = CiChunkParser.parse(makeChunk(sequence = 0, index = 0, total = 1, body = byteArrayOf(0x42)))
        assertNotNull(chunk)
        assertEquals(0, chunk!!.index)
    }

    @Test
    fun `parses maximum valid total`() {
        val chunk = CiChunkParser.parse(makeChunk(sequence = 100, index = 63, total = 64, body = byteArrayOf(0x42)))
        assertNotNull(chunk)
        assertEquals(64, chunk!!.total)
    }

    @Test
    fun `parses large sequence number`() {
        val largeSeq = 0xFFFFFFFFFFFFFFFFL.toLong() // -1 as signed, but raw bytes are all 0xFF
        val data = makeChunkRaw(largeSeq, 0, 1, byteArrayOf(0x01))
        val chunk = CiChunkParser.parse(data)
        assertNotNull(chunk)
        assertEquals(largeSeq, chunk!!.sequence)
    }

    private fun makeChunk(sequence: Long, index: Int, total: Int, body: ByteArray): ByteArray {
        return makeChunkRaw(sequence, index, total, body)
    }

    companion object {
        fun makeChunkRaw(sequence: Long, index: Int, total: Int, body: ByteArray): ByteArray {
            val data = ByteArray(15 + body.size)
            data[0] = 0x43.toByte()
            data[1] = 0x49.toByte()
            data[2] = 0x01.toByte()
            // UInt64 Big-Endian
            for (i in 0..7) {
                data[3 + i] = ((sequence shr (56 - i * 8)) and 0xFF).toByte()
            }
            // UInt16 index Big-Endian
            data[11] = ((index shr 8) and 0xFF).toByte()
            data[12] = (index and 0xFF).toByte()
            // UInt16 total Big-Endian
            data[13] = ((total shr 8) and 0xFF).toByte()
            data[14] = (total and 0xFF).toByte()
            // Body
            System.arraycopy(body, 0, data, 15, body.size)
            return data
        }
    }
}
