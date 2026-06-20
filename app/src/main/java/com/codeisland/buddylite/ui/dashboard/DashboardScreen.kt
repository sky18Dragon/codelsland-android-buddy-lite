package com.codeisland.buddylite.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeisland.buddylite.data.model.BuddyDashboardState
import com.codeisland.buddylite.data.model.CompanionStatus
import com.codeisland.buddylite.data.model.ConnectionState
import com.codeisland.buddylite.data.model.SessionCard
import com.codeisland.buddylite.ui.components.BuddyButton
import com.codeisland.buddylite.ui.components.MetadataChip
import com.codeisland.buddylite.ui.components.PulseDot
import com.codeisland.buddylite.ui.components.StatusPill
import com.codeisland.buddylite.ui.theme.BuddyColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    state: BuddyDashboardState,
    onNavigateToDiagnostics: () -> Unit,
    onEnterDemo: () -> Unit,
    onExitDemo: () -> Unit,
    onCycleDemo: () -> Unit,
    onRescan: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BuddyColors.Background)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Status Header Card
        item {
            StatusHeaderCard(state = state)
        }

        // Connection state-specific content
        when {
            state.connectionState == ConnectionState.PERMISSION_BLOCKED -> {
                item { PermissionBlockedCard(onRequestPermissions = onRequestPermissions) }
            }
            state.connectionState == ConnectionState.DISCONNECTED && !state.isDemoMode -> {
                item { DiscoveryCard(state = state, onEnterDemo = onEnterDemo, onRescan = onRescan) }
            }
            state.connectionState == ConnectionState.SCANNING -> {
                item { ScanningCard(onEnterDemo = onEnterDemo) }
            }
            state.connectionState.isTransitional -> {
                item { ConnectingCard(state = state) }
            }
            else -> {
                // Connected or Stale or Demo - show full dashboard
                item { MessageCard(state = state) }
                if (state.pendingAction != null && state.questionText != null) {
                    item { QuestionCard(state = state) }
                }
                if (state.sessions.isNotEmpty()) {
                    item { SessionsCard(sessions = state.sessions) }
                }
            }
        }

        // Action buttons
        item {
            ActionBar(
                isDemoMode = state.isDemoMode,
                onEnterDemo = onEnterDemo,
                onExitDemo = onExitDemo,
                onCycleDemo = onCycleDemo,
                onDiagnostics = onNavigateToDiagnostics,
                onRescan = onRescan
            )
        }

        // Stale indicator
        if (state.isStale && state.connectionState == ConnectionState.STALE) {
            item {
                StaleWarning()
            }
        }

        // Diagnostic message
        state.diagnosticMessage?.let { msg ->
            item {
                DiagnosticBanner(message = msg)
            }
        }
    }
}

@Composable
private fun StatusHeaderCard(state: BuddyDashboardState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BuddyColors.Surface)
            .border(1.dp, BuddyColors.CardBorder, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (state.source.isNotEmpty()) state.source.uppercase() else "CODEISLAND",
                    color = BuddyColors.OnSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle(state),
                    color = BuddyColors.OnSurfaceDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            StatusPill(status = state.status)
        }
    }
}

private fun subtitle(state: BuddyDashboardState): String {
    if (state.isDemoMode) return "演示模式"
    if (state.isStale) return "数据延迟 · ${state.connectedDeviceName ?: ""}"
    return state.connectedDeviceName
        ?: when (state.connectionState) {
            ConnectionState.DISCONNECTED -> "离线"
            ConnectionState.SCANNING -> "搜索中"
            else -> ""
        }
}

@Composable
private fun MessageCard(state: BuddyDashboardState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BuddyColors.Surface)
            .border(1.dp, BuddyColors.CardBorder, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Text(
            text = state.latestMessage ?: "等待同步数据...",
            color = if (state.latestMessage != null) Color.White.copy(alpha = 0.86f)
            else Color.White.copy(alpha = 0.42f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 5
        )
        if (state.toolName != null || state.workspaceName != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.workspaceName?.let { ws ->
                    MetadataChip(
                        icon = Icons.Default.Build,
                        text = ws
                    )
                }
                state.toolName?.let { tool ->
                    MetadataChip(
                        icon = Icons.Default.Search,
                        text = tool
                    )
                }
            }
        }
    }
}

@Composable
private fun QuestionCard(state: BuddyDashboardState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BuddyColors.SurfaceVariant)
            .border(1.dp, BuddyColors.StatusAttention.copy(alpha = 0.24f), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulseDot(
                status = CompanionStatus.WAITING_QUESTION,
                size = 10.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            state.questionHeader?.let { header ->
                Text(
                    text = header,
                    color = BuddyColors.StatusQuestion,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(BuddyColors.StatusQuestion.copy(alpha = 0.14f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = state.questionText ?: "",
            color = BuddyColors.OnSurface.copy(alpha = 0.9f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 5
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = state.actionHint,
            color = BuddyColors.OnSurfaceDisabled,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SessionsCard(sessions: List<SessionCard>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BuddyColors.Surface)
            .border(1.dp, BuddyColors.CardBorder, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Text(
            text = "会话",
            color = BuddyColors.OnSurface.copy(alpha = 0.45f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))
        sessions.forEachIndexed { index, session ->
            if (index > 0) {
                HorizontalDivider(
                    color = BuddyColors.Divider,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            SessionRow(session = session)
        }
    }
}

@Composable
private fun SessionRow(session: SessionCard) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PulseDot(status = session.status, size = 10.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.source.uppercase(),
                color = BuddyColors.OnSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            session.message?.let { msg ->
                Text(
                    text = msg.take(60),
                    color = BuddyColors.OnSurfaceDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                session.workspaceName?.let {
                    Text(text = it, color = BuddyColors.OnSurfaceFaint, fontSize = 11.sp)
                }
                session.toolName?.let {
                    Text(text = it, color = BuddyColors.OnSurfaceFaint, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun DiscoveryCard(
    state: BuddyDashboardState,
    onEnterDemo: () -> Unit,
    onRescan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BuddyColors.Surface)
            .border(1.dp, BuddyColors.CardBorder, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Text(
            text = "等待 Mac",
            color = BuddyColors.OnSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "搜索已停止",
            color = BuddyColors.OnSurfaceDim,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(color = BuddyColors.Divider)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "保持手机与 Mac 在同一网络，CodeIsland 会持续同步当前状态。",
            color = BuddyColors.OnSurfaceFaint,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BuddyButton(
                text = "搜索 Mac",
                icon = Icons.Default.Refresh,
                tint = BuddyColors.AccentGreen,
                onClick = onRescan,
                modifier = Modifier.weight(1f)
            )
            BuddyButton(
                text = "进入演示模式",
                icon = Icons.Default.PlayArrow,
                tint = BuddyColors.AccentBlue,
                onClick = onEnterDemo,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ScanningCard(onEnterDemo: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BuddyColors.Surface)
            .border(1.dp, BuddyColors.CardBorder, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = BuddyColors.StatusActive,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "正在搜索附近的 CodeIsland",
                color = BuddyColors.OnSurface.copy(alpha = 0.72f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        BuddyButton(
            text = "进入演示模式",
            icon = Icons.Default.PlayArrow,
            tint = BuddyColors.AccentBlue,
            onClick = onEnterDemo
        )
    }
}

@Composable
private fun ConnectingCard(state: BuddyDashboardState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BuddyColors.Surface)
            .border(1.dp, BuddyColors.CardBorder, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = BuddyColors.StatusActive,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "正在连接 ${state.connectedDeviceName ?: "Mac"}...",
                color = BuddyColors.OnSurface.copy(alpha = 0.72f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun PermissionBlockedCard(onRequestPermissions: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BuddyColors.Surface)
            .border(1.dp, BuddyColors.StatusAttention.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Text(
            text = "需要蓝牙权限",
            color = BuddyColors.OnSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "CodeIsland Buddy 需要蓝牙权限来发现和连接 Mac",
            color = BuddyColors.OnSurfaceDim,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(10.dp))
        BuddyButton(
            text = "授予权限",
            icon = Icons.Default.Build,
            tint = BuddyColors.AccentBlue,
            onClick = onRequestPermissions
        )
    }
}

@Composable
private fun ActionBar(
    isDemoMode: Boolean,
    onEnterDemo: () -> Unit,
    onExitDemo: () -> Unit,
    onCycleDemo: () -> Unit,
    onDiagnostics: () -> Unit,
    onRescan: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isDemoMode) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BuddyButton(
                    text = "切换演示状态",
                    icon = Icons.Default.Refresh,
                    tint = BuddyColors.AccentBlue,
                    onClick = onCycleDemo,
                    modifier = Modifier.weight(1f)
                )
                BuddyButton(
                    text = "退出演示",
                    icon = Icons.Default.Stop,
                    tint = BuddyColors.AccentRed,
                    onClick = onExitDemo,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BuddyButton(
                text = "诊断",
                icon = Icons.Default.Build,
                tint = BuddyColors.AccentBlue,
                onClick = onDiagnostics,
                modifier = Modifier.weight(1f)
            )
            BuddyButton(
                text = "重连",
                icon = Icons.Default.Refresh,
                tint = BuddyColors.AccentGreen,
                onClick = onRescan,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StaleWarning() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BuddyColors.StatusAttention.copy(alpha = 0.12f))
            .padding(12.dp)
    ) {
        Text(
            text = "数据同步延迟，请确认 Mac 上的 CodeIsland 正在运行",
            color = BuddyColors.StatusAttention,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DiagnosticBanner(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BuddyColors.AccentRed.copy(alpha = 0.12f))
            .padding(12.dp)
    ) {
        Text(
            text = message,
            color = BuddyColors.AccentRed,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 3
        )
    }
}
