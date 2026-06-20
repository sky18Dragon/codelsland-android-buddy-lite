package com.codeisland.buddylite.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dashboard_snapshots")
data class DashboardSnapshotEntity(
    @PrimaryKey val id: Int = 1,
    val jsonPayload: String,
    val updatedAt: Long
)

@Entity(tableName = "notification_log")
data class NotificationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sequence: Long,
    val pendingAction: String,
    val notifiedAt: Long
)

@Entity(tableName = "diagnostic_events")
data class DiagnosticEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val level: String,
    val tag: String,
    val message: String
)
