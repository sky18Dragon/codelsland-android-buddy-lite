package com.codeisland.buddylite.ble

import android.os.SystemClock

class BleDiagnostics(
    private val maxEvents: Int = 200,
    private val onLog: (level: String, tag: String, message: String) -> Unit = { _, _, _ -> }
) {
    private val events = ArrayDeque<DiagnosticEntry>(maxEvents + 1)

    fun log(level: String, tag: String, message: String) {
        val entry = DiagnosticEntry(
            timestampMs = SystemClock.elapsedRealtime(),
            systemTimeMs = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message
        )
        synchronized(events) {
            events.addLast(entry)
            if (events.size > maxEvents) {
                events.removeFirst()
            }
        }
        onLog(level, tag, message)
    }

    fun scanStarted() = log("INFO", "ble-scan", "BLE scan started")
    fun scanStopped() = log("INFO", "ble-scan", "BLE scan stopped")
    fun deviceDiscovered(name: String, address: String, rssi: Int) =
        log("DEBUG", "ble-scan", "Discovered: $name ($address) RSSI=$rssi")
    fun connecting(address: String) = log("INFO", "ble-connect", "Connecting to $address")
    fun connected(name: String) = log("INFO", "ble-connect", "Connected to $name")
    fun servicesDiscovered(count: Int) = log("INFO", "ble-gatt", "Services discovered: $count")
    fun mtuNegotiated(mtu: Int) = log("INFO", "ble-gatt", "MTU negotiated: $mtu")
    fun subscribed() = log("INFO", "ble-gatt", "Notification subscribed")
    fun notificationReceived(bytes: Int) = log("DEBUG", "ble-notify", "Notification received: $bytes bytes")
    fun disconnected() = log("WARN", "ble-connect", "Disconnected")
    fun error(tag: String, message: String) = log("ERROR", tag, message)
    fun serviceNotFound() = log("ERROR", "ble-gatt", "CodeIsland service not found")
    fun characteristicNotFound() = log("ERROR", "ble-gatt", "Notify characteristic not found")
    fun chunkParseError(detail: String) = log("WARN", "ble-chunk", "Chunk parse error: $detail")

    fun getRecent(limit: Int = 20): List<DiagnosticEntry> {
        synchronized(events) {
            return events.takeLast(limit).toList()
        }
    }

    fun getSummary(): BleDiagnosticsSummary {
        val all = synchronized(events) { events.toList() }
        val connectedCount = all.count { it.message.contains("Connected to") }
        val disconnectedCount = all.count { it.message.contains("Disconnected") }
        val errors = all.filter { it.level == "ERROR" }
        val lastSync = all.lastOrNull { it.message.contains("Summary received") }
        return BleDiagnosticsSummary(
            totalEvents = all.size,
            connectionAttempts = connectedCount,
            disconnections = disconnectedCount,
            errorCount = errors.size,
            lastError = errors.lastOrNull()?.message,
            lastSyncMs = lastSync?.systemTimeMs ?: 0L,
            recentEvents = all.takeLast(20)
        )
    }
}

data class DiagnosticEntry(
    val timestampMs: Long,
    val systemTimeMs: Long,
    val level: String,
    val tag: String,
    val message: String
)

data class BleDiagnosticsSummary(
    val totalEvents: Int,
    val connectionAttempts: Int,
    val disconnections: Int,
    val errorCount: Int,
    val lastError: String?,
    val lastSyncMs: Long,
    val recentEvents: List<DiagnosticEntry>
)
