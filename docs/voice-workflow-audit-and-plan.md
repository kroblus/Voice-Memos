# 声历语音任务流审计与实施计划

日期：2026-07-25

## 1. 当前项目审计

### 1.1 技术与架构

- 原生 Android，Kotlin + Jetpack Compose，单 `Activity`。
- 当前没有 Navigation Compose；页面和弹层由 `CalendarUiState` 中的多个布尔值与
  `ModalBottomSheet` 条件渲染。
- 状态由 `CalendarViewModel` 的单个 `StateFlow<CalendarUiState>` 提供，但语音任务仍由
  `isListening`、`isParsing`、`showInputCurtain`、`showDraftSheet` 等分散字段共同表达。
- 数据使用 Room 1 号 schema：`calendar_events` 与 `app_settings` 两张表。
- 日历是应用内本地日历，不同步 Android Calendar Provider 或 Google Calendar。
- 语音转写使用 Android `SpeechRecognizer`，请求 partial results，静音结束时间为 3 秒。
- AI 使用 OkHttp 调用 DeepSeek Chat Completions，并通过 kotlinx.serialization 解码 JSON。
- API Key 使用 Android Keystore AES-GCM 加密，密文保存在私有 SharedPreferences。
- 本地提醒使用 `AlarmManager` + `BroadcastReceiver` + 高优先级通知渠道。
- 支持 JSON 备份/导入、ICS 文本分享、桌面 Widget。
- `compileSdk` 与 `targetSdk` 均为 36。

### 1.2 当前核心流程

```text
首页语音按钮
  → 打开录音幕布并开始 SpeechRecognizer
  → partial transcript
  → final transcript
  → 立即调用 DeepSeek
  → 草稿 BottomSheet
  → 写入 Room 并安排 AlarmManager
  → 无操作按钮的 Snackbar
```

当前流程缺少可编辑的 `TranscriptReady`、明确的 `Transcribing`、消歧、保存中、
部分成功、可撤销成功与持久失败状态。

### 1.3 可复用部分

- `SpeechInputController` 的系统识别封装与错误映射。
- `DeepSeekCalendarParser` 的网络层、超时设置、JSON fence 恢复和 mock 流程。
- `AiResponseMapper` 的日期解析、默认时长和准点提醒归一化。
- Room、Repository、备份、ICS 和 Widget 基础。
- 首页 `Header`、月/周/日/清单视图、事件列表与事件编辑器。
- 草稿卡片的折叠编辑交互。
- 设置分区、权限行、首次引导和隐私说明。
- 浅色/深色主题与现有品牌色。
- 现有 JVM 与 connected instrumented tests，尤其多事项拆分回归。

### 1.4 必须重构的部分

- 用唯一会话状态机取代互相冲突的语音布尔值。
- 最终转写后停止自动解析，先进入可编辑的 `TranscriptReady`。
- 将 AI 协议升级为 `EVENT`、`REMINDER`、`TODO` 强类型 items，并校验日期、时区、
  枚举、先后关系、缺失字段和歧义字段。
- 为 Room 增加类型、地点、重复规则、优先级、通知/同步状态和幂等键；显式迁移旧数据。
- 保存过程逐项记录结果，防止重复提交，并提供撤销。
- 将识别历史移出录音幕布。
- 将错误显示在当前交互上下文内，而不是只放在被 Sheet 遮住的首页。
- 清理 `CalendarApp.kt` 和 `CalendarViewModel.kt` 中已确认的不可达旧代码。

### 1.5 高风险模块

- `CalendarApp.kt` 约 2390 行，包含全部首页、弹层、编辑器、历史和设置。
- `CalendarViewModel.kt` 同时承担状态机、AI、权限、备份、CRUD 和提醒调度。
- 批量保存没有 saving guard、事务结果或幂等键。
- 请求通知权限和保存当前同时发生；权限结果未参与逐项保存结果。
- Manifest 同时声明 `SCHEDULE_EXACT_ALARM` 与 `USE_EXACT_ALARM`。
- 没有开机、时间/时区变化、应用升级后的提醒重建。
- Android 11+ SpeechRecognizer package visibility 查询声明缺失。
- release 未配置生产证书时会使用 debug 证书。
- 绝大多数 UI 文案仍硬编码在 Kotlin；可访问性和大字体覆盖不足。

### 1.6 数据、权限与系统边界

- Room schema 将从 v1 升级；旧 `calendar_events` 全部迁移为 `EVENT`，不清空数据。
- 本轮继续使用应用内本地日历，不申请 `READ_CALENDAR`/`WRITE_CALENDAR`。
- 若后续选择系统日历同步，必须新增独立 Calendar Provider adapter、账户选择、
  `externalCalendarEventId`、逐项同步结果和双端撤销；不在本轮静默加入。
- 精确闹钟只保留 `SCHEDULE_EXACT_ALARM`，无权限时明确标记近似提醒。
- 新增 `RECEIVE_BOOT_COMPLETED` 仅用于重建用户已创建的本地提醒。
- 通知权限拒绝不阻止本地事项保存，但必须反馈“事项已保存，通知未安排”。

### 1.7 基线

审计前工作树干净，`main` 与 `origin/main` 同步。以下基线通过：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
```

当前没有在线设备；本机存在 Android 16 AVD `ShengliFlowApi36`。

## 2. 分阶段实施计划

### 阶段 1：统一状态机与错误模型

- 目标：建立 `VoiceScheduleSession` 与 `VoiceTaskState`；加入录音、转写完成、解析、
  保存与失败的合法转换和防重入；失败后保留文本。
- 涉及：`CalendarUiState.kt`、`CalendarViewModel.kt`、`SpeechInputController.kt`；
  新增 `ui/session/*` 与状态转换单元测试。
- 风险：现有 instrumentation test 假定 final transcript 自动解析。
- 验收：状态转换单测；重复 parse/save 不会并行；final transcript 停在可编辑状态。
- 旧数据影响：无。
- 数据库迁移：无。

### 阶段 2：强类型事项模型与 AI Schema

- 目标：区分 `EVENT`、`REMINDER`、`TODO`；采用 `items` 响应，支持多事项、问题列表、
  missing/ambiguous fields 和严格验证。
- 涉及：`domain/*`、`ai/AiCalendarResponse.kt`、`AiResponseMapper.kt`、
  `DeepSeekPrompt.kt`、`DeepSeekCalendarParser.kt` 及 AI 单元测试。
- 风险：真实模型可能仍返回旧 `events`；解析器需兼容旧字段后再逐步收紧。
- 验收：多事项、三类型、非法 JSON、非法枚举、时区、结束早于开始、歧义测试。
- 旧数据影响：无。
- 数据库迁移：本阶段模型本身不迁移；持久化在下一阶段完成。

### 阶段 3：Room v2 与安全迁移

- 目标：为本地事项增加类型、地点、重复、优先级、通知状态、同步状态和幂等键。
- 涉及：`AppDatabase.kt`、`CalendarEventEntity.kt`、DAO、Repository、mappers、
  schema 文件、备份/ICS 与迁移测试。
- 风险：Room identity hash、旧备份兼容和重复键冲突。
- 验收：v1 schema 创建后迁移至 v2；旧行保留且类型为 `EVENT`；备份 v1 可导入。
- 旧数据影响：保留；新字段使用明确默认值。
- 数据库迁移：需要显式 `MIGRATION_1_2`，禁止 destructive migration。

### 阶段 4：转写、消歧与预览

- 目标：录音幕布只处理当前会话；显示时长/动画；最终转写可编辑；结构化预览支持
  类型、标题、日期时间、提醒、重复、地点、备注、全天、缺失与歧义编辑。
- 涉及：`CalendarApp.kt`、`CalendarUiState.kt`、`CalendarViewModel.kt`；
  新增 `ui/voice/*`、`ui/preview/*` 和 strings。
- 风险：Compose Sheet 返回行为、键盘遮挡、现有 testTag 迁移。
- 验收：用户确认前不写 Room；关键歧义阻止创建；按钮动态显示“创建 N 项”。
- 旧数据影响：无。
- 数据库迁移：无新增迁移。

### 阶段 5：保存结果、部分成功与撤销

- 目标：逐项保存、幂等、防重复点击、明确通知调度结果；成功可查看和撤销，
  部分失败可仅重试失败项。
- 涉及：ViewModel、Repository、ReminderScheduler、结果 UI 与历史更新。
- 风险：本地记录与 AlarmManager 的补偿一致性。
- 验收：单项/多项/部分失败/全部失败/重复提交/撤销自动化测试。
- 旧数据影响：仅对本次创建项执行补偿删除。
- 数据库迁移：使用阶段 3 已加入字段，无新迁移。

### 阶段 6：首页、历史与设置

- 目标：首页提供有价值的今日区域和类型标识；历史独立；设置按 AI、默认值、
  权限、数据、危险操作分区；API Key 默认隐藏；从系统设置返回自动刷新。
- 涉及：home/history/settings UI、SettingsRepository、历史 codec、strings 和测试。
- 风险：旧 UI 测试定位器与历史 JSON 兼容。
- 验收：今日排序、历史查看/编辑/重解析/删除、清空二次确认、设置层级和权限状态。
- 旧数据影响：兼容读取旧历史项。
- 数据库迁移：无；历史 JSON 采用带默认值的向后兼容升级。

### 阶段 7：平台可靠性、视觉与无障碍

- 目标：修复精确闹钟权限模型；开机/时间/时区/应用更新重建提醒；
  SpeechRecognizer visibility；统一颜色/间距/圆角/文案；48dp、TalkBack、大字体和安全区。
- 涉及：Manifest、receiver、ReminderScheduler、主题、strings、关键 Compose semantics。
- 风险：OEM AlarmManager 行为、Play exact-alarm 资格、动态字体布局。
- 验收：Android 16 AVD 权限与提醒 smoke；小屏/横屏/深色/大字体/语义测试。
- 旧数据影响：只读取未来事项重建提醒。
- 数据库迁移：无。

### 阶段 8：完整回归与交付

- 目标：全量 JVM、lint、Debug/AndroidTest/Release 构建；Android 16 安装启动；
  APK 签名与 16KB 对齐检查；文档、版本、提交与推送。
- 涉及：测试、README、设计规范、交付报告和版本配置。
- 风险：真实 DeepSeek 测试依赖本地未提交密钥；正式发布证书可能不可用。
- 验收：所有可运行门禁有输出；无法完成的真机/生产签名项明确标记，不虚报。
- 旧数据影响：通过升级测试证明不丢失。
- 数据库迁移：验证阶段 3 的迁移。

## 3. 回滚策略

- 每个阶段保持独立提交，后续阶段不通过时可回滚到上一提交。
- Room v2 一旦安装到真实用户数据，不使用 Git 回滚数据库；应用代码必须继续携带
  v1→v2 migration。若需功能回滚，保留 v2 schema 并关闭新入口。
- 系统权限采用增量 Manifest 变更，可单独回滚；不新增系统日历权限。
- API Key、`deepseekapi.txt` 与用户转写不会进入提交、公开日志或测试输出。
