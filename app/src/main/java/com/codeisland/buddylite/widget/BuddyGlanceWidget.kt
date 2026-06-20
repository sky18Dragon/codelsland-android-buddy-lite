package com.codeisland.buddylite.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.LocalContext
import android.content.Intent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.codeisland.buddylite.MainActivity

class BuddyGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as com.codeisland.buddylite.BuddyLiteApp
        val state = app.locator.widgetSnapshotStore.load()

        provideContent {
            GlanceTheme {
                WidgetContent(state)
            }
        }
    }

    fun update(context: Context, manager: android.appwidget.AppWidgetManager, appWidgetId: Int) {
        val app = context.applicationContext as? com.codeisland.buddylite.BuddyLiteApp ?: return
        val state = runCatching {
            kotlinx.coroutines.runBlocking {
                app.locator.widgetSnapshotStore.load()
            }
        }.getOrNull() ?: return

        val views = RemoteViews(context.packageName, android.R.layout.simple_list_item_1)
        // Use Glance to render
        update(context, manager, appWidgetId)
    }
}

@Composable
private fun WidgetContent(state: com.codeisland.buddylite.data.model.BuddyDashboardState?) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ColorProvider(Color(0xFF0D0D12)))
            .cornerRadius(18.dp)
            .padding(12.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
    ) {
        // Title
        Text(
            text = "CodeIsland Buddy",
            style = TextStyle(
                color = ColorProvider(0xFFFFFFFF.toInt()),
                fontWeight = FontWeight.Medium
            )
        )

        if (state != null && state.source.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.height(6.dp))

            // Status dot + source
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "●",
                    style = TextStyle(color = statusColor(state.status.name))
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(
                    text = state.source.uppercase(),
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFFFFFFF)),
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = state.status.label,
                    style = TextStyle(
                        color = ColorProvider(Color(0x99FFFFFF))
                    )
                )
            }

            // Workspace / Tool
            state.workspaceName?.let { ws ->
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = "workspace: $ws",
                    style = TextStyle(color = ColorProvider(Color(0x99FFFFFF))),
                    maxLines = 1
                )
            }
            state.toolName?.let { tool ->
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = "tool: $tool",
                    style = TextStyle(color = ColorProvider(Color(0x99FFFFFF))),
                    maxLines = 1
                )
            }

            // Session count
            val sessionCount = state.sessions.size
            val activeCount = state.sessions.count { it.status.isActive || it.status.isWaiting }
            if (sessionCount > 0) {
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = "$sessionCount 个会话 · $activeCount 个活跃",
                    style = TextStyle(color = ColorProvider(Color(0x66FFFFFF)))
                )
            }

            // Sync time
            if (state.lastSyncEpochMs > 0) {
                val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                val time = fmt.format(java.util.Date(state.lastSyncEpochMs))
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = "更新于 $time",
                    style = TextStyle(color = ColorProvider(Color(0x66FFFFFF)))
                )
            }
        } else {
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = "等待连接 Mac...",
                style = TextStyle(color = ColorProvider(Color(0x99FFFFFF)))
            )
        }
    }
}

private fun statusColor(status: String): ColorProvider = when (status.lowercase()) {
    "idle" -> ColorProvider(Color(0xFF8C96A8))
    "processing", "running" -> ColorProvider(Color(0xFF4DD964))
    "waitingapproval", "waitingquestion" -> ColorProvider(Color(0xFFFF8C00))
    else -> ColorProvider(Color(0xFF8C96A8))
}
