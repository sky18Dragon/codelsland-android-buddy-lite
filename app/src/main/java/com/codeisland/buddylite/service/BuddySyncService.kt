package com.codeisland.buddylite.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.codeisland.buddylite.MainActivity
import com.codeisland.buddylite.ble.BleTransport
import com.codeisland.buddylite.data.NotificationController

class BuddySyncService : Service() {

    private lateinit var bleTransport: BleTransport

    override fun onCreate() {
        super.onCreate()
        bleTransport = BleTransport(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as com.codeisland.buddylite.BuddyLiteApp
        val repo = app.locator.repository

        startForeground(
            NotificationController.SYNC_NOTIFICATION_ID,
            buildOngoingNotification()
        )

        repo.bindTransport(bleTransport)
        bleTransport.start()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        val app = application as? com.codeisland.buddylite.BuddyLiteApp
        app?.locator?.repository?.unbindTransport()
        bleTransport.stop()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Don't stop; allow restart via START_STICKY
        super.onTaskRemoved(rootIntent)
    }

    private fun buildOngoingNotification(): android.app.Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NotificationController.CHANNEL_SYNC)
            .setContentTitle("CodeIsland Buddy")
            .setContentText("正在同步...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }
}
