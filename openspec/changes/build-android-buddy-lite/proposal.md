# Proposal: CodeIsland Android Buddy Lite

## Change ID
`build-android-buddy-lite`

## Status
🟡 Proposed (Research Complete)

## Summary

为 CodeIsland Mac 端 AI Agent 会话构建 **Android 伴侣 App**（代号 **CodeIsland Android Buddy Lite**），通过 BLE 摘要协议将 Mac 端 agent 状态镜像到 Android 手机（目标设备：小米 14 Ultra / HyperOS / Android 16），提供状态看板、通知提醒、多会话总览、演示模式和系统诊断能力。

本 App 定位为 **Mirror-first（镜像优先）**，在不修改 Mac 端代码的前提下，严格使用现有 BLE 摘要协议实现状态同步。iOS 版的双向命令回传能力（审批/回答/跳过等）依赖 Apple 专有 `MultipeerConnectivity`，安卓无法等价实现，本期标注为不可用。

## Motivation

- CodeIsland 现有 iPhone/iWatch 伴侣端依赖 Apple 专有协议，安卓用户无法使用
- Mac 端已通过 `AppleCompanionBluetoothPeripheral` 广播 BLE 摘要，该协议**跨平台可读**
- 目标用户持有小米 14 Ultra，期望在随身设备上查看 Mac 端 AI agent 工作状态
- 预留在未来接入小米音箱 lx04 等设备的扩展能力

## Constraints

### Hard Constraints (不可违反)

| ID | Constraint | Source |
|----|-----------|--------|
| HC-1 | BLE Service UUID: `6D951BA3-8F41-4C45-9D8A-12085E0D7A10` | Mac 端 `AppleCompanionBluetoothPeripheral.swift` |
| HC-2 | Notify Characteristic UUID: `25C1B67B-E903-4A0C-8A78-3EE8AB7317B7` | 同上 |
| HC-3 | Chunk 格式: 0x43 0x49 0x01 + UInt64 BE seq + UInt16 BE idx + UInt16 BE total + body | 同上 |
| HC-4 | 单 chunk payload ≤ 120 bytes，单消息 ≤ 64 chunks (max ~7680 bytes) | 同上 |
| HC-5 | BLE 仅支持 Notify（单向），无 Write characteristic → 不可回传命令 | `CompanionBluetoothCentral.swift` |
| HC-6 | JSON 日期编码: ISO 8601 | 已验证 |
| HC-7 | 不修改 Mac 端代码 | 用户约束 |
| HC-8 | 目标设备: 小米 14 Ultra / HyperOS / Android 16 | 用户约束 |
| HC-9 | 包名: `com.codeisland.buddylite` | 用户确认 |
| HC-10 | 仅使用 Apple Companion BLE 协议，不涉及 Buddy/ESP32 协议 | 用户确认 |

### Soft Constraints (建议遵循)

| ID | Constraint |
|----|-----------|
| SC-1 | 语言: Kotlin，UI: Jetpack Compose + Material 3 |
| SC-2 | 并发: Kotlin Coroutines + Flow |
| SC-3 | 序列化: kotlinx.serialization JSON |
| SC-4 | 本地存储: Room + SQLite |
| SC-5 | 前台服务类型: `connectedDevice` (Android 14+ 要求) |
| SC-6 | targetSdk: Android 16, minSdk: 29 |
| SC-7 | 视觉: 深色背景 #040406, 胶囊圆角 R=18, 状态色 green/orange |
| SC-8 | 小组件: Glance App Widget |

### 协议字段截断限制

| 字段 | 限制 |
|------|------|
| sessionId | 96 字符 |
| source | 无限制 |
| toolName | 64 字符 |
| workspaceName | 64 字符 |
| message | 220 字符 |
| questionHeader | 40 字符 |
| questionText | 180 字符 |
| sessions[] | 最多 5 个 |
| session.message | 120 字符 |
| session.toolName | 48 字符 |
| session.workspaceName | 48 字符 |

## Scope

### In Scope（本期实现）

1. **BLE 连接层**
   - 扫描 Mac 端 `6D951BA3` 服务广播
   - GATT 连接 + 订阅 Notify characteristic
   - CI 分片重组（校验 magic bytes + sequence/index/total + 乱序重组）
   - JSON 解码为 `CompanionBluetoothSummary` 领域模型
   - 断连自动重扫 + 状态恢复

2. **Dashboard 首页**
   - 连接状态卡片（已连接/搜索中/离线）
   - Agent 状态展示（source/status/workspace/toolName/message）
   - 多会话总览列表（最多 5 个 session）
   - 问题摘要卡片（仅展示 questionHeader/questionText，标注"仅 Mac 可操作"）
   - 状态 pulse 动画（idle=灰/processing+running=绿/waiting=橙）

3. **常驻通知 + 状态变化通知**
   - `connectedDevice` 前台服务常驻通知
   - waitingApproval / waitingQuestion 触发高优先级通知
   - 通知文案引导用户回到 Mac 处理

4. **桌面小组件 (Glance)**
   - 当前 agent 状态 + 最近消息摘要
   - 多会话数量徽标
   - 点击打开 App

5. **演示模式**
   - 本地 mock 多种状态（idle/processing/running/waitingApproval/waitingQuestion）
   - 支持切换不同 mascot source（codex/claude/gemini/copilot 等）
   - 无需连接 Mac 即可体验完整 UI

6. **诊断页**
   - 蓝牙开关状态 + 权限状态
   - 最近一次摘要接收时间
   - 最近 20 条 GATT 事件日志
   - HyperOS 后台限制检测 + 引导（Background autostart / Battery Saver）
   - 通知权限检测

7. **权限引导**
   - 蓝牙权限（BLUETOOTH_SCAN / BLUETOOTH_CONNECT，Android 12+）
   - 通知权限（POST_NOTIFICATIONS，Android 13+）
   - 前台服务权限
   - HyperOS 后台自启动引导

8. **扩展预留**
   - 协议兼容层接口（`TransportLayer` 抽象 → 未来可替换为 mDNS/WebSocket）
   - 设备插件注册接口（`DevicePluginRegistry` → 为 lx04 音箱预留）

### Out of Scope（本期不实现，标注不可用）

| 功能 | 原因 | UI 处理 |
|------|------|---------|
| 批准/拒绝审批 | 需要双向命令信道 | 显示"请回到 Mac 批准"，按钮置灰 |
| 回答问题 | 同上 | 显示"请回到 Mac 回答" |
| 跳过问题 | 同上 | 同批准/拒绝 |
| 打开 Mac 会话 (focus) | 同上 | 不显示此按钮 |
| 完整消息列表 | BLE 摘要仅有单条 message | 展示最近一条 + 会话摘要 |
| 完整问题选项 | BLE 摘要无 options 数组 | 仅展示问题标题和文本 |
| 与 iPhone 版功能等价 | Apple 专有协议限制 | 产品定位明确为 Lite 版 |

## Architecture Overview

### Module Structure

```
app/                                 # Android 应用模块
├── src/main/java/com/codeisland/buddylite/
│   ├── App.kt                       # Application 入口
│   ├── MainActivity.kt              # 主 Activity + Compose 导航
│   ├── ble/                         # BLE 传输层
│   │   ├── BleScanner.kt            # BLE 扫描 + 设备发现
│   │   ├── BleConnector.kt          # GATT 连接管理
│   │   ├── CiChunkParser.kt         # CI 分片重组 + 校验
│   │   └── CompanionBluetoothCentral.kt  # BLE Central 编排
│   ├── protocol/                    # 协议兼容层
│   │   ├── CompanionBluetoothSummary.kt   # BLE 摘要 JSON 模型
│   │   ├── TransportLayer.kt        # 传输层抽象接口（预留扩展）
│   │   └── ProtocolCompatLayer.kt   # BLE 摘要 → 领域模型映射 + 降级标记
│   ├── data/                        # 数据层
│   │   ├── BuddyRepository.kt       # 状态仓库（StateFlow）
│   │   ├── BuddyDatabase.kt         # Room 数据库
│   │   └── model/                   # 领域模型
│   │       ├── BuddyDashboardState.kt
│   │       ├── SessionCard.kt
│   │       └── CompanionStatus.kt
│   ├── ui/                          # UI 层
│   │   ├── theme/                   # 主题（深色 + 颜色令牌
│   │   ├── dashboard/               # Dashboard 首页
│   │   ├── discovery/               # 设备发现页
│   │   ├── demo/                    # 演示模式
│   │   ├── diagnostics/             # 诊断页
│   │   ├── components/              # 共享组件（PulseDot, StatusPill, MascotIcon等）
│   │   └── navigation/              # 导航
│   ├── service/                     # 前台服务
│   │   └── BuddySyncService.kt      # connectedDevice 前台服务
│   ├── widget/                      # 桌面小组件
│   │   └── BuddyWidget.kt           # Glance App Widget
│   ├── plugin/                      # 设备插件框架（预留）
│   │   ├── DevicePlugin.kt          # 插件接口
│   │   └── DevicePluginRegistry.kt  # 插件注册表
│   └── diagnostics/                 # 诊断工具
│       └── SystemDiagnostics.kt     # HyperOS 兼容性检测
```

### Data Flow

```text
Mac CodeIsland (BLE Peripheral)
    │
    │ BLE Advertisement (Service UUID: 6D951BA3)
    │ BLE Notify (Chunked JSON over 25C1B67B characteristic)
    ▼
BleScanner ──► BleConnector ──► CiChunkParser
                                    │
                                    ▼
                            CompanionBluetoothSummary
                                    │
                                    ▼
                            ProtocolCompatLayer
                              (BLE摘要→领域模型
                               + canApprove=false等降级标记)
                                    │
                                    ▼
                            BuddyRepository
                              (StateFlow<BuddyDashboardState>)
                               │        │        │
                    ┌──────────┼────────┼────────┐
                    ▼          ▼        ▼        ▼
              Dashboard   通知      小组件    诊断页
              (Compose)  (Notification) (Glance) (Compose)
```

### Tech Stack

| Dimension | Choice | Rationale |
|-----------|--------|-----------|
| Language | Kotlin 1.9.24+ | 与 android-watch 一致 |
| UI | Jetpack Compose + Material 3 | 状态驱动，深色模式，多尺寸适配 |
| Build | AGP 8.5.2, Gradle Kotlin DSL | 与 android-watch 一致 |
| Concurrency | Kotlin Coroutines + Flow | BLE 流 + StateFlow 状态分发 |
| Serialization | kotlinx.serialization | BLE JSON 直接映射 |
| Storage | Room + SQLite | 状态快照 + 诊断日志 |
| DI | Manual (或 Hilt 可选) | 轻量优先 |
| Widget | Glance 1.x | Jetpack Compose 风格 Widget API |
| Testing | JUnit 5 + MockK + Turbine | Flow 测试 |

## Key Design Decisions

### 1. 协议兼容层显式降级标记

```kotlin
data class AndroidBuddyState(
    val source: String,
    val status: CompanionStatus,
    val workspaceName: String?,
    val latestMessage: String?,
    val pendingAction: String?,
    val questionHeader: String?,
    val questionText: String?,
    val sessions: List<SessionCard>,
    // 降级标记 — 本期全部为 false
    val canApprove: Boolean = false,
    val canDeny: Boolean = false,
    val canSkip: Boolean = false,
    val canAnswerOnPhone: Boolean = false,
    val actionHint: String = "当前 Mac 端未提供安卓可用的命令回传通道，请回到 Mac 处理"
)
```

### 2. BLE Chunk 解析

```kotlin
// 对应 iPhone CompanionBluetoothChunk 的 Kotlin 实现
fun parseCiChunk(data: ByteArray): CiChunk? {
    if (data.size < 15) return null
    if (data[0] != 0x43.toByte() || data[1] != 0x49.toByte() || data[2] != 0x01.toByte()) return null
    val sequence = ByteBuffer.wrap(data, 3, 8).long  // Big-endian UInt64
    val index = ((data[11].toInt() and 0xFF) shl 8) or (data[12].toInt() and 0xFF)
    val total = ((data[13].toInt() and 0xFF) shl 8) or (data[14].toInt() and 0xFF)
    if (total !in 1..64 || index !in 0 until total) return null
    return CiChunk(sequence, index, total, data.copyOfRange(15, data.size))
}
```

### 3. HyperOS 兼容策略

- **前台服务常驻**: `connectedDevice` type + 持续通知
- **能力探测**: 启动时检测蓝牙/通知/后台限制
- **分级通知**: 仅 `waitingApproval`/`waitingQuestion` 触发高优先级
- **用户引导**: 诊断页直接引导到 HyperOS Background autostart / Battery Saver 设置
- **失败回退**: 后台不稳定时降级为"仅前台同步"

## Risks & Mitigations

| Risk | Severity | Mitigation |
|------|----------|------------|
| HyperOS 后台清理 BLE 连接 | 🔴 High | 前台服务 + 用户引导关闭省电 + 诊断页可视化 |
| BLE 摘要信息受限（无完整消息列表/选项） | 🔴 High | 协议兼容层显式标记降级 + UI 明确标注"仅 Mac 可操作" |
| 用户期望与 iOS 版功能等价 | 🟡 Medium | 产品命名 Lite + PRD 明确边界 + UI 不可用态而非隐藏 |
| 首次 BLE 连接成功率 | 🟡 Medium | 详细权限引导 + 发现页面重试机制 + 诊断日志 |
| lx04 音箱接口文档未公开 | 🟢 Low | 插件框架预留，不绑定具体实现 |
| BLE chunk 丢包/乱序 | 🟡 Medium | sequence 校验 + 超时丢弃 + 等待下一轮完整消息 |

## Success Criteria (Verifiable)

1. ✅ 小米 14 Ultra 上稳定扫描并发现 Mac BLE 广播（`6D951BA3` service UUID）
2. ✅ 成功 GATT 连接 + 订阅 Notify characteristic
3. ✅ CI 分片正确重组并解码为 `CompanionBluetoothSummary` JSON
4. ✅ Dashboard 正确展示 source/status/workspace/message/sessions
5. ✅ `waitingApproval` / `waitingQuestion` 状态触发通知（文案引导回 Mac）
6. ✅ 前台服务在熄屏后保持运行，BLE 回调在 1 分钟内可送达
7. ✅ 演示模式可切换 idle/processing/running/waitingApproval/waitingQuestion
8. ✅ 诊断页显示蓝牙权限、通知权限、最近摘要时间、HyperOS 后台限制
9. ✅ 不可用操作标注为"仅 Mac 可操作"，按钮置灰
10. ✅ 单元测试覆盖 chunk parser + summary mapping + 通知规则
11. ✅ 代码预埋 `TransportLayer` 接口 + `DevicePlugin` 接口
12. ✅ 桌面小组件可展示当前状态 + 会话数

## Deliverables

| 交付物 | 说明 |
|--------|------|
| `app/` Gradle 工程 | 完整可构建 Android 工程 |
| `BleScanner` + `BleConnector` + `CiChunkParser` | BLE 传输层，含单元测试 |
| `ProtocolCompatLayer` | 协议映射 + 降级标记 |
| `BuddyRepository` | StateFlow 状态管理 |
| Dashboard UI (Compose) | 首页状态看板 |
| 通知体系 | 前台服务常驻通知 + 状态变化通知 |
| 演示模式 | 本地 mock 多状态切换 |
| 诊断页 | 蓝牙/权限/HyperOS 后台状态 |
| Glance Widget | 桌面小组件 |
| `DevicePlugin` 接口 | 设备扩展预留 |
| 单元测试 | chunk parser, summary mapping, 通知规则 |

## Open Questions (Resolved)

| Question | Resolution |
|----------|-----------|
| 项目目录 | 当前目录作为 Gradle 工程根目录 |
| 协议范围 | 仅 Apple Companion BLE |
| 包名 | `com.codeisland.buddylite` |
| openspec CLI | 不可用，手动创建 OPSX 结构 |

## References

- 深度研究报告: `/Users/zhanghuilong/Downloads/deep-research-report.md`
- CodeIsland 参考仓库: `/tmp/CodeIsland-ref/`
- Mac BLE Peripheral: `Sources/CodeIsland/AppleCompanionBluetoothPeripheral.swift`
- iOS BLE Central: `ios/CodeIslandCompanion/CodeIslandCompanion/CompanionBluetoothCentral.swift`
- 共享协议模型: `Sources/CodeIslandCore/AppleCompanionPayload.swift`
- Android Watch 参考: `android-watch/`
- iOS UI 参考: `ios/CodeIslandCompanion/CodeIslandCompanion/ContentView.swift`
