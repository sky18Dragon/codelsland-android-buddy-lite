# specs.md — CodeIsland Android Buddy Lite

## 1. Protocol Specification

### 1.1 BLE Identifiers

| Property | Value |
|----------|-------|
| Service UUID | `6D951BA3-8F41-4C45-9D8A-12085E0D7A10` |
| Notify Characteristic UUID | `25C1B67B-E903-4A0C-8A78-3EE8AB7317B7` |
| Advertisement Local Name | `CodeIsland` |
| MTU Target | 517 bytes (fallback 185, minimum 135) |

### 1.2 CI Chunk Format

```
Offset  Size  Field       Encoding
0       3     Magic       0x43 0x49 0x01 ("CI" + version 1)
3       8     Sequence    UInt64 Big-Endian
11      2     Index       UInt16 Big-Endian
13      2     Total       UInt16 Big-Endian
15      N     Body        UTF-8 JSON fragment
```

**Validation Rules:**
- Total size MUST be >= 15 bytes
- Magic bytes MUST match exactly
- Total MUST be in range [1, 64]
- Index MUST be in range [0, total-1]
- Aggregate body across all chunks MUST <= 7680 bytes

### 1.3 JSON Summary Schema

```kotlin
@Serializable
data class CompanionBluetoothSummary(
    val version: Int,
    val sequence: Long,                    // UInt64, monotonic
    val sessionId: String? = null,         // max 96 chars
    val source: String,                    // e.g. "codex", "claude"
    val status: String,                    // idle|processing|running|waitingApproval|waitingQuestion
    val toolName: String? = null,          // max 64 chars
    val workspaceName: String? = null,     // max 64 chars
    val message: String? = null,           // max 220 chars, last assistant message
    val pendingAction: String? = null,     // approval|question
    val questionHeader: String? = null,    // max 40 chars
    val questionText: String? = null,      // max 180 chars
    val sessions: List<SessionSummary>? = null, // max 5 entries
    val updatedAt: String                  // ISO 8601
)

@Serializable
data class SessionSummary(
    val sessionId: String? = null,
    val source: String,
    val status: String,
    val toolName: String? = null,          // max 48 chars
    val workspaceName: String? = null,     // max 48 chars
    val message: String? = null,           // max 120 chars
    val updatedAt: String
)
```

- **Date format**: ISO 8601 (e.g. `2026-06-20T10:30:00Z`)
- **Unknown fields**: MUST be silently ignored (forward compatibility)
- **Unknown status values**: MUST map to `idle` with diagnostic log

## 2. BLE Transport Specification

### 2.1 Connection State Machine

```
                    ┌──────────┐
                    │   Idle   │
                    └────┬─────┘
                         │ start()
              ┌──────────┴──────────┐
              ▼                     ▼
     ┌────────────────┐   ┌──────────────────┐
     │ Permission     │   │    Scanning      │◄──────────┐
     │ Blocked        │   │ (BLE scan on)    │           │
     └────────┬───────┘   └────────┬─────────┘           │
              │                    │ discovered            │
              │                    ▼                       │
              │           ┌───────────────┐               │
              │           │  Connecting   │               │
              │           └───────┬───────┘               │
              │                   │ connected              │
              │                   ▼                       │
              │           ┌──────────────────┐           │
              │           │ Discovering      │           │
              │           │ Services         │           │
              │           └────────┬─────────┘           │
              │                    │ services found        │
              │                    ▼                       │
              │           ┌──────────────────┐           │
              │           │ Requesting MTU   │           │
              │           └────────┬─────────┘           │
              │                    │ MTU ok                │
              │                    ▼                       │
              │           ┌──────────────────┐           │
              │           │ Subscribing      │           │
              │           │ (write CCCD)     │           │
              │           └────────┬─────────┘           │
              │                    │ subscribed            │
              │                    ▼                       │
              │           ┌──────────────────┐           │
              │           │   Connected      │───────────►│
              │           │ (receiving data) │ disconnect  │
              │           └────────┬─────────┘           │
              │                    │ stale (30s)           │
              │                    ▼                       │
              │           ┌──────────────────┐           │
              │           │   Stale         │───────────►│
              │           │ (no data 30s)   │ disconnect  │
              │           └────────┬─────────┘           │
              │                    │ no data 60s           │
              │                    ▼                       │
              │           ┌──────────────────┐           │
              └───────────│ Disconnected     │───────────┘
                          └──────────────────┘
```

### 2.2 GATT Operation Queue

All GATT actions MUST be serialized through a Mutex-protected channel:

```kotlin
sealed interface GattOperation {
    data class DiscoverServices(val services: List<UUID>) : GattOperation
    data class RequestMtu(val mtu: Int) : GattOperation
    data class SetCharacteristicNotification(val characteristic: UUID, val enable: Boolean) : GattOperation
    data class WriteDescriptor(val descriptor: UUID, val value: ByteArray) : GattOperation
}
```

- Each operation has a **5-second timeout**
- On Any operation timeout → disconnect + retry
- `BluetoothGatt` closed on main thread before new connection

### 2.3 Chunk Reassembler

```kotlin
class CiChunkReassembler(
    private val maxAggregateBytes: Int = 7680,
    private val timeoutMs: Long = 5_000
) {
    // Keyed by sequence
    private var currentSequence: Long = -1
    private var currentTotal: Int = 0
    private val chunks = mutableMapOf<Int, ByteArray>()
    private var lastChunkTime: Long = 0

    fun accept(chunk: CiChunk): ReassemblyResult {
        // Reject older sequences
        if (chunk.sequence < currentSequence) return Rejected.Stale
        // Newer sequence → abandon old
        if (chunk.sequence > currentSequence) reset(chunk.sequence, chunk.total)
        // Inconsistent total → corrupt
        if (chunk.total != currentTotal) return Rejected.Corrupt
        // Duplicate same body → ignore; different body → corrupt
        if (chunks.containsKey(chunk.index)) {
            return if (chunks[chunk.index].contentEquals(chunk.body)) Rejected.Duplicate
            else Rejected.Corrupt
        }
        chunks[chunk.index] = chunk.body
        lastChunkTime = SystemClock.elapsedRealtime()
        if (chunks.size == currentTotal) {
            val combined = combine()
            reset()
            return ReassemblyResult.Complete(combined)
        }
        return ReassemblyResult.Pending
    }
}
```

### 2.4 Reconnect Policy

- On disconnect: jittered backoff 1s → 2s → 4s → 8s → max 30s
- On Bluetooth OFF → ON: immediate restart scan
- On app killed: START_STICKY for service restart; last snapshot visible on cold start

## 3. Data Model Specification

### 3.1 Domain State

```kotlin
data class BuddyDashboardState(
    // Connection
    val connectionState: ConnectionState = ConnectionState.Idle,
    val connectedDeviceName: String? = null,
    val lastSyncTime: Instant? = null,
    val isStale: Boolean = false,

    // Current agent (from latest BLE summary)
    val source: String = "",
    val status: CompanionStatus = CompanionStatus.IDLE,
    val toolName: String? = null,
    val workspaceName: String? = null,
    val latestMessage: String? = null,

    // Pending action
    val pendingAction: PendingAction? = null,
    val questionHeader: String? = null,
    val questionText: String? = null,

    // Sessions
    val sessions: List<SessionCard> = emptyList(),

    // Capability flags (all false for Android Lite)
    val canApprove: Boolean = false,
    val canDeny: Boolean = false,
    val canSkip: Boolean = false,
    val canAnswerOnPhone: Boolean = false,
    val actionHint: String = "请回到 Mac 处理",

    // Demo mode
    val isDemoMode: Boolean = false,

    // Diagnostics
    val diagnosticMessage: String? = null,
    val permissionState: PermissionState = PermissionState.UNKNOWN,

    // Widget snapshot
    val widgetSnapshot: WidgetSnapshot? = null
)

data class SessionCard(
    val sessionId: String?,
    val source: String,
    val status: CompanionStatus,
    val toolName: String?,
    val workspaceName: String?,
    val message: String?
)

enum class CompanionStatus(val label: String, val shortLabel: String) {
    IDLE("空闲", "空闲"),
    PROCESSING("处理中", "处理"),
    RUNNING("运行中", "运行"),
    WAITING_APPROVAL("等待批准", "批准"),
    WAITING_QUESTION("等待回答", "问题");

    companion object {
        fun fromString(value: String): CompanionStatus =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: IDLE
    }
}

enum class ConnectionState {
    Idle, PermissionBlocked, Scanning, Connecting,
    DiscoveringServices, RequestingMtu, Subscribing, Connected,
    Stale, Disconnected
}
```

### 3.2 Room Entities

```kotlin
@Entity(tableName = "device_preferences")
data class DevicePreference(
    @PrimaryKey val id: Int = 1,
    val lastConnectedDeviceAddress: String?,
    val lastConnectedDeviceName: String?
)

@Entity(tableName = "dashboard_snapshots")
data class DashboardSnapshot(
    @PrimaryKey val id: Int = 1,
    val jsonPayload: String,      // serialized BuddyDashboardState
    val updatedAt: Long
)

@Entity(tableName = "notification_log")
data class NotificationLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sequence: Long,
    val pendingAction: String,
    val notifiedAt: Long
)

@Entity(tableName = "diagnostic_events")
data class DiagnosticEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val level: String,            // DEBUG, INFO, WARN, ERROR
    val tag: String,
    val message: String
)
```

### 3.3 Transport Events (Internal)

```kotlin
sealed interface TransportEvent {
    data object Scanning : TransportEvent
    data class Discovered(val name: String, val address: String) : TransportEvent
    data object Connecting : TransportEvent
    data object Connected : TransportEvent
    data class MtuNegotiated(val mtu: Int) : TransportEvent
    data object Subscribed : TransportEvent
    data class SummaryReceived(val summary: CompanionBluetoothSummary) : TransportEvent
    data class Stale(val secondsSinceLastSummary: Int) : TransportEvent
    data object Disconnected : TransportEvent
    data class Error(val message: String, val cause: Throwable? = null) : TransportEvent
    data object BluetoothOff : TransportEvent
    data object BluetoothOn : TransportEvent
    data class PermissionDenied(val permissions: List<String>) : TransportEvent
}
```

## 4. Notification Specification

### 4.1 Channels

| Channel ID | Name | Importance | Usage |
|------------|------|-----------|-------|
| `buddy_sync` | 同步服务 | LOW | 前台服务常驻通知 |
| `buddy_alert` | 状态提醒 | HIGH | waitingApproval / waitingQuestion |

### 4.2 Alert Rules

- **Fire when**: `pendingAction == "approval"` OR `pendingAction == "question"`
- **Dedupe**: Only fire if `summary.sequence > lastNotifiedSequence`
- **Repeat**: Not before 5 minutes since last alert for same sequence
- **Click**: Opens MainActivity → Dashboard
- **Content**:
  - Title: `[source] 需要你的注意`
  - Body: `pendingAction=approval → "等待批准: [message前80字]"` / `pendingAction=question → "等待回答: [questionText前80字]"`
  - Footer: `请回到 Mac 处理`

## 5. PBT Properties

### 5.1 Protocol Invariants

| ID | Invariant | Falsification Strategy |
|----|-----------|----------------------|
| P1 | Chunk parser MUST return null for any byte array < 15 bytes | Generate all byte arrays of length 0..14, assert all return null |
| P2 | Chunk parser MUST reject magic != [0x43, 0x49, 0x01] | For each byte position 0-2, substitute all 255 other values, assert null |
| P3 | Chunk parser MUST reject total ∉ [1,64] | Generate totals 0, 65..65535, assert null |
| P4 | Chunk parser MUST reject index >= total | For each (total, index) where index ≥ total, assert null |
| P5 | Reassembler deliver order MUST = index order regardless of arrival order | Shuffle chunk arrival order, assert combined bytes = original |
| P6 | Reassembler MUST abandon old sequence when newer arrives | Send partial seq N, then seq N+1, assert seq N data discarded |
| P7 | Reassembler MUST reject chunks with inconsistent total for same sequence | Send chunk with total=3, then chunk with total=4 same seq, assert corrupt |

### 5.2 Data Invariants

| ID | Invariant | Falsification Strategy |
|----|-----------|----------------------|
| P8 | JSON decode of ISO 8601 dates MUST be lossless round-trip | Encode Instant→String in Kotlin, assert matches Swift encoder output |
| P9 | Unknown status values MUST map to IDLE without crashing | Decode JSON with status="unknownFutureValue", assert state.status == IDLE |
| P10 | ProtocolCompatLayer MUST set all can* flags to false | Assert canApprove==false, canDeny==false, etc. for any valid input summary |
| P11 | sessions list of null or empty MUST produce emptyList in domain state | Decode summary with sessions=null and sessions=[], both assert sessions.isEmpty() |

### 5.3 State Machine Invariants

| ID | Invariant | Falsification Strategy |
|----|-----------|----------------------|
| P12 | Connection state transitions MUST follow valid DAG | Generate all illegal transitions (e.g., Idle→Connected without scan), assert rejected |
| P13 | Stale MUST transition to Disconnected after 60s without data | Advance clock 61s past lastSummaryAt while state==Connected, assert state==Disconnected |
| P14 | Demo mode state MUST be self-consistent: isDemoMode → can* = false | Assert demo states always have actionHint pointing to Mac |

### 5.4 Notification Invariants

| ID | Invariant | Falsification Strategy |
|----|-----------|----------------------|
| P15 | Alert MUST NOT fire twice for same sequence | Apply same summary twice, assert only one notification triggered |
| P16 | waitingApproval/Question alert MUST fire within 500ms of state update | Measure time from state update to notification post |
| P17 | Non-waiting status MUST NOT trigger alert | Apply summary with status=idle/processing/running, assert no alert |

## 6. Resolved Constraints (from Uncertainty Audit)

| # | Decision | Rationale |
|---|----------|-----------|
| D1 | MTU target 517, fallback 185, minimum 135 | 135 bytes = 15 header + 120 payload. 517 handles 4 chunks in one notification |
| D2 | GATT operations serialized via Mutex + Channel | Prevents Android BLE stack race conditions |
| D3 | BLE state machine: 10 explicit states | Matches Android BLE callback lifecycle |
| D4 | Stale at 30s, offline at 60s no summary | User confirmed |
| D5 | Auto-connect to first discovered Mac | User confirmed; simpler UX for single-Mac |
| D6 | Demo mode overrides BLE data | User confirmed; explicit opt-in/out |
| D7 | TransportLayer interface only (no DevicePluginRegistry) | User confirmed; YAGNI |
| D8 | Room for diagnostics + snapshots; DataStore for preferences | Room handles structured queries; DataStore simpler for key-value |
| D9 | Notification dedupe: lastNotifiedSequence persisted in Room | Survives process restart |
| D10 | Scan stops while connected; resumes on disconnect | Power saving; single-Mac assumption |
| D11 | lastDeliveredSequence persisted for cold start dedupe | Prevents stale replay after restart |
| D12 | minSdk 29: handle ACCESS_FINE_LOCATION for Android 10/11 | Required for BLE scanning on older API levels |

## 7. Test Fixture Specification

### 7.1 Reference JSON Payloads

Test fixtures MUST match the exact JSON output of Swift `AppleCompanionBluetoothPeripheral.makeChunks()`.

```json
{
  "version": 1,
  "sequence": 42,
  "sessionId": "abc123def456",
  "source": "codex",
  "status": "waitingQuestion",
  "toolName": "WebSearch",
  "workspaceName": "repo/CodeIsland",
  "message": "正在搜索相关文档，找到了3个可能匹配的结果需要你确认...",
  "pendingAction": "question",
  "questionHeader": "选择搜索范围",
  "questionText": "你想搜索哪个目录下的文件？",
  "sessions": [
    {
      "sessionId": "abc123",
      "source": "codex",
      "status": "waitingQuestion",
      "toolName": "WebSearch",
      "workspaceName": "repo/CodeIsland",
      "message": "搜索中...",
      "updatedAt": "2026-06-20T10:30:00Z"
    },
    {
      "sessionId": "def456",
      "source": "claude",
      "status": "idle",
      "toolName": null,
      "workspaceName": "docs/",
      "message": null,
      "updatedAt": "2026-06-20T10:25:00Z"
    }
  ],
  "updatedAt": "2026-06-20T10:30:00Z"
}
```

### 7.2 Chunk Fixtures

Test that a JSON payload of length L produces `ceil(L / 120)` chunks, each with:
- Correct magic bytes
- Monotonically increasing index
- Correct total
- Bodies that concatenate to original JSON
