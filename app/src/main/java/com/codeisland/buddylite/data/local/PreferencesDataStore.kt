package com.codeisland.buddylite.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "buddy_prefs")

class PreferencesDataStore(private val context: Context) {

    companion object Keys {
        val LAST_CONNECTED_ADDRESS = stringPreferencesKey("last_connected_address")
        val LAST_CONNECTED_NAME = stringPreferencesKey("last_connected_name")
        val LAST_DELIVERED_SEQUENCE = longPreferencesKey("last_delivered_sequence")
        val LAST_NOTIFIED_SEQUENCE = longPreferencesKey("last_notified_sequence")
        val DEMO_MODE_ENABLED = stringPreferencesKey("demo_mode_enabled")
    }

    val lastConnectedAddress: Flow<String?> = context.dataStore.data.map { it[LAST_CONNECTED_ADDRESS] }
    val lastConnectedName: Flow<String?> = context.dataStore.data.map { it[LAST_CONNECTED_NAME] }
    val lastDeliveredSequence: Flow<Long> = context.dataStore.data.map { it[LAST_DELIVERED_SEQUENCE] ?: 0L }
    val lastNotifiedSequence: Flow<Long> = context.dataStore.data.map { it[LAST_NOTIFIED_SEQUENCE] ?: 0L }

    suspend fun getLastConnectedAddress(): String? = context.dataStore.data.first()[LAST_CONNECTED_ADDRESS]
    suspend fun getLastConnectedName(): String? = context.dataStore.data.first()[LAST_CONNECTED_NAME]
    suspend fun getLastDeliveredSequence(): Long = context.dataStore.data.first()[LAST_DELIVERED_SEQUENCE] ?: 0L
    suspend fun getLastNotifiedSequence(): Long = context.dataStore.data.first()[LAST_NOTIFIED_SEQUENCE] ?: 0L

    suspend fun setLastConnectedDevice(address: String, name: String) {
        context.dataStore.edit {
            it[LAST_CONNECTED_ADDRESS] = address
            it[LAST_CONNECTED_NAME] = name
        }
    }

    suspend fun setLastDeliveredSequence(seq: Long) {
        context.dataStore.edit { it[LAST_DELIVERED_SEQUENCE] = seq }
    }

    suspend fun setLastNotifiedSequence(seq: Long) {
        context.dataStore.edit { it[LAST_NOTIFIED_SEQUENCE] = seq }
    }

    suspend fun clearDevice() {
        context.dataStore.edit {
            it.remove(LAST_CONNECTED_ADDRESS)
            it.remove(LAST_CONNECTED_NAME)
        }
    }
}
