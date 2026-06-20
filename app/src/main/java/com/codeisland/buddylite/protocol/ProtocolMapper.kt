package com.codeisland.buddylite.protocol

import com.codeisland.buddylite.data.model.BuddyDashboardState
import com.codeisland.buddylite.data.model.CompanionStatus
import com.codeisland.buddylite.data.model.PendingAction
import com.codeisland.buddylite.data.model.SessionCard
import java.time.Instant

object ProtocolMapper {

    fun mapToDomainState(
        summary: CompanionBluetoothSummary,
        previousState: BuddyDashboardState = BuddyDashboardState()
    ): BuddyDashboardState {
        return BuddyDashboardState(
            connectionState = previousState.connectionState,
            connectedDeviceName = previousState.connectedDeviceName,
            lastSyncEpochMs = parseInstant(summary.updatedAt)?.toEpochMilli()
                ?: previousState.lastSyncEpochMs,
            isStale = false,

            source = summary.source,
            status = CompanionStatus.fromString(summary.status),
            toolName = summary.toolName,
            workspaceName = summary.workspaceName,
            latestMessage = summary.message,

            pendingAction = PendingAction.fromString(summary.pendingAction),
            questionHeader = summary.questionHeader,
            questionText = summary.questionText,

            sessions = summary.sessions
                ?.map { session ->
                    SessionCard(
                        sessionId = session.sessionId,
                        source = session.source,
                        status = CompanionStatus.fromString(session.status),
                        toolName = session.toolName,
                        workspaceName = session.workspaceName,
                        message = session.message
                    )
                }
                ?: emptyList(),

            canApprove = false,
            canDeny = false,
            canSkip = false,
            canAnswerOnPhone = false,
            actionHint = "当前 Mac 端未提供安卓可用的命令回传通道，请回到 Mac 处理",

            isDemoMode = previousState.isDemoMode,
            permissionState = previousState.permissionState,
            diagnosticMessage = null,
            lastDeliveredSequence = summary.sequence
        )
    }

    private fun parseInstant(iso8601: String): Instant? {
        return try {
            Instant.parse(iso8601)
        } catch (_: Exception) {
            null
        }
    }
}
