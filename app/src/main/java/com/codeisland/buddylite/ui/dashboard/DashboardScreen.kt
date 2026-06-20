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
import com.codeisland.buddylite.data.model.PeripheralState
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
    onRequestPermissions: () -> Unit,
    onApprove: () -> Unit = {},
    onDeny: () -> Unit = {},
    onSkip: () -> Unit = {},
    onFocus: () -> Unit = {}
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
            state.peripheralState == PeripheralState.PERMISSION_REQUIRED -> {
                item { PermissionBlockedCard(onRequestPermissions = onRequestPermissions) }
            }
            state.peripheralState == PeripheralState.BLUETOOTH_OFF ||
                (state.peripheralState != PeripheralState.CONNECTED && !state.isDemoMode) -> {
                item { AdvertisingCard(state = state, onEnterDemo = onEnterDemo, onRescan = onRescan) }
            }
            state.peripheralState == PeripheralState.STARTING -> {
                item { StartingCard() }
            }
            else -> {
                // Connected or Demo - show full dashboard
                item { MessageCard(state = state) }
                // Command buttons (approve/deny/skip/focus)
                if (state.canApprove || state.canSkip) {
                    item { CommandButtons(state = state, onApprove = onApprove, onDeny = onDeny, onSkip = onSkip, onFocus = onFocus) }
                }
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
    if (state.connectedDeviceName != null) return state.connectedDeviceName
    return when (state.peripheralState) {
        PeripheralState.STARTING -> "正在启动 Buddy 广播..."
        PeripheralState.ADVERTISING -> "等待 Mac 连接"
        PeripheralState.CONNECTED -> if (state.macSubscribed) "已连接 · 已订阅" else "已连接 · 等待订阅"
        PeripheralState.PERMISSION_REQUIRED -> "需要权限"
        PeripheralState.UNSUPPORTED -> "不支持 BLE 外设"
        PeripheralState.BLUETOOTH_OFF -> "蓝牙已关闭"
        PeripheralState.ERROR -> "错误"
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
private fun AdvertisingCard(
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
        Text(text = "等待 Mac 连接", color = BuddyColors.OnSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(text = state.peripheralState.label, color = BuddyColors.OnSurfaceDim, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(color = BuddyColors.Divider)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "手机正在广播为 Buddy 设备。在 Mac 上打开 CodeIsland → 设置 → Buddy，Mac 会自动发现并连接。",
            color = BuddyColors.OnSurfaceFaint, fontSize = 13.sp, fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BuddyButton("重启广播", Icons.Default.Refresh, BuddyColors.AccentGreen, onRescan, Modifier.weight(1f))
            BuddyButton("进入演示模式", Icons.Default.PlayArrow, BuddyColors.AccentBlue, onEnterDemo, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StartingCard() {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(BuddyColors.Surface)
            .border(1.dp, BuddyColors.CardBorder, RoundedCornerShape(18.dp)).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), color = BuddyColors.StatusActive, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("正在启动 Buddy 广播...", color = BuddyColors.OnSurface.copy(alpha = 0.72f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun CommandButtons(
    state: BuddyDashboardState, onApprove: () -> Unit, onDeny: () -> Unit, onSkip: () -> Unit, onFocus: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!state.macSubscribed) {
            Text("Mac 未订阅通知，操作不可用", color = BuddyColors.OnSurfaceDisabled, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.canApprove) {
                BuddyButton("批准", Icons.Default.Search, BuddyColors.AccentGreen, onApprove, Modifier.weight(1f))
                BuddyButton("拒绝", Icons.Default.Stop, BuddyColors.AccentRed, onDeny, Modifier.weight(1f))
            }
            if (state.canSkip) {
                BuddyButton("跳过", Icons.Default.Refresh, BuddyColors.AccentOrange, onSkip, Modifier.weight(1f))
                BuddyButton("打开 Mac", Icons.Default.PlayArrow, BuddyColors.AccentGreen, onFocus, Modifier.weight(1f))
            }
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
