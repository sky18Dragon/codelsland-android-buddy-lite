# Proposal v2: BLE Peripheral Architecture (scope correction)

## Status
🟡 Proposed — supersedes v1 Apple Companion BLE Central approach

## Change Summary

**从 BLE Central (Phone→Mac) 翻转为 BLE Peripheral (Mac→Phone)**，协议从 Apple Companion BLE (6D951BA3) 切换为 Buddy/ESP32 (0000beef)。

## Why

用户明确："手机app是要被mac端连接" — Mac 应主动连接 Phone，而非 Phone 连接 Mac。

Mac 端已有 `ESP32BridgeManager` 可扫描并连接 Buddy 协议外设（`android-watch` 即基于此协议）。Android 手机应像 android-watch 一样作为 BLE Peripheral 广播，Mac 发现后连接并写入 agent 状态帧。

## Technical Architecture v2

```
Mac CodeIsland (ESP32BridgeManager)
    │ BLE Central: scan for "Buddy" service
    │ BLE Write: agent/workspace/message/brightness/orientation frames
    │ BLE Notify subscribe: receive uplink commands from phone
    ▼
Phone (BuddyPeripheralService)
    │ BLE Peripheral: advertise as "Buddy"
    │ GATT Server: expose write + notify characteristics
    │ Frame Parser: decode binary frames → domain state
    │ Uplink: send approve/deny/skip/focus via notify
```

## Protocol: Buddy/ESP32

| Property | Value |
|----------|-------|
| Service UUID | `0000beef-0000-1000-8000-00805f9b34fb` |
| Write Characteristic | `0000beef-0001-1000-8000-00805f9b34fb` |
| Notify Characteristic | `0000beef-0002-1000-8000-00805f9b34fb` |
| Advertised Name | `Buddy` 或 `Buddy-XXXXXX` |

### Downlink Frames (Mac → Phone, via Write)

| Frame | Format | Example |
|-------|--------|---------|
| Agent | `[sourceId:1B][statusId:1B][toolLen:1B][toolName:N]` | `0x01 0x03 0x09 "WebSearch"` |
| Workspace | `[0xFC][len:1B][workspace:N]` | `0xFC 0x0F "repo/CodeIsland"` |
| Message Preview | `[0xFB][index:1B][total:1B][flags&len:1B][text:N]` | bit7=isUser, low7=textLen |
| Brightness | `[0xFE][percent:1B]` | `0xFE 0x46` (70%) |
| Orientation | `[0xFD][orientation:1B]` | `0xFD 0x00` (up) |

### Uplink Commands (Phone → Mac, via Notify)

| Command | Byte | Description |
|---------|------|-------------|
| Approve | `0xF0` | 批准当前权限请求 |
| Deny | `0xF1` | 拒绝当前权限请求 |
| Skip | `0xF2` | 跳过当前问题 |
| Focus | `[sourceId]` | 请求 Mac 聚焦当前 agent |

### Mascot Wire IDs

| ID | Name | ID | Name | ID | Name |
|----|------|----|------|----|------|
| 0 | Claude | 6 | Qoder | 12 | AntiGravity |
| 1 | Codex | 7 | Factory | 13 | WorkBuddy |
| 2 | Gemini | 8 | CodeBuddy | 14 | Hermes |
| 3 | Cursor | 9 | StepFun | 15 | Kimi |
| 4 | Copilot | 10 | OpenCode | | |
| 5 | Trae | 11 | Qwen | | |

### Agent Status Wire IDs

| ID | Status |
|----|--------|
| 0 | idle |
| 1 | processing |
| 2 | running |
| 3 | waitingApproval |
| 4 | waitingQuestion |

## Scope: What Changes vs What Stays

### Files to DELETE (v1 Apple Companion BLE code)
- `ble/BleTransport.kt` — Central BLE → replaced
- `ble/GattOperationQueue.kt` — Central GATT queue → not needed
- `ble/CiChunkParser.kt` — CI chunk parser → replaced
- `ble/CiChunkReassembler.kt` — chunk reassembler → not needed
- `protocol/CompanionBluetoothSummary.kt` — Apple protocol model → replaced
- `protocol/ProtocolMapper.kt` — JSON mapper → replaced

### Files to CREATE/REWRITE
- `ble/BuddyPeripheralService.kt` — Foreground Service as BLE Peripheral (GATT Server + Advertiser)
- `ble/BuddyFrameParser.kt` — Binary frame parser (mirrors android-watch BleProtocol + BuddyFrameParser)
- `ble/BuddyModels.kt` — Mascot, AgentStatusCode, PeripheralState, DisplayMode, etc.
- `ble/BuddyRepository.kt` — Updated state management for Buddy protocol
- `service/BuddySyncService.kt` — Update to use BuddyPeripheralService

### Files to KEEP (no changes needed)
- `ui/` — All Compose screens (Dashboard, Diagnostics, Components, Theme, Navigation)
- `data/NotificationController.kt` — Notification logic (add attention notification)
- `data/local/` — Room DB, DataStore, WidgetSnapshotStore
- `widget/` — Glance widget
- `di/ServiceLocator.kt` — Update wiring only
- `MainActivity.kt` — Update wiring only
- `BuddyLiteApp.kt` — Update wiring only

### New Capabilities Unlocked

| Capability | v1 (Apple Central) | v2 (Buddy Peripheral) |
|------------|-------------------|----------------------|
| 状态镜像 | ✅ | ✅ |
| 批准权限请求 | ❌ | ✅ |
| 拒绝权限请求 | ❌ | ✅ |
| 跳过问题 | ❌ | ✅ |
| 打开 Mac 会话 (Focus) | ❌ | ✅ |
| 问题展示 | ⚠️ 降级 | ✅ |
| 多会话 | ✅ | ✅ |
| 通知提醒 | ✅ | ✅ + attention vibration |
| 演示模式 | ✅ | ✅ |
| 诊断页 | ✅ | ✅ |

## Implementation Strategy

参考 `android-watch/` 的 `BuddyPeripheralService` + `BleProtocol` + `BuddyFrameParser` + `BuddyRepository` 实现，适配为手机端 UI：

1. **BLE Peripheral Service**: 从 android-watch 移植，增加 phone-appropriate 通知和生命周期
2. **Frame Parser**: 直接复用 android-watch `BuddyFrameParser` 逻辑
3. **Models**: 复用 Mascot/AgentStatusCode/PeripheralState 枚举
4. **Repository**: 更新为 Buddy 协议状态机（STANDBY→AGENT→DEMO）
5. **UI**: 保留现有 Compose Dashboard，更新数据绑定
6. **Commands**: 添加 approve/deny/skip/focus 按钮 + notify uplink

## Constraints (unchanged from v1)
- HC-9: 包名 `com.codeisland.buddylite`
- SC-1~8: Kotlin/Compose/Room/DataStore 技术栈不变
- 目标设备: 小米 14 Ultra / HyperOS / Android 16
- 不改 Mac 端代码

## Risks
| Risk | Severity | Mitigation |
|------|----------|------------|
| BLE Peripheral 在部分手机上不可用 | 🟡 Medium | 参考 android-watch 的 unsupported 检测逻辑 |
| 手机 BLE 广播耗电 | 🟡 Medium | 仅在 Mac 连接期间保持广播；空闲时降低广播频率 |
| 后台 Peripheral 被杀 | 🟡 Medium | Foreground Service + START_STICKY + HyperOS 引导 |
