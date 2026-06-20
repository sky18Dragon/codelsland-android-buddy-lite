# specs-v2.md — BLE Peripheral Architecture

## 1. Protocol: Buddy/ESP32

### 1.1 Identifiers

| Property | Value |
|----------|-------|
| Service UUID | `0000beef-0000-1000-8000-00805f9b34fb` |
| Write Char UUID | `0000beef-0001-1000-8000-00805f9b34fb` |
| Notify Char UUID | `0000beef-0002-1000-8000-00805f9b34fb` |
| CCC Descriptor | `00002902-0000-1000-8000-00805f9b34fb` |
| Advertised Name | `Buddy` (default, or `Buddy-XXXXXX` for multi-device) |

### 1.2 Downlink Frames (Mac → Phone, Write characteristic)

| Frame | Format | Max Len |
|-------|--------|---------|
| Agent | `[sourceId:1B][statusId:1B][toolLen:1B][toolName:toolLen]` | tool 17B |
| Workspace | `[0xFC][len:1B][workspace:len]` | workspace 18B |
| Message Preview | `[0xFB][index:1B][total:1B][flagsLen:1B][text:len]` | text 16B |
| Brightness | `[0xFE][percent:1B]` | clamped [10,100] |
| Orientation | `[0xFD][wireValue:1B]` | 0=up, 1=down |

**Agent Frame**: sourceId from Mascot.wireId, statusId from AgentStatusCode.wireId
**Message Preview**: flagsLen bit7 = isUser, low7 = textLen
**Brightness**: default 70, clamped to [10, 100]
**Workspace/Message**: May arrive before Agent frame; repository must buffer.

### 1.3 Uplink Commands (Phone → Mac, Notify characteristic)

| Command | Byte(s) |
|---------|---------|
| Approve | `0xF0` |
| Deny | `0xF1` |
| Skip | `0xF2` |
| Focus | `[sourceId:1B]` |

**Precondition**: Mac must be connected AND subscribed to notify characteristic (CCC descriptor != 0x0000).
**Retry**: 2 attempts on GATT failure.
**Delivery Mode**: Support both notification (0x0100) and indication (0x0200).

### 1.4 Wire ID Mappings

**Mascot** (sourceId → name):
0=Claude, 1=Codex, 2=Gemini, 3=Cursor, 4=Copilot, 5=Trae, 6=Qoder, 7=Factory, 8=CodeBuddy, 9=StepFun, 10=OpenCode, 11=Qwen, 12=AntiGravity, 13=WorkBuddy, 14=Hermes, 15=Kimi

**AgentStatusCode** (statusId → status):
0=IDLE, 1=PROCESSING, 2=RUNNING, 3=WAITING_APPROVAL, 4=WAITING_QUESTION

### 1.5 Inactivity Timeout
- `firmwareInactivityTimeoutMs = 60_000` (60s without agent frame → return to STANDBY)
- Demo cycle: `8_000ms`

## 2. BLE Peripheral Specification

### 2.1 Peripheral State Machine

```
IDLE ──start()──► STARTING ──► ADVERTISING ──Mac connects──► CONNECTED
  ▲                   │              │                           │
  │        ┌──────────┘              │ Mac subscribed            │
  │        ▼                         ▼                           │
  │  PERMISSION_BLOCKED         CONNECTED ◄──────────────────────┘
  │        │                         │
  │        │ permission granted      │ inactivity 60s
  │        └────────────────────────►│
  │                                  ▼
  └─────────────────────────── STANDBY (keep advertising, show idle)
```

### 2.2 GATT Service

```kotlin
BluetoothGattService(SERVICE_UUID, SERVICE_TYPE_PRIMARY)
├── Write Characteristic: PROPERTY_WRITE | PROPERTY_WRITE_NO_RESPONSE
│   └── PERMISSION_WRITE
└── Notify Characteristic: PROPERTY_NOTIFY | PROPERTY_INDICATE
    └── CCC Descriptor: PERMISSION_READ | PERMISSION_WRITE
```

### 2.3 Advertising

- Mode: `ADVERTISE_MODE_LOW_LATENCY`
- TX Power: `ADVERTISE_TX_POWER_MEDIUM`
- Connectable: true
- Data: service UUID + device name
- Name: "Buddy" (default), or "Buddy-XXXXXX" (derived from device serial/hash)

### 2.4 Phone-specific Constraints

- `bluetoothLeAdvertiser` may be null → UNSUPPORTED state
- `isMultipleAdvertisementSupported` may be false → log warning, continue
- Android 12+: BLUETOOTH_ADVERTISE, BLUETOOTH_CONNECT permissions
- Android 13+: POST_NOTIFICATIONS for attention alerts
- HyperOS: foreground service + battery optimization exemption

## 3. Domain Model

### 3.1 Protocol Enums (from android-watch)

```kotlin
enum class DisplayMode { STANDBY, AGENT, DEMO }
enum class PeripheralState { STARTING, ADVERTISING, CONNECTED, PERMISSION_REQUIRED, UNSUPPORTED, BLUETOOTH_OFF, ERROR }
enum class Mascot(val wireId: Int, val title: String, val accentColor: Int)
enum class AgentStatusCode(val wireId: Int, val title: String)
```

### 3.2 Extended BuddyDashboardState

Add to existing state:
```kotlin
val peripheralState: PeripheralState = PeripheralState.STARTING
val displayMode: DisplayMode = DisplayMode.STANDBY
val mascot: Mascot = Mascot.CODEX
val agentStatus: AgentStatusCode = AgentStatusCode.IDLE
val messages: List<MessagePreview> = emptyList()
val brightnessPercent: Int = 70
// canApprove/canDeny/canSkip now DYNAMIC:
//   true when Mac subscribed + status is WAITING_APPROVAL/QUESTION
// canAnswerOnPhone stays false (no text answer in Buddy protocol)
```

### 3.3 IncomingCommand (renamed from android-watch)

```kotlin
sealed interface IncomingCommand {
    data class AgentFrame(val mascotId: Int, val statusId: Int, val toolName: String?)
    data class WorkspaceFrame(val workspaceName: String?)
    data class MessagePreviewFrame(val index: Int, val total: Int, val isUser: Boolean, val text: String?)
    data class Brightness(val percent: Int)
    data class Orientation(val wireValue: Int)
}
```

## 4. PBT Properties

### 4.1 Frame Parser Invariants

| ID | Invariant | Falsification |
|----|-----------|---------------|
| FP1 | Empty payload → null | Test with ByteArray(0) |
| FP2 | Agent frame with valid IDs → correct IncomingCommand | For all 16 mascots × 5 statuses |
| FP3 | Unknown mascot ID → CODEX (not crash) | Test with mascotId=0xFF |
| FP4 | Unknown status ID → IDLE (not crash) | Test with statusId=0xFF |
| FP5 | toolName > 17 bytes → clamped to 17 | Test with 100-byte tool name |
| FP6 | workspaceName > 18 bytes → clamped to 18 | Test with 50-byte workspace |
| FP7 | message text > 16 bytes → clamped to 16 | Test with 100-byte text |
| FP8 | Brightness out of [10,100] → clamped | Test with 0 and 255 |
| FP9 | UTF-8 decode failure → null string (not crash) | Test with invalid UTF-8 sequences |

### 4.2 State Invariants

| ID | Invariant | Falsification |
|----|-----------|---------------|
| FP10 | Agent frame resets inactivity timer | Advance clock 59s, send frame, assert still AGENT mode |
| FP11 | No agent frame for 60s → STANDBY | Advance clock 61s, assert displayMode=STANDBY |
| FP12 | canApprove=true iff peripheralState=CONNECTED AND status=WAITING_APPROVAL AND Mac subscribed |
| FP13 | Demo mode ignores incoming frames |
| FP14 | Message list drops slots with index >= total |

### 4.3 Uplink Invariants

| ID | Invariant | Falsification |
|----|-----------|---------------|
| UP1 | Uplink sent only when Mac connected AND subscribed | assert no notifyCharacteristicChanged call otherwise |
| UP2 | Uplink retries ≤ 2 on failure | assert max 3 total attempts |
| UP3 | Approve/Deny only for WAITING_APPROVAL status |

## 5. Resolved Decisions

| ID | Decision |
|----|----------|
| D1 | BuddyPeripheralService IS the foreground service (replaces BuddySyncService) |
| D2 | TransportLayer interface removed; events flow directly from BuddyPeripheralService → BuddyRepository |
| D3 | Package structure preserved; ble/ files swapped, protocol/ files replaced |
| D4 | Device name: "Buddy" for single device, "Buddy-XXXXXX" (6 hex chars from device serial hash) for multi-device |
| D5 | Only one Mac connection supported at a time; extra centrals rejected |
| D6 | Attention notification: BigTextStyle, approve/deny/skip/open actions via PendingIntent → Service |
| D7 | Notification dedupe: attention key = "${mascotId}:${statusId}:${toolName}:${messageHash}" |
