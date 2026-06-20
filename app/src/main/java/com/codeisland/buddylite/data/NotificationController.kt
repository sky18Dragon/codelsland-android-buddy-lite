package com.codeisland.buddylite.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.codeisland.buddylite.MainActivity
import com.codeisland.buddylite.data.local.NotificationLogDao
import com.codeisland.buddylite.data.local.NotificationLogEntity
import com.codeisland.buddylite.data.model.BuddyDashboardState

class NotificationController(
    private val context: Context,
    private val notificationLogDao: NotificationLogDao
) {
    companion object {
        const val CHANNEL_SYNC = "buddy_sync"
        const val CHANNEL_ALERT = "buddy_alert"
        const val SYNC_NOTIFICATION_ID = 1
        const val ALERT_NOTIFICATION_ID = 2
    }

    init {
        createChannels()
    }

    fun createSyncNotification(deviceName: String?): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setContentTitle("CodeIsland Buddy")
            .setContentText(if (deviceName != null) "已连接 $deviceName" else "正在同步...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    suspend fun evaluateAlert(state: BuddyDashboardState, sequence: Long) {
        if (state.isDemoMode) return
        if (!hasNotificationPermission()) return
        if (!state.status.isWaiting) return

        val action = state.pendingAction ?: return
        if (action !is com.codeisland.buddylite.data.model.PendingAction) return

        val lastLog = notificationLogDao.getLastForSequence(sequence)
        if (lastLog != null &&
            System.currentTimeMillis() - lastLog.notifiedAt < 300_000
        ) return

        val title = "${state.source.uppercase()} 需要你的注意"
        val body = when (action) {
            com.codeisland.buddylite.data.model.PendingAction.APPROVAL ->
                "等待批准: ${(state.latestMessage ?: "").take(80)}"
            com.codeisland.buddylite.data.model.PendingAction.QUESTION ->
                "等待回答: ${(state.questionText ?: "").take(80)}"
        }

        postAlert(title, body)
        notificationLogDao.insert(
            NotificationLogEntity(
                sequence = sequence,
                pendingAction = action.name,
                notifiedAt = System.currentTimeMillis()
            )
        )
    }

    private fun postAlert(title: String, body: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSubText("请回到 Mac 处理")
            .build()

        NotificationManagerCompat.from(context).notify(ALERT_NOTIFICATION_ID, notification)
    }

    fun cancelAlert() {
        NotificationManagerCompat.from(context).cancel(ALERT_NOTIFICATION_ID)
    }

    private fun createChannels() {
        val syncChannel = NotificationChannel(
            CHANNEL_SYNC,
            "同步服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "前台同步服务常驻通知"
            setShowBadge(false)
        }

        val alertChannel = NotificationChannel(
            CHANNEL_ALERT,
            "状态提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Agent 等待批准或回答时的提醒"
            setShowBadge(true)
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(syncChannel)
        manager.createNotificationChannel(alertChannel)
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
