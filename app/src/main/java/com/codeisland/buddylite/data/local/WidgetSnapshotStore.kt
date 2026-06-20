package com.codeisland.buddylite.data.local

import com.codeisland.buddylite.data.model.BuddyDashboardState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WidgetSnapshotStore(
    private val dao: DashboardSnapshotDao
) {
    private val json = Json { encodeDefaults = true }

    suspend fun save(state: BuddyDashboardState) {
        val payload = json.encodeToString(state)
        dao.upsert(
            DashboardSnapshotEntity(
                id = 1,
                jsonPayload = payload,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun load(): BuddyDashboardState? {
        val entity = dao.getLatest() ?: return null
        return try {
            json.decodeFromString<BuddyDashboardState>(entity.jsonPayload)
        } catch (_: Exception) {
            null
        }
    }
}
