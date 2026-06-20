package com.codeisland.buddylite.di

import android.content.Context
import com.codeisland.buddylite.data.BuddyRepository
import com.codeisland.buddylite.data.NotificationController
import com.codeisland.buddylite.data.local.BuddyDatabase
import com.codeisland.buddylite.data.local.PreferencesDataStore
import com.codeisland.buddylite.data.local.WidgetSnapshotStore

class ServiceLocator(
    val appContext: Context,
    val database: BuddyDatabase,
    val prefs: PreferencesDataStore
) {
    val widgetSnapshotStore = WidgetSnapshotStore(database.snapshotDao())

    val notificationController by lazy {
        NotificationController(appContext, database.notificationLogDao())
    }

    val repository by lazy {
        BuddyRepository(
            database = database,
            prefs = prefs,
            notificationController = notificationController,
            widgetSnapshotStore = widgetSnapshotStore
        )
    }
}
