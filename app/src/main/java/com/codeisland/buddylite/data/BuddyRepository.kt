package com.codeisland.buddylite.data

import android.os.SystemClock
import com.codeisland.buddylite.data.local.BuddyDatabase
import com.codeisland.buddylite.data.local.DiagnosticEventEntity
import com.codeisland.buddylite.data.local.PreferencesDataStore
import com.codeisland.buddylite.data.local.WidgetSnapshotStore
import com.codeisland.buddylite.data.model.BuddyDashboardState
import com.codeisland.buddylite.data.model.CompanionStatus
import com.codeisland.buddylite.data.model.ConnectionState
import com.codeisland.buddylite.data.model.PermissionState
import com.codeisland.buddylite.data.model.SessionCard
import com.codeisland.buddylite.protocol.CompanionBluetoothSummary
import com.codeisland.buddylite.protocol.ProtocolMapper
import com.codeisland.buddylite.protocol.TransportEvent
import com.codeisland.buddylite.protocol.TransportLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class BuddyRepository(
    private val database: BuddyDatabase,
    private val prefs: PreferencesDataStore,
    private val notificationController: NotificationController,
    private val widgetSnapshotStore: WidgetSnapshotStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _dashboardState = MutableStateFlow(BuddyDashboardState())
    val dashboardState: StateFlow<BuddyDashboardState> = _dashboardState.asStateFlow()

    private val _events = MutableSharedFlow<OneShotEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<OneShotEvent> = _events.asSharedFlow()

    private var transportLayer: TransportLayer? = null
    private var transportCollectionJob: kotlinx.coroutines.Job? = null
    private var staleCheckJob: kotlinx.coroutines.Job? = null

    private val demoSources = listOf("codex", "claude", "gemini", "copilot", "cursor")
    private val demoStatuses = listOf(
        CompanionStatus.IDLE,
        CompanionStatus.PROCESSING,
        CompanionStatus.RUNNING,
        CompanionStatus.WAITING_APPROVAL,
        CompanionStatus.WAITING_QUESTION
    )
    private var demoIndex = 0

    fun bindTransport(transport: TransportLayer) {
        transportLayer = transport
        transportCollectionJob?.cancel()
        transportCollectionJob = scope.launch {
            transport.events.collect { event -> handleTransportEvent(event) }
        }
    }

    fun unbindTransport() {
        transportCollectionJob?.cancel()
        transportLayer = null
    }

    suspend fun loadInitialState() {
        val snapshot = widgetSnapshotStore.load()
        val lastSeq = prefs.getLastDeliveredSequence()
        if (snapshot != null) {
            _dashboardState.value = snapshot.copy(
                connectionState = ConnectionState.DISCONNECTED,
                isStale = true,
                lastDeliveredSequence = lastSeq
            )
        }
        staleCheckJob?.cancel()
        staleCheckJob = scope.launch { staleCheckLoop() }
    }

    fun updatePermissionState(state: PermissionState) {
        _dashboardState.update { current ->
            current.copy(
                permissionState = state,
                connectionState = when {
                    state == PermissionState.DENIED -> ConnectionState.PERMISSION_BLOCKED
                    current.connectionState == ConnectionState.PERMISSION_BLOCKED && state == PermissionState.GRANTED ->
                        ConnectionState.IDLE
                    else -> current.connectionState
                }
            )
        }
    }

    fun enterDemoMode() {
        demoIndex = 0
        _dashboardState.update { current ->
            current.copy(isDemoMode = true)
        }
        advanceDemo()
    }

    fun exitDemoMode() {
        _dashboardState.update { current ->
            current.copy(isDemoMode = false)
        }
    }

    fun cycleDemoState() {
        advanceDemo()
    }

    private var diagnosticCount = 0

    fun logDiagnostic(level: String, tag: String, message: String) {
        scope.launch {
            database.diagnosticEventDao().insert(
                DiagnosticEventEntity(
                    timestamp = System.currentTimeMillis(),
                    level = level,
                    tag = tag,
                    message = message
                )
            )
            diagnosticCount++
            if (diagnosticCount >= 20) {
                database.diagnosticEventDao().evictOldest(200)
                diagnosticCount = 0
            }
        }
        _dashboardState.update { it.copy(diagnosticMessage = message) }
    }

    private suspend fun handleTransportEvent(event: TransportEvent) {
        when (event) {
            is TransportEvent.Started -> updateConnection(ConnectionState.SCANNING)
            is TransportEvent.Discovered -> { /* logged via diagnostics */ }
            is TransportEvent.Connecting -> updateConnection(ConnectionState.CONNECTING)
            is TransportEvent.Connected -> {
                updateConnection(ConnectionState.CONNECTED)
                _dashboardState.update { it.copy(connectedDeviceName = event.name) }
                prefs.setLastConnectedDevice("", event.name)
            }
            is TransportEvent.MtuNegotiated -> { /* logged */ }
            is TransportEvent.Subscribed -> updateConnection(ConnectionState.CONNECTED)
            is TransportEvent.SummaryReceived -> handleSummaryReceived(event.jsonBytes)
            is TransportEvent.Stale -> {
                _dashboardState.update { it.copy(connectionState = ConnectionState.STALE, isStale = true) }
            }
            is TransportEvent.Disconnected -> handleDisconnected()
            is TransportEvent.Error -> logDiagnostic("ERROR", "transport", event.message)
            is TransportEvent.BluetoothOff -> updateConnection(ConnectionState.DISCONNECTED)
            is TransportEvent.BluetoothOn -> updateConnection(ConnectionState.IDLE)
            is TransportEvent.PermissionDenied -> updatePermissionState(PermissionState.DENIED)
        }
    }

    private suspend fun handleSummaryReceived(jsonBytes: ByteArray) {
        val state = _dashboardState.value
        if (state.isDemoMode) return

        try {
            val summary = json.decodeFromString<CompanionBluetoothSummary>(jsonBytes.decodeToString())
            if (summary.sequence <= state.lastDeliveredSequence) return

            val newState = ProtocolMapper.mapToDomainState(summary, state)
            _dashboardState.value = newState.copy(isStale = false)

            prefs.setLastDeliveredSequence(summary.sequence)
            widgetSnapshotStore.save(newState)
            notificationController.evaluateAlert(newState, summary.sequence)
        } catch (e: Exception) {
            logDiagnostic("ERROR", "protocol", "JSON decode failed: ${e.message}")
        }
    }

    private fun handleDisconnected() {
        _dashboardState.update { current ->
            current.copy(
                connectionState = ConnectionState.DISCONNECTED,
                isStale = true,
                connectedDeviceName = null
            )
        }
    }

    private fun updateConnection(state: ConnectionState) {
        _dashboardState.update { current ->
            if (current.isDemoMode) current
            else current.copy(connectionState = state)
        }
    }

    private suspend fun staleCheckLoop() {
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            delay(5_000)
            val state = _dashboardState.value
            if (state.isDemoMode) continue
            if (state.connectionState != ConnectionState.CONNECTED && state.connectionState != ConnectionState.STALE) continue
            if (state.lastSyncEpochMs == 0L) continue

            val elapsed = System.currentTimeMillis() - state.lastSyncEpochMs
            when {
                elapsed > 60_000 -> {
                    // Direct to DISCONNECTED regardless of whether currently CONNECTED or STALE
                    handleDisconnected()
                }
                elapsed > 30_000 && state.connectionState == ConnectionState.CONNECTED -> {
                    _dashboardState.update { it.copy(connectionState = ConnectionState.STALE, isStale = true) }
                }
            }
        }
    }

    private fun advanceDemo() {
        val mascot = demoSources[demoIndex % demoSources.size]
        val status = demoStatuses[demoIndex % demoStatuses.size]
        demoIndex++

        val demoTools = listOf("WebSearch", "Read", "Bash", "Grep", null)
        val demoWorkspaces = listOf("repo/CodeIsland", "docs/", "src/main", null, null)

        _dashboardState.update { current ->
            current.copy(
                isDemoMode = true,
                source = mascot,
                status = status,
                toolName = demoTools.random(),
                workspaceName = demoWorkspaces.random(),
                latestMessage = "演示模式 · ${status.label}",
                pendingAction = when (status) {
                    CompanionStatus.WAITING_APPROVAL -> com.codeisland.buddylite.data.model.PendingAction.APPROVAL
                    CompanionStatus.WAITING_QUESTION -> com.codeisland.buddylite.data.model.PendingAction.QUESTION
                    else -> null
                },
                questionHeader = if (status.isWaiting) "示例问题" else null,
                questionText = if (status.isWaiting) "这是一个演示问题，请回到 Mac 处理" else null,
                sessions = demoSources.take(3).mapIndexed { i, s ->
                    SessionCard(
                        sessionId = "demo-$i",
                        source = s,
                        status = demoStatuses[(demoIndex + i) % demoStatuses.size],
                        toolName = demoTools[i % demoTools.size],
                        workspaceName = demoWorkspaces[i % demoWorkspaces.size],
                        message = "Demo session $i"
                    )
                },
                connectionState = ConnectionState.CONNECTED,
                connectedDeviceName = "Demo Mac"
            )
        }
    }
}

sealed interface OneShotEvent {
    data class FireAlert(val sequence: Long, val pendingAction: String) : OneShotEvent
    data object WidgetUpdate : OneShotEvent
}
