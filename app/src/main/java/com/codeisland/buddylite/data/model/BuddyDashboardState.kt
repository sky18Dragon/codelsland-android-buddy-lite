package com.codeisland.buddylite.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BuddyDashboardState(
    val connectionState: ConnectionState = ConnectionState.IDLE,
    val connectedDeviceName: String? = null,
    val lastSyncEpochMs: Long = 0L,
    val isStale: Boolean = false,

    val source: String = "",
    val status: CompanionStatus = CompanionStatus.IDLE,
    val toolName: String? = null,
    val workspaceName: String? = null,
    val latestMessage: String? = null,

    val pendingAction: PendingAction? = null,
    val questionHeader: String? = null,
    val questionText: String? = null,

    val sessions: List<SessionCard> = emptyList(),

    val canApprove: Boolean = false,
    val canDeny: Boolean = false,
    val canSkip: Boolean = false,
    val canAnswerOnPhone: Boolean = false,
    val actionHint: String = "请回到 Mac 处理",

    val isDemoMode: Boolean = false,

    val permissionState: PermissionState = PermissionState.UNKNOWN,
    val diagnosticMessage: String? = null,

    val lastDeliveredSequence: Long = 0L
)

@Serializable
data class SessionCard(
    val sessionId: String?,
    val source: String,
    val status: CompanionStatus,
    val toolName: String?,
    val workspaceName: String?,
    val message: String?
)

@Serializable
enum class PermissionState {
    UNKNOWN,
    GRANTED,
    PARTIAL,
    DENIED
}
