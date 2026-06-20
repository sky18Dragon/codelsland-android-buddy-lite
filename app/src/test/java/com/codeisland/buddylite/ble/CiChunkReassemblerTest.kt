package com.codeisland.buddylite.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CiChunkReassemblerTest {

    @Test
    fun `P5 - delivers body regardless of chunk arrival order`() {
        val original = "The quick brown fox jumps over the lazy dog".toByteArray()
        val chunks = splitIntoChunks(sequence = 1, body = original, chunkSize = 10)
        val shuffled = chunks.toMutableList().apply { shuffle() }

        val reassembler = CiChunkReassembler()
        for (chunk in shuffled) {
            val result = reassembler.accept(chunk)
            if (result is ReassemblyResult.Complete) {
                assertArrayEquals(original, result.body)
                return
            }
            assertTrue(result is ReassemblyResult.Pending || result is ReassemblyResult.RejectedDuplicate)
        }
    }

    @Test
    fun `P6 - abandons old sequence when newer arrives`() {
        val reassembler = CiChunkReassembler()
        val oldChunk0 = makeChunk(sequence = 1, index = 0, total = 2, body = byteArrayOf(0x01))
        val newChunk0 = makeChunk(sequence = 2, index = 0, total = 1, body = byteArrayOf(0x02, 0x03))

        assertEquals(ReassemblyResult.Pending, reassembler.accept(oldChunk0))
        val result = reassembler.accept(newChunk0)
        assertTrue(result is ReassemblyResult.Complete)
        assertArrayEquals(byteArrayOf(0x02, 0x03), (result as ReassemblyResult.Complete).body)
    }

    @Test
    fun `P7 - rejects inconsistent total for same sequence`() {
        val reassembler = CiChunkReassembler()
        reassembler.accept(makeChunk(sequence = 1, index = 0, total = 2, body = byteArrayOf(0x01)))
        val result = reassembler.accept(makeChunk(sequence = 1, index = 1, total = 3, body = byteArrayOf(0x02)))
        assertTrue(result is ReassemblyResult.RejectedCorrupt)
    }

    @Test
    fun `rejects duplicate chunk index with different body`() {
        val reassembler = CiChunkReassembler()
        reassembler.accept(makeChunk(sequence = 1, index = 0, total = 2, body = byteArrayOf(0x01)))
        val result = reassembler.accept(makeChunk(sequence = 1, index = 0, total = 2, body = byteArrayOf(0x02)))
        assertTrue(result is ReassemblyResult.RejectedCorrupt)
    }

    @Test
    fun `ignores duplicate chunk with same body`() {
        val reassembler = CiChunkReassembler()
        reassembler.accept(makeChunk(sequence = 1, index = 0, total = 2, body = byteArrayOf(0x01)))
        val result = reassembler.accept(makeChunk(sequence = 1, index = 0, total = 2, body = byteArrayOf(0x01)))
        assertTrue(result is ReassemblyResult.RejectedDuplicate)
    }

    @Test
    fun `rejects older sequence after delivery`() {
        val reassembler = CiChunkReassembler()
        // Complete sequence 2
        val result2 = reassembler.accept(makeChunk(sequence = 2, index = 0, total = 1, body = byteArrayOf(0x42)))
        assertTrue(result2 is ReassemblyResult.Complete)
        // Old sequence 1 should be rejected
        val result1 = reassembler.accept(makeChunk(sequence = 1, index = 0, total = 1, body = byteArrayOf(0x01)))
        assertTrue(result1 is ReassemblyResult.RejectedStale)
    }

    @Test
    fun `completes with single chunk`() {
        val reassembler = CiChunkReassembler()
        val result = reassembler.accept(makeChunk(sequence = 1, index = 0, total = 1, body = byteArrayOf(0x01, 0x02)))
        assertTrue(result is ReassemblyResult.Complete)
        assertArrayEquals(byteArrayOf(0x01, 0x02), (result as ReassemblyResult.Complete).body)
    }

    @Test
    fun `assembles body from ordered chunks`() {
        val reassembler = CiChunkReassembler()
        val originalBody = (0..255).map { it.toByte() }.toByteArray()
        val chunks = splitIntoChunks(sequence = 1, body = originalBody, chunkSize = 50)

        var completeBody: ByteArray? = null
        for (chunk in chunks) {
            when (val result = reassembler.accept(chunk)) {
                is ReassemblyResult.Complete -> completeBody = result.body
                is ReassemblyResult.Pending -> {}
                else -> throw AssertionError("Unexpected result: $result")
            }
        }
        assertArrayEquals(originalBody, completeBody)
    }

    private fun makeChunk(sequence: Long, index: Int, total: Int, body: ByteArray): CiChunk {
        return CiChunk(sequence, index, total, body)
    }

    private fun splitIntoChunks(sequence: Long, body: ByteArray, chunkSize: Int): List<CiChunk> {
        val total = (body.size + chunkSize - 1) / chunkSize
        return (0 until total).map { index ->
            val start = index * chunkSize
            val end = minOf(start + chunkSize, body.size)
            CiChunk(sequence, index, total, body.copyOfRange(start, end))
        }
    }
}
