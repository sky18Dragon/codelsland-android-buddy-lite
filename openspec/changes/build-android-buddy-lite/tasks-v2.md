# tasks-v2.md — BLE Peripheral Implementation

## Phase V1: Protocol Foundation (移植 android-watch 协议层)

- [x] V1.1 Port `BleProtocol` constants: UUIDs, frame markers, command bytes, timing constants
- [x] V1.2 Port `IncomingCommand` sealed interface: AgentFrame, WorkspaceFrame, MessagePreviewFrame, Brightness, Orientation
- [x] V1.3 Port `BuddyFrameParser.parse(ByteArray): IncomingCommand?` with all validation rules
- [ ] V1.4 Write unit tests: all valid frame types, empty payload (FP1), unknown IDs (FP3/FP4), length clamping (FP5-FP7), brightness clamping (FP8), invalid UTF-8 (FP9)

## Phase V2: Buddy Models

- [x] V2.1 Port `Mascot` enum with wireId, title, accentColor
- [x] V2.2 Port `AgentStatusCode` enum with wireId, title
- [x] V2.3 Port `DisplayMode` (STANDBY, AGENT, DEMO) and `PeripheralState` (STARTING→ADVERTISING→CONNECTED→...PERMISSION_REQUIRED→UNSUPPORTED→BLUETOOTH_OFF→ERROR)
- [x] V2.4 Port `ScreenOrientation` enum
- [x] V2.5 Add `MessagePreview` data class to domain models
- [ ] V2.6 Write unit tests: Mascot.fromWireId, AgentStatusCode.fromWireId for all valid + unknown IDs

## Phase V3: BLE Peripheral Service

- [x] V3.1 Create `BuddyPeripheralService` extending Service (BLE Peripheral + GATT Server + Advertiser)
- [x] V3.2 Implement `startPeripheral()`: capability checks (adapter, advertiser, permissions)
- [x] V3.3 Implement `ensureGattServer()`: open GATT server, add Buddy service with Write + Notify characteristics
- [x] V3.4 Implement `startAdvertising()`: AdvertiseSettings + AdvertiseData (service UUID + device name)
- [x] V3.5 Implement `BluetoothGattServerCallback`: onCharacteristicWriteRequest (parse frames), onDescriptorWriteRequest (CCC subscription tracking), onConnectionStateChange
- [x] V3.6 Handle CCC descriptor: track subscribed devices and delivery mode (notification vs indication)
- [x] V3.7 Implement `notifyHostPayload()`: send bytes via notifyCharacteristicChanged with retry logic
- [x] V3.8 Implement `notifyHostControl()`: approve (0xF0), deny (0xF1), skip (0xF2)
- [x] V3.9 Implement `notifyHostFocusRequest()`: send current mascot sourceId as focus
- [x] V3.10 Wire Bluetooth state broadcast receiver (restart on BT ON, mark offline on BT OFF)
- [x] V3.11 Implement foreground notification: ongoing notification showing peripheral state
- [x] V3.12 Handle `ACTION_APPROVE_CURRENT_PERMISSION`, `ACTION_DENY_CURRENT_PERMISSION`, `ACTION_SKIP_CURRENT_QUESTION`, `ACTION_REQUEST_FOCUS` intents
- [x] V3.13 Implement `stopPeripheral()`: close GATT server, stop advertising, clear state
- [ ] V3.14 Write unit tests: GATT service construction, CCC parsing, permission matrix per Android version

## Phase V4: Repository Adaptation

- [x] V4.1 Replace TransportEvent flow with IncomingCommand consumption
- [x] V4.2 Map IncomingCommand → BuddyDashboardState updates (agent→source/status/tool, workspace, message, brightness)
- [x] V4.3 Implement DisplayMode state machine: STANDBY→AGENT on agent frame, AGENT→STANDBY on 60s inactivity
- [x] V4.4 Set canApprove/canDeny dynamically: true iff Mac subscribed AND status=WAITING_APPROVAL
- [x] V4.5 Set canSkip dynamic: true iff Mac subscribed AND status=WAITING_QUESTION
- [x] V4.6 Adapt stale detection: use firmwareInactivityTimeoutMs (60s with no agent frame)
- [x] V4.7 Adapt demo mode: generate Buddy-protocol-shaped mock states using Mascot + AgentStatusCode
- [x] V4.8 Remove lastDeliveredSequence; use local monotonic state version for widget dedupe
- [x] V4.9 Update widget snapshot on state change
- [ ] V4.10 Write unit tests with Turbine: IncomingCommand→state mapping, inactivity timeout (FP10/FP11), dynamic canApprove/canSkip (FP12/FP13), message slot management (FP14)

## Phase V5: Notification Adaptation

- [x] V5.1 Implement attention key generation: "${mascotId}:${statusId}:${toolName}:${messageHash}"
- [x] V5.2 Implement attention notification: BigTextStyle with action buttons
- [x] V5.3 Wire notification actions (Approve/Deny/Skip/Open) via PendingIntent to BuddyPeripheralService
- [x] V5.4 Handle POST_NOTIFICATIONS denied: skip attention alerts, keep foreground sync notification
- [x] V5.5 Add vibration on attention notification (respect notification channel settings)
- [ ] V5.6 Write unit tests: attention key dedupe, action intents, denied permission behavior

## Phase V6: UI Adaptation

- [x] V6.1 Add PeripheralState display: advertising, connected, permission blocked, unsupported, error
- [x] V6.2 Replace scan/connect terminology with advertise/wait terminology
- [x] V6.3 Add dynamic Approve/Deny buttons (visible when canApprove=true)
- [x] V6.4 Add dynamic Skip/Open buttons (visible when canSkip=true)
- [x] V6.5 Show "Mac 未订阅通知" when connected but CCC not enabled (buttons disabled)
- [x] V6.6 Add actionHint: "可在手机处理" when uplink available
- [x] V6.7 Update Diagnostics: show advertiser state, GATT server state, Buddy UUIDs, subscription status
- [x] V6.8 Add BLUETOOTH_ADVERTISE to permission checks
- [x] V6.9 Update widget: show peripheral state (advertising/connected/stale)

## Phase V7: Wiring & Cleanup

- [x] V7.1 Update ServiceLocator: wire BuddyPeripheralService + BuddyRepository
- [x] V7.2 Update MainActivity: start BuddyPeripheralService, wire action callbacks
- [x] V7.3 Update AndroidManifest: declare BuddyPeripheralService with foregroundServiceType="connectedDevice"
- [x] V7.4 Delete v1 files: BleTransport, GattOperationQueue, CiChunkParser, CiChunkReassembler, CompanionBluetoothSummary, ProtocolMapper, TransportLayer
- [x] V7.5 Remove v1 test files referencing Apple Companion protocol
- [x] V7.6 Update tasks.md to mark v1 tasks as superseded

## Phase V8: Testing & Verification

- [ ] V8.1 Run all unit tests (protocol + repository + notification)
- [ ] V8.2 Manual test on target device: permissions granted/denied flow
- [ ] V8.3 Manual test: Bluetooth off/on while service running
- [ ] V8.4 Manual test: Mac connect → write agent/workspace/message frames → verify UI
- [ ] V8.5 Manual test: Mac subscribe CCC → Approve/Deny/Skip/Focus uplink
- [ ] V8.6 Manual test: Demo mode enter/cycle/exit
- [ ] V8.7 Manual test: Screen off / background behavior (5-15 min observation)
- [ ] V8.8 Manual test: Widget update from Buddy frames

## Dependency Order

```
V1 (Protocol) → V2 (Models) → V3 (Peripheral Service)
                                    ↓
                               V4 (Repository) → V5 (Notifications)
                                    ↓                  ↓
                               V6 (UI) ←──────────────┘
                                    ↓
                               V7 (Wiring+Cleanup) → V8 (Testing)
```

## Effort

| Phase | Tasks | Estimate |
|-------|-------|----------|
| V1 Protocol | 4 | 1-2 人日 |
| V2 Models | 6 | 0.5-1 人日 |
| V3 Peripheral Service | 14 | 5-7 人日 |
| V4 Repository | 10 | 2-3 人日 |
| V5 Notifications | 6 | 1-2 人日 |
| V6 UI | 9 | 2-3 人日 |
| V7 Wiring+Cleanup | 6 | 1-2 人日 |
| V8 Testing | 8 | 2-4 人日 |
| **Total** | **63** | **15-24 人日** |
