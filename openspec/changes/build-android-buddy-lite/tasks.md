# tasks.md — CodeIsland Android Buddy Lite

## Phase 1: Project Setup & Protocol Foundation

### 1.1 Project Scaffold
- [x] 1.1.1 Create Gradle project with Kotlin DSL (AGP 8.5.2, Kotlin 1.9.24, targetSdk 36/Android 16, minSdk 29, compileSdk 36)
- [x] 1.1.2 Configure build types (debug/release), Java 17, Compose compiler, kotlinx.serialization plugin
- [x] 1.1.3 Add dependencies: Compose BOM, Material 3, Coroutines, kotlinx.serialization, Room, Glance, Lifecycle
- [x] 1.1.4 Configure AndroidManifest.xml with permissions: BLUETOOTH_SCAN, BLUETOOTH_CONNECT, ACCESS_FINE_LOCATION (maxSdkVersion=30), POST_NOTIFICATIONS, FOREGROUND_SERVICE_CONNECTED_DEVICE
- [x] 1.1.5 Create package structure: ble/, protocol/, data/, ui/, service/, widget/
- [x] 1.1.6 Set up Room database with initial entities (DashboardSnapshot, DiagnosticEvent, NotificationLog)
- [x] 1.1.7 Set up DataStore for user preferences (selected device, demo prefs)
- [x] 1.1.8 Create ServiceLocator for manual DI

### 1.2 Protocol Models & JSON Decoder
- [x] 1.2.1 Write `CompanionBluetoothSummary` @Serializable data class (specs.md §1.3)
- [x] 1.2.2 Write `SessionSummary` @Serializable data class
- [x] 1.2.3 Implement ISO 8601 date parsing (Instant serializer)
- [x] 1.2.4 Implement unknown status enum fallback (unknown → IDLE)
- [x] 1.2.5 Write unit tests: valid JSON decode, missing optional fields, sessions=null/empty, ISO-8601 round-trip, unknown status → IDLE (P8, P9, P11)

### 1.3 CI Chunk Parser
- [x] 1.3.1 Write `CiChunkParser.parse(ByteArray): CiChunk?` with magic/version/sequence/index/total validation (specs.md §1.2)
- [x] 1.3.2 Handle Big-Endian UInt64/UInt16 parsing via ByteBuffer
- [x] 1.3.3 Write `CiChunkReassembler` with sequence tracking, out-of-order assembly, total validation, timeout expiry (specs.md §2.3)
- [x] 1.3.4 Write unit tests: valid chunk parse, null for <15 bytes (P1), null for wrong magic (P2), null for total∉[1,64] (P3), null for index≥total (P4), ordered assembly, out-of-order assembly (P5), old sequence discard (P6), inconsistent total (P7), duplicate chunk ignore, timeout expiry, max aggregate size
- [ ] 1.3.5 Write integration test: encode known JSON → chunk → reassemble → decode → assert match (test fixture from specs.md §7)

## Phase 2: BLE Transport Layer

### 2.1 Transport Contract
- [x] 2.1.1 Define `TransportEvent` sealed interface (specs.md §3.3)
- [x] 2.1.2 Define `TransportLayer` interface: `fun events(): Flow<TransportEvent>`, `fun start()`, `fun stop()`

### 2.2 BleTransport Implementation
- [x] 2.2.1 Implement `BleStateMachine` with 10 states + valid transition validation (specs.md §2.1)
- [x] 2.2.2 Implement `GattOperationQueue` with serialized Mutex + Channel (specs.md §2.2)
- [x] 2.2.3 Implement BLE scanner: filter by service UUID `6D951BA3-...`, handle Android 10-11 location permission
- [x] 2.2.4 Implement BLE connector: auto-connect to first discovered Mac, MTU negotiation (target 517, fallback 185), service discovery, characteristic discovery, CCCD subscription
- [x] 2.2.5 Wire scan results → TransportEvent flow (callbackFlow)
- [x] 2.2.6 Wire GATT notifications → raw bytes → CiChunkParser → CiChunkReassembler → TransportEvent.SummaryReceived
- [x] 2.2.7 Implement reconnect with jittered backoff (1s, 2s, 4s, 8s, max 30s)
- [x] 2.2.8 Handle Bluetooth ON/OFF broadcast receiver

### 2.3 BLE Diagnostics
- [x] 2.3.1 Implement `BleDiagnostics` event logger (ring buffer, max 200 events)
- [x] 2.3.2 Log all GATT callbacks: scan results, connection state, services discovered, MTU negotiated, notification subscribed, notification received, disconnection
- [x] 2.3.3 Write unit tests: state machine valid transitions (P12), invalid transition rejection, scanner filters correct UUID, MTU negotiation sequence, CCCD write sequence

## Phase 3: Data Layer

### 3.1 Domain Models
- [x] 3.1.1 Write `BuddyDashboardState`, `CompanionStatus`, `ConnectionState`, `PendingAction`, `SessionCard`, `PermissionState`, `WidgetSnapshot` (specs.md §3.1)
- [x] 3.1.2 Implement `ProtocolMapper.toDomainState(summary: CompanionBluetoothSummary): BuddyDashboardState` with ALL can* flags = false + actionHint (P10, P11)

### 3.2 BuddyRepository
- [x] 3.2.1 Implement `BuddyRepository` as singleton with `MutableStateFlow<BuddyDashboardState>` (design.md §2.3)
- [x] 3.2.2 Implement transport event collector: each `TransportEvent` → state update
- [x] 3.2.3 Implement stale detection: coroutine timer checks `lastSyncTime`, marks stale at 30s, disconnected at 60s (P13, P14)
- [x] 3.2.4 Implement demo mode: enter/exit/cycle with local mock dataset
- [x] 3.2.5 Implement snapshot persistence: write DashboardSnapshot to Room on every state change
- [x] 3.2.6 Implement cold start restore: load last snapshot from Room
- [ ] 3.2.7 Write unit tests with Turbine: scan→discover→connect→summary→stale→disconnect flow (P12, P13), demo mode enter/cycle/exit, snapshot persist/restore, transport error → state error, permission denied → blocked state

### 3.3 Room DAOs
- [x] 3.3.1 Implement `DashboardSnapshotDao`: upsert + getLatest
- [x] 3.3.2 Implement `DiagnosticEventDao`: insert + getRecent(limit=200) + deleteOldest(keep=200)
- [x] 3.3.3 Implement `NotificationLogDao`: insert + getLastForSequence + deleteOlderThan(duration)
- [x] 3.3.4 Implement `DevicePreferenceDao`: get + upsert
- [ ] 3.3.5 Write unit tests: snapshot upsert/getLatest, diagnostic event insert/getRecent/eviction, notification log insert/dedup query

## Phase 4: Foreground Service & Permissions

### 4.1 Foreground Service
- [x] 4.1.1 Implement `BuddySyncService` extending `Service` with `connectedDevice` foreground service type
- [x] 4.1.2 Create sync notification channel (`buddy_sync`, IMPORTANCE_LOW) and ongoing notification
- [x] 4.1.3 Wire service lifecycle: onStartCommand → BleTransport.start(), onDestroy → BleTransport.stop()
- [x] 4.1.4 Handle START_STICKY for process death recovery
- [x] 4.1.5 Start service from MainActivity on first launch after permissions granted

### 4.2 Permission Coordinator
- [x] 4.2.1 Implement permission check matrix per Android version (design.md §5.2)
- [x] 4.2.2 Implement step-by-step permission request flow: Bluetooth → Notification (Android 13+)
- [x] 4.2.3 Handle "never ask again" → guide to app settings
- [x] 4.2.4 Implement `PermissionState` tracking in repository
- [ ] 4.2.5 Write instrumentation test: permission denial flows per Android version

### 4.3 HyperOS Compatibility
- [x] 4.3.1 Add battery optimization exemption request UI
- [x] 4.3.2 Add Background autostart guidance (link to HyperOS settings)
- [x] 4.3.3 Add app lock in recents suggestion in diagnostics
- [x] 4.3.4 Implement foreground-only degradation when background restricted
- [ ] 4.3.5 Log HyperOS-specific behavior via `BleDiagnostics`

## Phase 5: UI

### 5.1 Theme
- [x] 5.1.1 Implement dark-only Material 3 theme with color tokens (design.md §4.3)
- [x] 5.1.2 Define typography (Display 15sp Bold, Headline 16sp Medium, Body 13sp Medium, Caption 12sp Bold)
- [x] 5.1.3 Define shapes (cards R=18dp, chips R=8dp, capsules R=50%)
- [x] 5.1.4 Set up `BuddyLiteApp` Application class

### 5.2 Navigation
- [x] 5.2.1 Implement `MainActivity` as single-Activity Compose host
- [x] 5.2.2 Set up `NavHost` with "dashboard" and "diagnostics" routes
- [x] 5.2.3 Wire StateFlow collection with `collectAsStateWithLifecycle()`

### 5.3 Shared Components
- [x] 5.3.1 Implement `PulseDot`: animated circle with status color + pulse animation (idle=static, active=slow pulse, waiting=fast pulse)
- [x] 5.3.2 Implement `StatusPill`: capsule with PulseDot + status label
- [x] 5.3.3 Implement `BuddyButton`: rounded button with icon + label + tint (primary/secondary)
- [x] 5.3.4 Implement `MetadataChip`: capsule with icon + text (workspace/tool)
- [ ] 5.3.5 Implement `ConnectionDot`: connection state indicator
- [ ] 5.3.6 Implement `MascotIcon`: source-based emoji/icon display

### 5.4 Dashboard Screen
- [x] 5.4.1 Implement `StatusHeader`: source name + status pill + connection indicator (design.md §4.4)
- [x] 5.4.2 Implement `PrimaryMessage`: latest message with role indicator
- [x] 5.4.3 Implement `SessionList`: multi-session cards with status dots + truncation
- [x] 5.4.4 Implement `QuestionCard`: read-only question header/text + "仅 Mac 可操作" label
- [x] 5.4.5 Implement `ActionBar`: demo entry, diagnostics, reconnect buttons
- [x] 5.4.6 Implement 5 visual states: searching, connected, waiting, stale, offline (design.md §4.2)
- [ ] 5.4.7 Implement animation: state transitions via `animateContentSize()` + `Crossfade`

### 5.5 Discovery Screen
- [x] 5.5.1 Implement scanning state: spinner + "正在搜索附近的 CodeIsland..."
- [x] 5.5.2 Implement "搜索已停止" state with manual retry
- [x] 5.5.3 Implement connection progress: "正在连接 MacBook Pro..."
- [x] 5.5.4 Implement demo mode entry button

### 5.6 Diagnostics Screen
- [x] 5.6.1 Implement Bluetooth status section (adapter state, connection state, negotiated MTU)
- [x] 5.6.2 Implement permission status section with ✓/✗ indicators
- [x] 5.6.3 Implement last sync section (time, sequence, source, status)
- [x] 5.6.4 Implement GATT event log list (last 20 events, scrollable)
- [x] 5.6.5 Implement HyperOS guidance section (background autostart, battery saver links)
- [x] 5.6.6 Implement action buttons: verify permissions, rescan, export diagnostics, forget device
- [ ] 5.6.7 Implement export diagnostics as JSON share intent (privacy: no message content)

## Phase 6: Notifications

### 6.1 NotificationController
- [x] 6.1.1 Create alert notification channel (`buddy_alert`, IMPORTANCE_HIGH)
- [x] 6.1.2 Implement `NotificationController.evaluateAlert(state, sequence)` with dedupe (specs.md §4.2)
- [x] 6.1.3 Persist `lastNotifiedSequence` in Room NotificationLog
- [x] 6.1.4 Implement 5-minute repeat cooldown per sequence
- [x] 6.1.5 Build alert notification: title + body + "请回到 Mac 处理" action, click opens MainActivity
- [x] 6.1.6 Write unit tests: fire for waitingApproval (P17), fire for waitingQuestion (P17), no fire for idle/processing/running (P17), dedupe same sequence (P15), cooldown respect, non-blocking within 500ms (P16), sequence-based dedupe after restart

## Phase 7: Widget

### 7.1 Glance Widget
- [x] 7.1.1 Define `WidgetSnapshot` data class (compact version of dashboard state)
- [x] 7.1.2 Implement `WidgetSnapshotProvider`: extract from BuddyDashboardState → persist to Room
- [x] 7.1.3 Implement `BuddyGlanceWidget`: 4x1 layout (source + status + workspace + session count + sync time) (design.md §6.1)
- [x] 7.1.4 Implement 2x2 compact layout (source + status only)
- [x] 7.1.5 Wire widget update via WorkManager on each new summary
- [x] 7.1.6 Write unit test: WidgetSnapshot serialization round-trip, snapshot provider extracts correctly

## Phase 8: Integration & Polish

### 8.1 Demo Mode
- [x] 8.1.1 Implement demo dataset: 5 mock states × 5 sources cycling (specs.md §3.1)
- [x] 8.1.2 Implement `DemoController`: enter/exit/cycle with state consistency
- [ ] 8.1.3 Write unit test: demo state consistency (P14), demo override vs real data behavior

### 8.2 Cold Start & Recovery
- [x] 8.2.1 Implement cold start: load snapshot from Room → show immediately
- [x] 8.2.2 Implement service restart after process death (START_STICKY)
- [x] 8.2.3 Handle stale snapshot: show with "数据可能已过期" label
- [ ] 8.2.4 Write integration test: kill process → restart → snapshot visible → BLE reconnects

### 8.3 Accessibility
- [ ] 8.3.1 Add contentDescription to all icons and status indicators
- [ ] 8.3.2 Ensure minimum touch target 48dp for all interactive elements
- [ ] 8.3.3 Test with TalkBack navigation flow

### 8.4 TransportLayer Interface (Extension Point)
- [x] 8.4.1 Extract TransportLayer interface from BleTransport
- [x] 8.4.2 Document extension contract: events, lifecycle, reconnect policy
- [x] 8.4.3 Add comment placeholder for future mDNS/WebSocket transport

## Phase 9: Testing & QA

### 9.1 Unit Tests
- [ ] 9.1.1 Protocol layer: chunk parser, reassembler, JSON decode, status mapping (Phase 1 tests)
- [ ] 9.1.2 BLE layer: state machine (P12-P14), GATT queue sequencing
- [ ] 9.1.3 Data layer: repository state transitions, stale detection, snapshot persist/restore
- [ ] 9.1.4 Notification: alert rules (P15-P17)
- [ ] 9.1.5 Widget: snapshot serialization, provider logic

### 9.2 Integration Tests
- [ ] 9.2.1 End-to-end: fake BLE stream → chunk parser → reassembler → JSON decode → domain mapping → state update (use reference-compatible fixtures)
- [ ] 9.2.2 Repository + fake transport: full lifecycle from scan to disconnect
- [ ] 9.2.3 Foreground service + fake transport: service start/stop, notification state

### 9.3 Instrumentation Tests
- [ ] 9.3.1 Permission flow on Android 12+, 13+, 14+
- [ ] 9.3.2 BLE scan/connect with real or simulated peripheral on device
- [ ] 9.3.3 Screen-off/background: 5-15 minute observation on Xiaomi/HyperOS
- [ ] 9.3.4 Compose UI: dark mode rendering, state transitions, accessibility

### 9.4 Manual Test Scenarios
- [ ] 9.4.1 First launch → permission grant → BLE scan → Mac discovery → connect → summary received (小米 14 Ultra)
- [ ] 9.4.2 Connected → Mac stop CodeIsland → stale → disconnected → Mac restart → auto-reconnect
- [ ] 9.4.3 WaitingApproval/WaitingQuestion → notification received → tap → dashboard shows question
- [ ] 9.4.4 Screen off 5 minutes → screen on → check last state freshness
- [ ] 9.4.5 Force kill app → relaunch → verify snapshot visible + BLE reconnects
- [ ] 9.4.6 Demo mode: enter → cycle states 5× → exit → verify real state restored
- [ ] 9.4.7 Bluetooth toggle OFF/ON while connected
- [ ] 9.4.8 Multiple status transitions: idle→processing→running→waiting→idle

## Phase 10: Build & Release

### 10.1 Build Configuration
- [ ] 10.1.1 Configure ProGuard/R8 rules for release (kotlinx.serialization, Room, Coroutines)
- [ ] 10.1.2 Set up signing config for debug (default) and release
- [ ] 10.1.3 Verify APK size, enable minification for release

### 10.2 Documentation
- [ ] 10.2.1 Write README.md: project overview, build instructions, BLE setup, permissions guide
- [ ] 10.2.2 Write HyperOS setup guide: autostart, battery saver, app lock instructions
- [ ] 10.2.3 Document BLE protocol for future reference (link to research report)
- [ ] 10.2.4 Document TransportLayer extension point for future developers

## Dependency Graph

```
Phase 1 (Project + Protocol)
  └─► Phase 2 (BLE Transport)
        └─► Phase 3 (Data Layer)
              ├─► Phase 4 (Service + Permissions)
              ├─► Phase 5 (UI)
              │     └─► Phase 6 (Notifications) + Phase 7 (Widget)
              └─► Phase 8 (Integration)
                    └─► Phase 9 (Testing)
                          └─► Phase 10 (Release)
```

**Parallelization**: Phase 4, Phase 5, Phase 6, and Phase 7 can start in parallel once Phase 3 reaches Repository milestone.

## Effort Estimates

| Phase | Tasks | Estimated Effort |
|-------|-------|-----------------|
| Phase 1: Setup + Protocol | 15 | 3-5 人日 |
| Phase 2: BLE Transport | 11 | 5-8 人日 |
| Phase 3: Data Layer | 14 | 3-5 人日 |
| Phase 4: Service + Permissions | 10 | 3-5 人日 |
| Phase 5: UI | 23 | 5-8 人日 |
| Phase 6: Notifications | 7 | 2-3 人日 |
| Phase 7: Widget | 7 | 1-2 人日 |
| Phase 8: Integration | 12 | 2-4 人日 |
| Phase 9: Testing | 12 | 4-6 人日 |
| Phase 10: Release | 7 | 1-2 人日 |
| **Total** | **118** | **30-48 人日** |
