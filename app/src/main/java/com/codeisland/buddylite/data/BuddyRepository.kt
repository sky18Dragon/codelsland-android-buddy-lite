package com.codeisland.buddylite.data

import com.codeisland.buddylite.ble.BleProtocol
import com.codeisland.buddylite.ble.IncomingCommand
import com.codeisland.buddylite.data.local.BuddyDatabase
import com.codeisland.buddylite.data.local.DiagnosticEventEntity
import com.codeisland.buddylite.data.local.PreferencesDataStore
import com.codeisland.buddylite.data.local.WidgetSnapshotStore
import com.codeisland.buddylite.data.model.AgentStatusCode
import com.codeisland.buddylite.data.model.BuddyDashboardState
import com.codeisland.buddylite.data.model.CompanionStatus
import com.codeisland.buddylite.data.model.ConnectionState
import com.codeisland.buddylite.data.model.DisplayMode
import com.codeisland.buddylite.data.model.Mascot
import com.codeisland.buddylite.data.model.MessagePreview
import com.codeisland.buddylite.data.model.PendingAction
import com.codeisland.buddylite.data.model.PeripheralState
import com.codeisland.buddylite.data.model.PermissionState
import com.codeisland.buddylite.data.model.ScreenOrientation
import com.codeisland.buddylite.data.model.SessionCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BuddyRepository(
    private val database: BuddyDatabase,
    private val prefs: PreferencesDataStore,
    private val notificationController: NotificationController,
    private val widgetSnapshotStore: WidgetSnapshotStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _dashboardState = MutableStateFlow(BuddyDashboardState())
    val dashboardState: StateFlow<BuddyDashboardState> = _dashboardState.asStateFlow()

    private val _events = MutableSharedFlow<OneShotEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<OneShotEvent> = _events.asSharedFlow()

    private var staleCheckJob: kotlinx.coroutines.Job? = null
    private var diagnosticCount = 0
    private var demoIndex = 0

    private val demoMascots = listOf(
        Mascot.CODEX,
        Mascot.CLAUDE,
        Mascot.GEMINI,
        Mascot.COPILOT,
        Mascot.CURSOR
    )
    private val demoStatuses = listOf(
        AgentStatusCode.IDLE,
        AgentStatusCode.PROCESSING,
        AgentStatusCode.RUNNING,
        AgentStatusCode.WAITING_APPROVAL,
        AgentStatusCode.WAITING_QUESTION
    )

    @Suppress("UNUSED_PARAMETER")
    fun bindTransport(transport: Any) = Unit

    fun unbindTransport() = Unit

    suspend fun loadInitialState() {
        val snapshot = widgetSnapshotStore.load()
        val lastSeq = prefs.getLastDeliveredSequence()
        if (snapshot != null) {
            _dashboardState.value = snapshot.copy(
                peripheralState = PeripheralState.STARTING,
                connectionState = ConnectionState.IDLE,
                displayMode = DisplayMode.STANDBY,
                connectedDeviceName = null,
                macSubscribed = false,
                isStale = true,
                lastDeliveredSequence = lastSeq
            )
        }
        staleCheckJob?.cancel()
        staleCheckJob = scope.launch { staleCheckLoop() }
    }

    fun updatePermissionState(state: PermissionState) {
        mutate("权限状态已更新: $state") { current ->
            val peripheralState = if (state == PermissionState.DENIED) {
                PeripheralState.PERMISSION_REQUIRED
            } else {
                current.peripheralState
            }
            current.copy(
                permissionState = state,
                peripheralState = peripheralState,
                connectionState = connectionStateFor(peripheralState)
            ).withDerivedActionState()
        }
    }

    fun onPeripheralStarting(message: String) {
        mutate(message) { current ->
            current.copy(
                peripheralState = PeripheralState.STARTING,
                connectionState = ConnectionState.SCANNING,
                connectedDeviceName = null,
                macSubscribed = false,
                isStale = false
            ).withDerivedActionState()
        }
    }

    fun onPeripheralAdvertising(message: String) {
        mutate(message) { current ->
            current.copy(
                peripheralState = PeripheralState.ADVERTISING,
                connectionState = ConnectionState.SCANNING,
                connectedDeviceName = null,
                macSubscribed = false,
                isStale = false
            ).withDerivedActionState()
        }
    }

    fun onPeripheralConnected(deviceName: String) {
        mutate("Buddy 已连接 Mac: $deviceName") { current ->
            current.copy(
                peripheralState = PeripheralState.CONNECTED,
                connectionState = ConnectionState.CONNECTED,
                connectedDeviceName = deviceName,
                isStale = false
            ).withDerivedActionState()
        }
        scope.launch { prefs.setLastConnectedDevice("", deviceName) }
    }

    fun onMacSubscriptionChanged(subscribed: Boolean) {
        mutate(if (subscribed) "Mac 已订阅 Buddy 上行通知" else "Mac 已取消 Buddy 上行订阅") { current ->
            current.copy(macSubscribed = subscribed).withDerivedActionState()
        }
    }

    fun onPeripheralDisconnected(message: String) {
        mutate(message) { current ->
            current.copy(
                peripheralState = PeripheralState.ADVERTISING,
                connectionState = ConnectionState.DISCONNECTED,
                connectedDeviceName = null,
                macSubscribed = false,
                isStale = true
            ).withDerivedActionState()
        }
    }

    fun onPeripheralPermissionRequired(message: String) {
        mutate(message) { current ->
            current.copy(
                peripheralState = PeripheralState.PERMISSION_REQUIRED,
                connectionState = ConnectionState.PERMISSION_BLOCKED,
                permissionState = PermissionState.DENIED,
                connectedDeviceName = null,
                macSubscribed = false
            ).withDerivedActionState()
        }
    }

    fun onPeripheralUnsupported(message: String) {
        mutate(message) { current ->
            current.copy(
                peripheralState = PeripheralState.UNSUPPORTED,
                connectionState = ConnectionState.DISCONNECTED,
                connectedDeviceName = null,
                macSubscribed = false
            ).withDerivedActionState()
        }
    }

    fun onPeripheralBluetoothOff(message: String) {
        mutate(message) { current ->
            current.copy(
                peripheralState = PeripheralState.BLUETOOTH_OFF,
                connectionState = ConnectionState.DISCONNECTED,
                connectedDeviceName = null,
                macSubscribed = false
            ).withDerivedActionState()
        }
    }

    fun onPeripheralError(message: String) {
        mutate(message) { current ->
            current.copy(
                peripheralState = PeripheralState.ERROR,
                connectionState = ConnectionState.DISCONNECTED
            ).withDerivedActionState()
        }
    }

    fun onIncomingCommand(command: IncomingCommand) {
        when (command) {
            is IncomingCommand.AgentFrame -> onAgentFrame(command)
            is IncomingCommand.WorkspaceFrame -> {
                mutate("收到工作区: ${command.workspaceName.orEmpty()}") { current ->
                    current.copy(workspaceName = command.workspaceName).withDerivedActionState()
                }
            }
            is IncomingCommand.MessagePreviewFrame -> onMessagePreview(command)
            is IncomingCommand.Brightness -> {
                mutate("亮度更新: ${command.percent}%") { current ->
                    current.copy(brightnessPercent = command.percent)
                }
            }
            is IncomingCommand.Orientation -> {
                mutate("方向更新: ${command.wireValue}") { current ->
                    current.copy(orientation = ScreenOrientation.fromWireValue(command.wireValue))
                }
            }
        }
    }

    fun enterDemoMode() {
        demoIndex = 0
        mutate("进入演示模式") { it.copy(isDemoMode = true) }
        advanceDemo()
    }

    fun exitDemoMode() {
        mutate("退出演示模式") { current ->
            current.copy(isDemoMode = false, displayMode = DisplayMode.STANDBY)
        }
    }

    fun cycleDemoState() {
        advanceDemo()
    }

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

    private fun onAgentFrame(command: IncomingCommand.AgentFrame) {
        val mascot = Mascot.fromWireId(command.mascotId)
        val status = AgentStatusCode.fromWireId(command.statusId)
        mutate("收到 Agent 帧: ${mascot.title} ${status.title}") { current ->
            current.copy(
                displayMode = DisplayMode.AGENT,
                mascot = mascot,
                agentStatus = status,
                source = mascot.title,
                status = status.toCompanionStatus(),
                toolName = command.toolName,
                pendingAction = status.pendingAction(),
                questionHeader = if (status == AgentStatusCode.WAITING_QUESTION) "Buddy 问题" else null,
                questionText = if (status == AgentStatusCode.WAITING_QUESTION) current.latestMessage else null,
                lastSyncEpochMs = System.currentTimeMillis(),
                isStale = false
            ).withDerivedActionState()
        }
    }

    private fun onMessagePreview(command: IncomingCommand.MessagePreviewFrame) {
        mutate("收到消息预览: ${command.index + 1}/${command.total}") { current ->
            val updatedMessages = if (command.total <= 0) {
                emptyList()
            } else {
                (current.messages.filter { it.index != command.index && it.index < command.total } +
                    command.text?.let {
                        if (command.index < command.total) {
                            MessagePreview(command.index, command.isUser, it)
                        } else {
                            null
                        }
                    }).filterNotNull()
                    .sortedBy { it.index }
            }
            val latest = updatedMessages.joinToString(separator = "") { it.text }
                .takeIf { it.isNotBlank() }
            current.copy(
                messages = updatedMessages,
                latestMessage = latest,
                questionText = if (current.agentStatus == AgentStatusCode.WAITING_QUESTION) latest else current.questionText
            ).withDerivedActionState()
        }
    }

    private fun advanceDemo() {
        val mascot = demoMascots[demoIndex % demoMascots.size]
        val status = demoStatuses[demoIndex % demoStatuses.size]
        demoIndex++
        val tool = listOf("Read", "Bash", "Edit", "Search", null)[demoIndex % 5]
        val workspace = listOf("CodeIsland", "app/src/main", "docs", null)[demoIndex % 4]
        val message = "演示模式 · ${status.title}"

        mutate(message) { current ->
            current.copy(
                isDemoMode = true,
                peripheralState = PeripheralState.CONNECTED,
                connectionState = ConnectionState.CONNECTED,
                displayMode = DisplayMode.AGENT,
                connectedDeviceName = "Demo Mac",
                macSubscribed = true,
                mascot = mascot,
                agentStatus = status,
                source = mascot.title,
                status = status.toCompanionStatus(),
                toolName = tool,
                workspaceName = workspace,
                latestMessage = message,
                pendingAction = status.pendingAction(),
                questionHeader = if (status == AgentStatusCode.WAITING_QUESTION) "示例问题" else null,
                questionText = if (status == AgentStatusCode.WAITING_QUESTION) "这是一个演示问题，请回到 Mac 处理" else null,
                sessions = demoMascots.take(3).mapIndexed { index, item ->
                    SessionCard(
                        sessionId = "demo-$index",
                        source = item.title,
                        status = demoStatuses[(demoIndex + index) % demoStatuses.size].toCompanionStatus(),
                        toolName = tool,
                        workspaceName = workspace,
                        message = "Demo session $index"
                    )
                }
            ).withDerivedActionState()
        }
    }

    private fun mutate(message: String? = null, reducer: (BuddyDashboardState) -> BuddyDashboardState) {
        var nextSnapshot: BuddyDashboardState? = null
        _dashboardState.update { current ->
            val nextVersion = current.localStateVersion + 1
            val next = reducer(current).copy(
                diagnosticMessage = message ?: current.diagnosticMessage,
                localStateVersion = nextVersion,
                lastDeliveredSequence = nextVersion
            )
            nextSnapshot = next
            next
        }
        message?.let { logDiagnostic("INFO", "buddy-repository", it) }
        nextSnapshot?.let { snapshot ->
            scope.launch {
                widgetSnapshotStore.save(snapshot)
                prefs.setLastDeliveredSequence(snapshot.lastDeliveredSequence)
                notificationController.evaluateAlert(snapshot, snapshot.lastDeliveredSequence)
            }
        }
    }

    private fun BuddyDashboardState.withDerivedActionState(): BuddyDashboardState {
        val connectedAndSubscribed = peripheralState == PeripheralState.CONNECTED && macSubscribed
        val approval = agentStatus == AgentStatusCode.WAITING_APPROVAL
        val question = agentStatus == AgentStatusCode.WAITING_QUESTION
        return copy(
            pendingAction = agentStatus.pendingAction(),
            canApprove = connectedAndSubscribed && approval,
            canDeny = connectedAndSubscribed && approval,
            canSkip = connectedAndSubscribed && question,
            canAnswerOnPhone = false,
            actionHint = if (connectedAndSubscribed) "可从手机发送到 Mac" else "请回到 Mac 处理",
            status = agentStatus.toCompanionStatus(),
            source = mascot.title
        )
    }

    private fun AgentStatusCode.toCompanionStatus(): CompanionStatus {
        return when (this) {
            AgentStatusCode.IDLE -> CompanionStatus.IDLE
            AgentStatusCode.PROCESSING -> CompanionStatus.PROCESSING
            AgentStatusCode.RUNNING -> CompanionStatus.RUNNING
            AgentStatusCode.WAITING_APPROVAL -> CompanionStatus.WAITING_APPROVAL
            AgentStatusCode.WAITING_QUESTION -> CompanionStatus.WAITING_QUESTION
        }
    }

    private fun AgentStatusCode.pendingAction(): PendingAction? {
        return when (this) {
            AgentStatusCode.WAITING_APPROVAL -> PendingAction.APPROVAL
            AgentStatusCode.WAITING_QUESTION -> PendingAction.QUESTION
            else -> null
        }
    }

    private fun connectionStateFor(state: PeripheralState): ConnectionState {
        return when (state) {
            PeripheralState.STARTING,
            PeripheralState.ADVERTISING -> ConnectionState.SCANNING
            PeripheralState.CONNECTED -> ConnectionState.CONNECTED
            PeripheralState.PERMISSION_REQUIRED -> ConnectionState.PERMISSION_BLOCKED
            PeripheralState.UNSUPPORTED,
            PeripheralState.BLUETOOTH_OFF,
            PeripheralState.ERROR -> ConnectionState.DISCONNECTED
        }
    }

    private suspend fun staleCheckLoop() {
        while (currentCoroutineContext().isActive) {
            delay(5_000)
            val state = _dashboardState.value
            if (state.isDemoMode) continue
            if (state.displayMode != DisplayMode.AGENT) continue
            if (state.lastSyncEpochMs == 0L) continue
            if (System.currentTimeMillis() - state.lastSyncEpochMs <= BleProtocol.firmwareInactivityTimeoutMs) continue

            mutate("60 秒未收到 Agent 帧，Buddy 返回待机") { current ->
                current.copy(
                    displayMode = DisplayMode.STANDBY,
                    agentStatus = AgentStatusCode.IDLE,
                    status = CompanionStatus.IDLE,
                    pendingAction = null,
                    canApprove = false,
                    canDeny = false,
                    canSkip = false,
                    isStale = true,
                    connectionState = ConnectionState.STALE
                )
            }
        }
    }
}

sealed interface OneShotEvent {
    data class FireAlert(val sequence: Long, val pendingAction: String) : OneShotEvent
    data object WidgetUpdate : OneShotEvent
}
