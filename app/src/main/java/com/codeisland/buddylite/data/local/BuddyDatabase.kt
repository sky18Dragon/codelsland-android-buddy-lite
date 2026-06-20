package com.codeisland.buddylite.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DashboardSnapshotEntity::class,
        NotificationLogEntity::class,
        DiagnosticEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class BuddyDatabase : RoomDatabase() {

    abstract fun snapshotDao(): DashboardSnapshotDao
    abstract fun notificationLogDao(): NotificationLogDao
    abstract fun diagnosticEventDao(): DiagnosticEventDao

    companion object {
        fun build(context: Context): BuddyDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                BuddyDatabase::class.java,
                "buddy_lite.db"
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
