# Codex Project Memory

这份文档给后续 Codex 对话快速接手本项目用。它不包含真实 API key，也不要把 `deepseekapi.txt` 的内容写进任何提交。

## 项目状态

- 工作区：`D:\Desktop\Codex\Voice Memos`
- 远端仓库：`https://github.com/kroblus/Voice-Memos.git`
- 默认分支：`main`
- 最新已验证提交：`9b09d66 feat: streamline task composer flow`
- 应用名：`声历`
- 包名：`com.voicememos.aicalendar`
- 技术栈：Kotlin、Jetpack Compose、Room、Android `SpeechRecognizer`、DeepSeek Chat Completions。

## 产品决策

- 声历是应用内自定义日历，不同步系统/Google 日历。
- DeepSeek API key 由用户填写并保存在本机；真实 key 绝不能打印、提交或写进崩溃日志。
- 仓库根目录的 `deepseekapi.txt` 只给真实 DeepSeek 设备测试读取，已经被 `.gitignore` 忽略。
- 所有事件保存时都要包含 `0` 分钟提醒，即事件开始时弹通知；提前提醒如 `10` 分钟继续保留。
- 语音输入使用 3 秒静音结束策略，逻辑在 `SpeechInputController`。

## 当前 UI/UX

- 首页顶部操作为 `今天`、主题切换、设置。
- 主题切换在主页面，浅色/深色模式会持久保存。
- 主屏底部仅保留一个语音按钮；点击后打开输入幕布，文字输入、生成按钮和解析历史都在幕布内。
- 月历星期表头和日期使用同一个 7 列网格，设备测试覆盖对齐。
- 待确认区是批量任务列表，默认折叠高级编辑，支持单条保存/删除和 `全部保存`。
- 设置页只放 DeepSeek API key 相关操作，不放主题切换。

## AI 多任务解析

- `DeepSeekPrompt` 要求同一句话中的每个独立任务或时间表达拆成单独 event。
- 关键场景：`明天中午11点提醒我打开电脑12点提醒我打开手机` 必须至少生成两条草稿/事件：
  - `打开电脑`，明天 11:00。
  - `打开手机`，明天 12:00。
- mock DeepSeek 路径在文本包含 `打开电脑` 和 `打开手机` 时会返回上述两条事件，便于本地 UI 流程测试。

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

- `versionCode = 3`
- `versionName = "0.1.2"`
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

## 工作习惯

- 用户通常希望直接完成实现、测试、打包、提交和推送。
- 每个完成批次用简短 commit message，并在回复里给中文摘要。
- 修改后优先跑相关 Gradle 测试；涉及 UI/流程时尽量跑 `connectedDebugAndroidTest`。
- 打包 APK 后检查签名、16KB 对齐、版本号和 Android 16 安装启动。
