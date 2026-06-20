package com.codeisland.buddylite.ui.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeisland.buddylite.data.model.BuddyDashboardState
import com.codeisland.buddylite.data.model.ConnectionState
import com.codeisland.buddylite.data.model.PermissionState
import com.codeisland.buddylite.ui.components.BuddyButton
import com.codeisland.buddylite.ui.theme.BuddyColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    state: BuddyDashboardState,
    isHyperOS: Boolean,
    onBack: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenAutostartSettings: () -> Unit,
    onRequestPermissions: () -> Unit,
    onRescan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BuddyColors.Background)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White
                )
            }
            Text(
                text = "诊断",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Bluetooth status
            item { SectionHeader("蓝牙状态") }
            item { StatusRow("连接状态", state.connectionState.name) }
            item { StatusRow("已连接设备", state.connectedDeviceName ?: "无") }
            item {
                val lastSync = if (state.lastSyncEpochMs > 0) {
                    val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    fmt.format(Date(state.lastSyncEpochMs))
                } else "从未同步"
                StatusRow("最近同步", lastSync)
            }
            item { StatusRow("序列号", state.lastDeliveredSequence.toString()) }

            // Permission status
            item { SectionHeader("权限状态") }
            item {
                StatusRow(
                    "蓝牙权限",
                    if (state.permissionState == PermissionState.GRANTED) "已授权" else "未授权",
                    isGood = state.permissionState == PermissionState.GRANTED
                )
            }
            item {
                StatusRow(
                    "通知权限",
                    if (state.permissionState == PermissionState.GRANTED) "已授权" else "未授权",
                    isGood = state.permissionState == PermissionState.GRANTED
                )
            }
            item {
                StatusRow(
                    "前台服务",
                    "已注册",
                    isGood = true
                )
            }

            // HyperOS guidance
            if (isHyperOS) {
                item { SectionHeader("HyperOS 后台设置") }
                item {
                    Text(
                        text = "为确保后台同步稳定，建议在系统设置中完成以下配置：",
                        color = BuddyColors.OnSurfaceDim,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                item {
                    BuddyButton(
                        text = "打开后台自启动",
                        icon = Icons.Default.Build,
                        tint = BuddyColors.AccentBlue,
                        onClick = onOpenAutostartSettings
                    )
                }
                item {
                    BuddyButton(
                        text = "关闭电池优化",
                        icon = Icons.Default.Build,
                        tint = BuddyColors.AccentOrange,
                        onClick = onOpenBatterySettings
                    )
                }
            }

            // Actions
            item { SectionHeader("操作") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BuddyButton(
                        text = "验证权限",
                        icon = Icons.Default.CheckCircle,
                        tint = BuddyColors.AccentBlue,
                        onClick = onRequestPermissions,
                        modifier = Modifier.weight(1f)
                    )
                    BuddyButton(
                        text = "重新搜索",
                        icon = Icons.Default.Refresh,
                        tint = BuddyColors.AccentGreen,
                        onClick = onRescan,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                BuddyButton(
                    text = "打开应用设置",
                    icon = Icons.Default.Error,
                    tint = BuddyColors.AccentOrange,
                    onClick = onOpenAppSettings
                )
            }

            // Diagnostic info
            state.diagnosticMessage?.let { msg ->
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(BuddyColors.AccentRed.copy(alpha = 0.12f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "最近错误",
                            color = BuddyColors.AccentRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = msg,
                            color = BuddyColors.AccentRed.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Bottom padding
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = Color.White.copy(alpha = 0.45f),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun StatusRow(label: String, value: String, isGood: Boolean? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BuddyColors.Surface)
            .border(1.dp, BuddyColors.Divider, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = BuddyColors.OnSurfaceDim,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isGood != null) {
                Icon(
                    imageVector = if (isGood) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (isGood) BuddyColors.StatusActive else BuddyColors.AccentRed,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = value,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
