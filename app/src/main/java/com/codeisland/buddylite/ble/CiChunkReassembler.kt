package com.codeisland.buddylite.ble

import android.os.SystemClock

class CiChunkReassembler(
    private val maxAggregateBytes: Int = 7680,
    private val timeoutMs: Long = 5_000L
) {
    private var currentSequence: Long = -1L
    private var currentTotal: Int = 0
    private val chunks = mutableMapOf<Int, ByteArray>()
    private var lastChunkTimeMs: Long = 0L
    private var currentAggregateBytes: Int = 0
    private var lastCompletedSequence: Long = -1L

    fun accept(chunk: CiChunk): ReassemblyResult {
        // Reject sequences we've already completed
        if (chunk.sequence <= lastCompletedSequence && chunk.sequence != currentSequence) {
            return ReassemblyResult.RejectedStale
        }

        // Newer sequence → abandon current assembly
        if (chunk.sequence > currentSequence) {
            reset(chunk.sequence, chunk.total)
        }

        // Reject chunks from other sequences
        if (chunk.sequence != currentSequence) {
            return ReassemblyResult.RejectedStale
        }

        // Inconsistent total for same sequence → corrupt
        if (chunk.total != currentTotal) {
            reset()
            return ReassemblyResult.RejectedCorrupt
        }

        // Check aggregate size before accepting
        if (currentAggregateBytes + chunk.body.size > maxAggregateBytes) {
            reset()
            return ReassemblyResult.RejectedCorrupt
        }

        // Duplicate index → check content
        chunks[chunk.index]?.let { existing ->
            return if (existing.contentEquals(chunk.body)) {
                ReassemblyResult.RejectedDuplicate
            } else {
                reset()
                ReassemblyResult.RejectedCorrupt
            }
        }

        chunks[chunk.index] = chunk.body
        currentAggregateBytes += chunk.body.size
        lastChunkTimeMs = SystemClock.elapsedRealtime()

        if (chunks.size == currentTotal) {
            val combined = combineBody()
            val completedSeq = currentSequence
            reset()
            lastCompletedSequence = completedSeq
            return if (combined.isEmpty()) ReassemblyResult.RejectedCorrupt
            else ReassemblyResult.Complete(combined)
        }

        return ReassemblyResult.Pending
    }

    fun checkTimeout(): Boolean {
        if (currentSequence < 0 || chunks.isEmpty()) return false
        val elapsed = SystemClock.elapsedRealtime() - lastChunkTimeMs
        if (elapsed > timeoutMs) {
            reset()
            return true
        }
        return false
    }

    fun reset() {
        currentSequence = -1L
        currentTotal = 0
        chunks.clear()
        currentAggregateBytes = 0
        lastChunkTimeMs = 0L
    }

    private fun reset(sequence: Long, total: Int) {
        currentSequence = sequence
        currentTotal = total
        chunks.clear()
        currentAggregateBytes = 0
        lastChunkTimeMs = SystemClock.elapsedRealtime()
    }

    private fun combineBody(): ByteArray {
        val result = ByteArray(currentAggregateBytes)
        var offset = 0
        for (i in 0 until currentTotal) {
            val body = chunks[i] ?: return ByteArray(0)
            System.arraycopy(body, 0, result, offset, body.size)
            offset += body.size
        }
        return result
    }
}

sealed interface ReassemblyResult {
    data object Pending : ReassemblyResult
    data class Complete(val body: ByteArray) : ReassemblyResult
    data object RejectedStale : ReassemblyResult
    data object RejectedCorrupt : ReassemblyResult
    data object RejectedDuplicate : ReassemblyResult
}
