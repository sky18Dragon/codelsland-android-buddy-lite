package com.codeisland.buddylite.protocol

import com.codeisland.buddylite.data.model.CompanionStatus
import com.codeisland.buddylite.data.model.PendingAction
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolMapperTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `P9 - unknown status maps to IDLE without crash`() {
        val raw = buildJsonStatus("unknownFutureStatus")
        val summary = json.decodeFromString<CompanionBluetoothSummary>(raw)
        val state = ProtocolMapper.mapToDomainState(summary)
        assertEquals(CompanionStatus.IDLE, state.status)
    }

    @Test
    fun `P10 - all capability flags set to false`() {
        val raw = buildValidJson()
        val summary = json.decodeFromString<CompanionBluetoothSummary>(raw)
        val state = ProtocolMapper.mapToDomainState(summary)

        assertFalse(state.canApprove)
        assertFalse(state.canDeny)
        assertFalse(state.canSkip)
        assertFalse(state.canAnswerOnPhone)
        assertEquals("当前 Mac 端未提供安卓可用的命令回传通道，请回到 Mac 处理", state.actionHint)
    }

    @Test
    fun `P11 - null sessions list maps to empty`() {
        val raw = """
        {
            "version": 1, "sequence": 1, "source": "codex", "status": "idle",
            "updatedAt": "2026-06-20T10:00:00Z"
        }
        """.trimIndent()
        val summary = json.decodeFromString<CompanionBluetoothSummary>(raw)
        val state = ProtocolMapper.mapToDomainState(summary)
        assertTrue(state.sessions.isEmpty())
    }

    @Test
    fun `maps all fields correctly`() {
        val raw = buildValidJson()
        val summary = json.decodeFromString<CompanionBluetoothSummary>(raw)
        val state = ProtocolMapper.mapToDomainState(summary)

        assertEquals("codex", state.source)
        assertEquals(CompanionStatus.WAITING_QUESTION, state.status)
        assertEquals("WebSearch", state.toolName)
        assertEquals("repo/CodeIsland", state.workspaceName)
        assertEquals("正在搜索相关文档...", state.latestMessage)
        assertEquals(PendingAction.QUESTION, state.pendingAction)
        assertEquals("选择搜索范围", state.questionHeader)
        assertEquals("你想搜索哪个目录下的文件？", state.questionText)
        assertEquals(2, state.sessions.size)
    }

    @Test
    fun `maps session cards`() {
        val raw = buildValidJson()
        val summary = json.decodeFromString<CompanionBluetoothSummary>(raw)
        val state = ProtocolMapper.mapToDomainState(summary)

        assertEquals("codex", state.sessions[0].source)
        assertEquals(CompanionStatus.WAITING_QUESTION, state.sessions[0].status)
        assertEquals("claude", state.sessions[1].source)
        assertEquals(CompanionStatus.IDLE, state.sessions[1].status)
    }

    @Test
    fun `parses ISO 8601 timestamp`() {
        val raw = buildValidJson()
        val summary = json.decodeFromString<CompanionBluetoothSummary>(raw)
        val state = ProtocolMapper.mapToDomainState(summary)
        assertTrue(state.lastSyncEpochMs > 0)
    }

    @Test
    fun `handles minimal valid JSON`() {
        val raw = """
        {"version":1,"sequence":0,"source":"claude","status":"processing","updatedAt":"2026-06-20T10:00:00Z"}
        """.trimIndent()
        val summary = json.decodeFromString<CompanionBluetoothSummary>(raw)
        val state = ProtocolMapper.mapToDomainState(summary)
        assertEquals("claude", state.source)
        assertEquals(CompanionStatus.PROCESSING, state.status)
        assertTrue(state.sessions.isEmpty())
    }

    @Test
    fun `maps all status values`() {
        for (status in listOf("idle", "processing", "running", "waitingApproval", "waitingQuestion")) {
            val raw = buildJsonStatus(status)
            val summary = json.decodeFromString<CompanionBluetoothSummary>(raw)
            assertEquals(status.lowercase(), CompanionStatus.fromString(summary.status).name.lowercase())
        }
    }

    @Test
    fun `handles empty sessions array`() {
        val raw = """
        {
            "version": 1, "sequence": 1, "source": "codex", "status": "idle",
            "sessions": [], "updatedAt": "2026-06-20T10:00:00Z"
        }
        """.trimIndent()
        val summary = json.decodeFromString<CompanionBluetoothSummary>(raw)
        assertNotNull(summary.sessions)
        assertTrue(summary.sessions!!.isEmpty())
    }

    private fun buildValidJson(): String = """
    {
        "version": 1,
        "sequence": 42,
        "sessionId": "abc123def456",
        "source": "codex",
        "status": "waitingQuestion",
        "toolName": "WebSearch",
        "workspaceName": "repo/CodeIsland",
        "message": "正在搜索相关文档...",
        "pendingAction": "question",
        "questionHeader": "选择搜索范围",
        "questionText": "你想搜索哪个目录下的文件？",
        "sessions": [
            {
                "sessionId": "abc123",
                "source": "codex",
                "status": "waitingQuestion",
                "toolName": "WebSearch",
                "workspaceName": "repo/CodeIsland",
                "message": "搜索中...",
                "updatedAt": "2026-06-20T10:30:00Z"
            },
            {
                "sessionId": "def456",
                "source": "claude",
                "status": "idle",
                "updatedAt": "2026-06-20T10:25:00Z"
            }
        ],
        "updatedAt": "2026-06-20T10:30:00Z"
    }
    """.trimIndent()

    private fun buildJsonStatus(status: String): String = """
        {"version":1,"sequence":1,"source":"codex","status":"$status","updatedAt":"2026-06-20T10:00:00Z"}
    """.trimIndent()
}
