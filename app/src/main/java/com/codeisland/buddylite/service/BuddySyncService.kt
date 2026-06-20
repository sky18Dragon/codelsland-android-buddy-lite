package com.codeisland.buddylite.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.codeisland.buddylite.ble.BuddyPeripheralService

class BuddySyncService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        BuddyPeripheralService.start(this)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }
}
