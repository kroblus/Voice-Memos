# 声历

声历是一个语音优先的自定义日历 Android 应用。用户说出一段自然语言，应用使用系统语音识别转文字，再调用 DeepSeek 将文本解析成一个或多个日程草稿，确认后写入应用内日历。

## Codex 项目记忆

后续 Codex 对话接手本项目时，先阅读 [docs/codex-project-memory.md](docs/codex-project-memory.md)。里面记录了当前 UI/多任务解析状态、关键文件、验证命令、APK 位置和不能提交 DeepSeek key 的约束。

## 当前范围

- Kotlin + Jetpack Compose 原生 Android 应用
- 自定义应用内日历，不同步系统/Google 日历
- 用户在设置中自填 DeepSeek API key
- DeepSeek API key 只保存在本机加密存储中
- 语音输入使用 Android SpeechRecognizer
- 本地事件使用 Room 保存
- 每个事件默认在开始时间弹出本机通知，也可保留提前提醒

## 常用命令

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

完整本地检查：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest --console=plain
```

真实 DeepSeek 设备流程测试需要在仓库根目录放置已被忽略的 `deepseekapi.txt`，然后运行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -PrunRealDeepSeekTests=true --console=plain
```

APK 输出位置：

```text
app/build/outputs/apk/release/app-release.apk
app/build/outputs/apk/debug/app-debug.apk
```

本项目的 release 包使用本机 debug signing config 签名，适合内测安装；正式发布前应替换为正式签名。
Android 13+ 首次保存事件时需要允许通知权限；Android 12+ 如果系统限制精确提醒，应用会退化为近似提醒。

如果本机缺少 Android SDK，请先用 Android Studio 安装 SDK Platform 36、Build Tools 35.0.0 和 Platform Tools。
