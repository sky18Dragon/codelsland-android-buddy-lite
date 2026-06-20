package com.codeisland.buddylite.protocol

import kotlinx.serialization.Serializable

@Serializable
data class CompanionBluetoothSummary(
    val version: Int = 1,
    val sequence: Long = 0,
    val sessionId: String? = null,
    val source: String = "",
    val status: String = "idle",
    val toolName: String? = null,
    val workspaceName: String? = null,
    val message: String? = null,
    val pendingAction: String? = null,
    val questionHeader: String? = null,
    val questionText: String? = null,
    val sessions: List<SessionSummary>? = null,
    val updatedAt: String = ""
)

@Serializable
data class SessionSummary(
    val sessionId: String? = null,
    val source: String,
    val status: String,
    val toolName: String? = null,
    val workspaceName: String? = null,
    val message: String? = null,
    val updatedAt: String = ""
)
