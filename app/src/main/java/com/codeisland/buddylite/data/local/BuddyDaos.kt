package com.codeisland.buddylite.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DashboardSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: DashboardSnapshotEntity)

    @Query("SELECT * FROM dashboard_snapshots WHERE id = 1")
    suspend fun getLatest(): DashboardSnapshotEntity?

    @Query("DELETE FROM dashboard_snapshots")
    suspend fun clear()
}

@Dao
interface DiagnosticEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: DiagnosticEventEntity)

    @Query("SELECT * FROM diagnostic_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 200): List<DiagnosticEventEntity>

    @Query("DELETE FROM diagnostic_events WHERE id NOT IN (SELECT id FROM diagnostic_events ORDER BY timestamp DESC LIMIT :keep)")
    suspend fun evictOldest(keep: Int = 200)

    @Query("DELETE FROM diagnostic_events")
    suspend fun clear()
}

@Dao
interface NotificationLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: NotificationLogEntity)

    @Query("SELECT * FROM notification_log WHERE sequence = :sequence ORDER BY notifiedAt DESC LIMIT 1")
    suspend fun getLastForSequence(sequence: Long): NotificationLogEntity?

    @Query("SELECT * FROM notification_log ORDER BY notifiedAt DESC LIMIT 1")
    suspend fun getLast(): NotificationLogEntity?

    @Query("DELETE FROM notification_log WHERE notifiedAt < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long)

    @Query("DELETE FROM notification_log")
    suspend fun clear()
}
