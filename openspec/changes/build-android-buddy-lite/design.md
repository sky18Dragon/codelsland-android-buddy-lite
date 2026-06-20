# design.md — CodeIsland Android Buddy Lite

## 1. System Architecture

### 1.1 Layered Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    UI Layer (Compose)                     │
│  Dashboard │ Discovery │ Demo │ Diagnostics │ Widget     │
├─────────────────────────────────────────────────────────┤
│                   State Management                        │
│           BuddyRepository (StateFlow + SharedFlow)        │
├─────────────────────────────────────────────────────────┤
│                  Protocol Compat Layer                    │
│     CompanionBluetoothSummary → BuddyDashboardState      │
├─────────────────────────────────────────────────────────┤
│                   BLE Transport Layer                     │
│  Scanner │ Connector │ GattQueue │ ChunkReassembler     │
├─────────────────────────────────────────────────────────┤
│                   Platform Services                       │
│  ForegroundService │ Notifications │ Room │ DataStore    │
└─────────────────────────────────────────────────────────┘
```

### 1.2 Package Structure

```
app/src/main/java/com/codeisland/buddylite/
├── BuddyLiteApp.kt                    # Application class
├── MainActivity.kt                     # Single Activity, Compose host
│
├── ble/                                # BLE Transport
│   ├── BleTransport.kt                 # TransportLayer impl, owns BLE lifecycle
│   ├── BleStateMachine.kt              # Connection state transitions
│   ├── GattOperationQueue.kt           # Serialized GATT operation executor
│   ├── CiChunkParser.kt                # Pure function: ByteArray → CiChunk?
│   ├── CiChunkReassembler.kt           # Stateful reassembler: chunks → ByteArray
│   └── BleDiagnostics.kt              # BLE event logger
│
├── protocol/                           # Protocol mapping (pure functions)
│   ├── CompanionBluetoothSummary.kt     # @Serializable JSON model
│   ├── ProtocolMapper.kt               # Summary → DomainState mapping
│   └── TransportEvent.kt               # Sealed interface for transport events
│
├── data/                               # Repository + persistence
│   ├── BuddyRepository.kt              # Central state: StateFlow + event dispatch
│   ├── NotificationController.kt       # Alert dedupe + channel management
│   ├── WidgetSnapshotProvider.kt       # Glance data source
│   ├── local/                          # Room + DataStore
│   │   ├── BuddyDatabase.kt            # Room database
│   │   ├── DashboardSnapshotDao.kt     # Snapshot CRUD
│   │   ├── DiagnosticEventDao.kt       # Event log CRUD (bounded retention)
│   │   ├── NotificationLogDao.kt       # Sequence dedupe records
│   │   └── PreferencesDataStore.kt     # DataStore for user prefs
│   └── model/                          # Domain models
│       ├── BuddyDashboardState.kt
│       ├── CompanionStatus.kt
│       ├── ConnectionState.kt
│       └── SessionCard.kt
│
├── ui/                                 # Compose UI
│   ├── theme/
│   │   ├── Theme.kt                    # Dark-only Material3 theme
│   │   ├── Color.kt                    # Color tokens
│   │   └── Type.kt                     # Typography
│   ├── navigation/
│   │   └── BuddyNavGraph.kt            # NavHost + route definitions
│   ├── dashboard/
│   │   ├── DashboardScreen.kt          # Main state card + sessions
│   │   ├── StatusHeader.kt             # Source name + status pill
│   │   ├── SessionList.kt              # Multi-session list
│   │   └── QuestionCard.kt             # Read-only question summary
│   ├── discovery/
│   │   ├── DiscoveryScreen.kt          # Scanning/searching UI
│   │   └── ConnectionDot.kt            # Animated connection indicator
│   ├── demo/
│   │   └── DemoController.kt           # Demo mode state cycler
│   ├── diagnostics/
│   │   └── DiagnosticsScreen.kt        # Permissions, BLE state, logs
│   └── components/
│       ├── PulseDot.kt                 # Animated status indicator
│       ├── StatusPill.kt               # Status badge (capsule)
│       ├── BuddyButton.kt              # Primary/secondary action button
│       ├── MascotIcon.kt              # Mascot display (emoji-based)
│       └── MetadataChip.kt            # Tool/workspace label
│
├── service/
│   └── BuddySyncService.kt             # connectedDevice foreground service
│
├── widget/
│   └── BuddyGlanceWidget.kt            # Glance App Widget
│
└── di/                                 # Dependency injection (manual)
    └── ServiceLocator.kt               # Simple DI container
```

## 2. Component Design

### 2.1 BleTransport

```
BleTransport (implements TransportLayer)
├── Input: start() / stop()
├── Internal: BleStateMachine, GattOperationQueue, CiChunkReassembler
├── Output: Flow<TransportEvent>
│
├── Dependencies:
│   ├── BluetoothAdapter (system)
│   ├── BluetoothLeScanner (system)
│   └── CiChunkParser (pure)
│
├── Lifecycle:
│   ├── Owned by BuddySyncService CoroutineScope
│   ├── start() → initiate BLE scan
│   ├── stop() → close GATT, cancel scan
│   └── Destroyed with service
```

**Design decisions:**
- Single `callbackFlow` for BluetoothLeScanner scan results → `Discovered` events
- Single `callbackFlow` for BluetoothGattCallback → raw notification bytes
- Internal actor (`Channel<GattOperation>`) for serialized GATT commands
- Connection state transitions logged to BleDiagnostics
- MTU negotiated BEFORE characteristic subscription

### 2.2 GattOperationQueue

```
GattOperationQueue
├── Channel<GattOperation> (capacity=16, conflated)
├── Mutex for BluetoothGatt access
├── Per-operation timeout: 5 seconds
│
├── Sequence:
│   1. discoverServices([6D951BA3...])
│   2. requestMtu(517)
│   3. setCharacteristicNotification(25C1B67B..., enable=true)
│   4. writeDescriptor(CCCD, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
│
├── Error handling:
│   ├── Timeout → closeGatt() → emit Error → reconnect
│   ├── GATT_FAILURE → log status → conditional retry
│   └── All failures surfaced as TransportEvent.Error
```

### 2.3 BuddyRepository (State Reducer)

```kotlin
class BuddyRepository(
    private val transportLayer: TransportLayer,
    private val database: BuddyDatabase,
    private val prefs: PreferencesDataStore,
    private val notificationController: NotificationController
) {
    private val _dashboardState = MutableStateFlow(BuddyDashboardState())
    val dashboardState: StateFlow<BuddyDashboardState> = _dashboardState.asStateFlow()

    private val _events = MutableSharedFlow<OneShotEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<OneShotEvent> = _events.asSharedFlow()

    // Internal: timer-based stale detection
    private val staleCheckJob: Job

    init {
        // Collect transport events, map to state updates
        // Clock tick → check staleness
        // Persist snapshots on change
    }

    fun startSync() { transportLayer.start() }
    fun stopSync() { transportLayer.stop() }
    fun enterDemoMode()
    fun exitDemoMode()
    fun cycleDemoState()
}

sealed interface OneShotEvent {
    data class FireAlert(val sequence: Long, val action: String) : OneShotEvent
    data class UpdateWidget(val snapshot: WidgetSnapshot) : OneShotEvent
    data class DiagnosticWarning(val message: String) : OneShotEvent
}
```

**State update rules:**
1. TransportEvent → always update `connectionState` and `lastSyncTime`
2. SummaryReceived → update entire domain state; reset stale timer
3. Stale → set `isStale = true`; keep last snapshot visible
4. Disconnected → clear `connectedDeviceName`; keep last snapshot as stale
5. Demo mode → overlay demo state; ignore transport events
6. Permission denial → set `permissionState`; do NOT attempt BLE operations

### 2.4 NotificationController

```kotlin
class NotificationController(
    private val context: Context,
    private val notificationLogDao: NotificationLogDao
) {
    private val lastNotifiedSequence: AtomicLong

    suspend fun evaluateAlert(state: BuddyDashboardState, sequence: Long) {
        if (state.isDemoMode) return
        if (sequence <= lastNotifiedSequence.get()) return
        val action = state.pendingAction ?: return
        if (action !in setOf("approval", "question")) return

        // Check repeat cooldown (5 min per sequence)
        val lastLog = notificationLogDao.getLastForSequence(sequence)
        if (lastLog != null &&
            System.currentTimeMillis() - lastLog.notifiedAt < 300_000) return

        postAlert(state, sequence, action)
        lastNotifiedSequence.set(sequence)
        notificationLogDao.insert(NotificationLog(sequence = sequence, ...))
    }
}
```

## 3. Data Flow Diagrams

### 3.1 Happy Path: BLE Summary → Dashboard

```
Mac BLE Peripheral
    │ notify(chunk[0..N])
    ▼
BleTransport.callbackFlow
    │ raw bytes
    ▼
CiChunkParser.parse(data)
    │ CiChunk?
    ▼
CiChunkReassembler.accept(chunk)
    │ ReassemblyResult.Complete(body)
    ▼
Json.decodeFromString<CompanionBluetoothSummary>(body)
    │ CompanionBluetoothSummary
    ▼
ProtocolMapper.toDomainState(summary)
    │ BuddyDashboardState
    ▼
BuddyRepository._dashboardState.update { ... }
    │
    ├──► Compose UI (DashboardScreen collects StateFlow)
    ├──► NotificationController.evaluateAlert(state)
    └──► WidgetSnapshotProvider.update(state)
```

### 3.2 Reconnect Flow

```
Connection lost → TransportEvent.Disconnected
    │
    ▼
BuddyRepository:
  ├── connectionState = Disconnected
  ├── isStale = true
  └── keep last snapshot
    │
    ▼ (after jittered backoff: 1s, 2s, 4s, ...)
BleTransport.start() → Scan → Connect → Subscribe
    │
    ▼
Mac sends latest chunks on subscribe → SummaryReceived
    │
    ▼
BuddyRepository:
  ├── connectionState = Connected
  ├── isStale = false
  ├── update snapshot
  └── NotificationController: dedupe by sequence
```

### 3.3 Demo Mode Flow

```
User taps "进入演示模式"
    │
    ▼
BuddyRepository.enterDemoMode():
  ├── isDemoMode = true
  ├── connectionState frozen at last known
  ├── source/status/tool cycled from demo dataset
  └── Transport: NOT stopped (still scanning if was connected)
    │
    ▼
User taps "切换演示状态" → cycleDemoState()
    │
    ├── Source: codex → claude → gemini → copilot → ...
    └── Status: idle → processing → running → waitingApproval → waitingQuestion → idle
    │
    ▼
User taps "退出演示" → exitDemoMode()
    │
    ├── isDemoMode = false
    └── if connected: restore from latest real summary
    └── if not connected: return to discovery
```

## 4. UI Design

### 4.1 Navigation Graph

```
NavHost(startDestination = "dashboard")
├── "dashboard"         → DashboardScreen
│   ├── (connected)     → StatusHeader + SessionList + QuestionCard
│   └── (disconnected)  → DiscoveryScreen
├── "diagnostics"       → DiagnosticsScreen
└── "demo" (bottom sheet/overlay, not a route)
```

### 4.2 Screen States

**DashboardScreen** has 5 visual states:

| State | Connection | Content |
|-------|-----------|---------|
| Searching | Scanning | "正在搜索附近的 CodeIsland..." + spinner |
| Connected (idle/processing/running) | Connected | Status header + message + sessions |
| Connected (waiting) | Connected | Status header + question card + alert pill |
| Stale | Connected but no data >30s | Status header with "⚠ 数据延迟" + last snapshot |
| Offline | Disconnected | "Mac 未连接" + last sync time + reconnect button + demo entry |

### 4.3 Design Tokens

```kotlin
// Color tokens
object BuddyColors {
    val Background = Color(0xFF040406)        // #040406 - deep dark
    val Surface = Color(0xFF0D0D12)           // Card background
    val SurfaceVariant = Color(0xFF1A1A24)    // Elevated surface
    val OnSurface = Color.White               // Primary text
    val OnSurfaceDim = Color.White.copy(alpha = 0.58f)  // Secondary text
    val OnSurfaceFaint = Color.White.copy(alpha = 0.42f) // Tertiary text

    // Status colors
    val StatusIdle = Color(0xFF8C96A8)        // Gray
    val StatusActive = Color(0xFF4DD964)      // Green (processing/running)
    val StatusAttention = Color(0xFFFF8C00)   // Orange (waitingApproval)
    val StatusQuestion = Color(0xFF409CFF)    // Blue (waitingQuestion)

    // Accent
    val AccentGreen = Color(0xFF59E68B)
    val AccentBlue = Color(0xFF409CFF)
    val AccentOrange = Color(0xFFFF8C00)

    // Divider
    val Divider = Color.White.copy(alpha = 0.10f)
}

// Typography
// Fonts: system default (sans-serif)
// Display: 15sp Bold (source name)
// Headline: 16sp Medium (primary message)
// Body: 13sp Medium (secondary info)
// Caption: 12sp Bold (labels, chips)
// Monospaced: 12sp (diagnostic text)

// Shapes
// Large cards: RoundedCornerShape(18.dp)
// Small cards/chips: RoundedCornerShape(8.dp)
// Capsules (pills): 50% height radius or RoundedCornerShape(50)
// Buttons: RoundedCornerShape(8.dp)
```

### 4.4 Dashboard Layout (ASCII Wireframe)

```
┌─────────────────────────────────────────┐
│ [Mascot] SOURCE              [StatusPill]│  ← StatusHeader
│         workspace · tool                │
├─────────────────────────────────────────┤
│ "latest message preview text..."        │  ← PrimaryMessage
├─────────────────────────────────────────┤
│ ? 问题标题                        1/3   │  ← QuestionCard (conditional)
│ 问题内容文本...                        │
│ [请回到 Mac 回答]                       │
├─────────────────────────────────────────┤
│ 会话                                    │
│ ● codex      processing   repo/...      │  ← SessionList
│ ● claude     idle                      │
│ ● gemini     waitingQuestion           │
├─────────────────────────────────────────┤
│ [进入演示模式]           [诊断] [重连]   │  ← Action Bar
└─────────────────────────────────────────┘
```

## 5. Service & Process Architecture

### 5.1 Foreground Service

```xml
<!-- AndroidManifest.xml -->
<service
    android:name=".service.BuddySyncService"
    android:foregroundServiceType="connectedDevice"
    android:exported="false"
    android:stopWithTask="false" />
```

```kotlin
class BuddySyncService : Service() {
    // Lifecycle
    override fun onStartCommand(): Int {
        startForeground(NOTIFICATION_ID, buildSyncNotification())
        repository.startSync()
        return START_STICKY
    }

    override fun onDestroy() {
        repository.stopSync()
    }
}
```

### 5.2 Android Version Permissions Matrix

| Android | Permissions Required |
|---------|---------------------|
| 10-11 (API 29-30) | BLUETOOTH, BLUETOOTH_ADMIN, ACCESS_FINE_LOCATION |
| 12 (API 31) | BLUETOOTH_SCAN, BLUETOOTH_CONNECT, ACCESS_FINE_LOCATION |
| 13+ (API 33+) | BLUETOOTH_SCAN, BLUETOOTH_CONNECT, POST_NOTIFICATIONS |
| 14+ (API 34+) | Above + foregroundServiceType="connectedDevice" |

## 6. Widget Design

### 6.1 Glance Widget Layout

```
┌─────────────────────────────────────────┐
│ CodeIsland Buddy                         │
│ [●] codex · processing                  │
│ workspace: repo/CodeIsland               │
│ tool: WebSearch                         │
│ 2 个会话 · 1 个活跃                      │
│ 更新于 10:30                             │
└─────────────────────────────────────────┘
```

- Update frequency: on each new summary (via WorkManager expedited work)
- Size: 4x1 (primary) and 2x2 (compact)
- Click target: opens MainActivity → Dashboard

## 7. Diagnostics Design

### 7.1 Diagnostics Screen Sections

1. **蓝牙状态**: BLE adapter state, scanning state, connection state
2. **权限状态**: BLUETOOTH_SCAN ✓/✗, POST_NOTIFICATIONS ✓/✗, foreground service running ✓/✗
3. **最近同步**: last summary time, sequence number, status, source
4. **GATT 事件日志**: last 20 events (timestamp + event type + detail)
5. **HyperOS 引导**: Background autostart link, Battery Saver link, app lock suggestion
6. **操作**: "验证权限" / "重新搜索" / "导出诊断" / "忘记已连接设备"

### 7.2 Diagnostic Event Retention

- Max 200 events in memory/Room
- FIFO eviction (oldest deleted when limit reached)
- Export as JSON string (share intent)
- Privacy: NO session message content in diagnostic logs (only metadata)
