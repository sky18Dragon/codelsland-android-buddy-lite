package com.codeisland.buddylite.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BuddyDashboardState(
    // Legacy UI compatibility while the app migrates to the Buddy peripheral model.
    val connectionState: ConnectionState = ConnectionState.IDLE,
    val isStale: Boolean = false,
    val source: String = "",
    val status: CompanionStatus = CompanionStatus.IDLE,
    val sessions: List<SessionCard> = emptyList(),
    val lastDeliveredSequence: Long = 0L,

    // Peripheral
    val peripheralState: PeripheralState = PeripheralState.STARTING,
    val displayMode: DisplayMode = DisplayMode.STANDBY,

    // Connection
    val connectedDeviceName: String? = null,
    val macSubscribed: Boolean = false,
    val lastSyncEpochMs: Long = 0L,

    // Agent
    val mascot: Mascot = Mascot.CODEX,
    val agentStatus: AgentStatusCode = AgentStatusCode.IDLE,
    val toolName: String? = null,
    val workspaceName: String? = null,

    // Messages
    val messages: List<MessagePreview> = emptyList(),
    val latestMessage: String? = null,

    // Pending action (derived from agentStatus)
    val pendingAction: PendingAction? = null,
    val questionHeader: String? = null,
    val questionText: String? = null,

    // Capability flags (now dynamic based on Mac subscription)
    val canApprove: Boolean = false,
    val canDeny: Boolean = false,
    val canSkip: Boolean = false,
    val canAnswerOnPhone: Boolean = false,
    val actionHint: String = "请回到 Mac 处理",

    // Hardware
    val brightnessPercent: Int = 70,
    val orientation: ScreenOrientation = ScreenOrientation.UP,

    // Demo
    val isDemoMode: Boolean = false,

    // Permissions & diagnostics
    val permissionState: PermissionState = PermissionState.UNKNOWN,
    val diagnosticMessage: String? = null,

    // Local version for widget/dedupe
    val localStateVersion: Long = 0L
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
