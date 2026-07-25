# Codex Project Memory

这份文档给后续 Codex 对话快速接手本项目用。它不包含真实 API key，也不要把 `deepseekapi.txt` 的内容写进任何提交。

## 项目状态

- 工作区：`D:\Desktop\Codex\Voice Memos`
- 远端仓库：`https://github.com/kroblus/Voice-Memos.git`
- 默认分支：`main`
- 本轮语音任务流基础回滚点：`86b06f8`、`4e265f5`
- 应用名：`声历`
- 包名：`com.voicememos.aicalendar`
- 技术栈：Kotlin、Jetpack Compose、Room、Android `SpeechRecognizer`、DeepSeek Chat Completions。

## 产品决策

- 声历是应用内自定义日历，不同步系统/Google 日历。
- DeepSeek API key 由用户填写并保存在本机；真实 key 绝不能打印、提交或写进崩溃日志。
- 仓库根目录的 `deepseekapi.txt` 只给真实 DeepSeek 设备测试读取，已经被 `.gitignore` 忽略。
- 事项分为 `EVENT`、`REMINDER`、`TODO`；无截止时间 TODO 合法且不会安排通知。
- 所有有时间锚点的事项保留 `0` 分钟提醒；提前提醒如 `10` 分钟继续保留。
- 继续使用应用内本地日历，不申请系统 `READ_CALENDAR` / `WRITE_CALENDAR` 权限。
- 语音输入使用 3 秒静音结束策略，逻辑在 `SpeechInputController`。

## 当前 UI/UX

- 首页顶部操作为 `今天`、主题切换、识别历史、设置。
- 主题切换在主页面，浅色/深色模式会持久保存。
- 主屏底部保留“说日程”入口；录音幕布只处理当前任务，识别历史已移到独立页面。
- 录音幕布提供独立文字输入切换；麦克风不可用或拒绝权限时仍可继续输入文字。
- 最终转写停在可编辑的 `TranscriptReady`；AI 预览确认后才创建。
- 月历星期表头和日期使用同一个 7 列网格，设备测试覆盖对齐。
- 待确认区区分事件、提醒、待办，支持消歧、冲突/过去时间确认、单条/批量创建。
- 保存逐项执行并使用 `sessionId:draftId:occurrenceIndex` 幂等键；失败项可重试，成功项可撤销。
- 设置按 AI 服务、默认值、权限、数据管理和危险操作分区，API key 默认遮挡并可测试连接。

## AI 多任务解析

- `DeepSeekPrompt` 要求同一句话中的每个独立任务或时间表达拆成单独 event。
- 关键场景：`明天中午11点提醒我打开电脑12点提醒我打开手机` 必须至少生成两条草稿/事件：
  - `打开电脑`，明天 11:00。
  - `打开手机`，明天 12:00。
- mock DeepSeek 路径在文本包含 `打开电脑` 和 `打开手机` 时会返回上述两条事件，便于本地 UI 流程测试。
- mock 路径只包含 `打开电脑` 时返回一条同名提醒，避免测试输入被替换成无关事项。

## 关键文件

- UI：`app/src/main/java/com/voicememos/aicalendar/ui/CalendarApp.kt`
- UI 状态：`app/src/main/java/com/voicememos/aicalendar/ui/CalendarUiState.kt`
- 主流程：`app/src/main/java/com/voicememos/aicalendar/ui/CalendarViewModel.kt`
- DeepSeek：`app/src/main/java/com/voicememos/aicalendar/ai/DeepSeekPrompt.kt`
- DeepSeek 客户端：`app/src/main/java/com/voicememos/aicalendar/ai/DeepSeekCalendarParser.kt`
- 提醒：`app/src/main/java/com/voicememos/aicalendar/reminder/ReminderScheduler.kt`
- 通知接收：`app/src/main/java/com/voicememos/aicalendar/reminder/ReminderReceiver.kt`
- mock/主流程设备测试：`app/src/androidTest/java/com/voicememos/aicalendar/VoiceCalendarFlowTest.kt`
- 真实 DeepSeek 设备测试：`app/src/androidTest/java/com/voicememos/aicalendar/RealDeepSeekCalendarFlowTest.kt`

## 构建与验证

当前版本：

- `versionCode = 4`
- `versionName = "0.2.0"`
- `compileSdk = 36`
- `targetSdk = 36`
- Android 16 兼容

APK 输出：

```text
D:\Desktop\Codex\Voice Memos\app\build\outputs\apk\release\app-release.apk
```

常用验证：

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest -PrunRealDeepSeekTests=true --console=plain
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleRelease
```

APK 检查：

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\35.0.0\apksigner.bat" verify --verbose app\build\outputs\apk\release\app-release.apk
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\35.0.0\zipalign.exe" -c -P 16 -v 4 app\build\outputs\apk\release\app-release.apk
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\35.0.0\aapt.exe" dump badging app\build\outputs\apk\release\app-release.apk
```

已用过的 Android 16 AVD：`ShengliFlowApi36`。

2026-07-25 最终验证：

- 85 个 JVM 测试通过。
- 8 个 Android instrumentation 测试均有 API 36 通过证据；真实 DeepSeek 用例使用本机忽略的密钥单独执行。
- `lintDebug` 0 error；Release APK 通过 v2 签名和 `zipalign -P 16` 校验，并在 API 36 安装启动。
- 当前没有生产 keystore，Release APK 使用 Android Debug 证书，只能用于内测。
- 尚未完成物理真机的真实语音、来电/后台、通知投递及 boot/time/timezone 广播验收。

## 工作习惯

- 用户通常希望直接完成实现、测试、打包、提交和推送。
- 每个完成批次用简短 commit message，并在回复里给中文摘要。
- 修改后优先跑相关 Gradle 测试；涉及 UI/流程时尽量跑 `connectedDebugAndroidTest`。
- 打包 APK 后检查签名、16KB 对齐、版本号和 Android 16 安装启动。
