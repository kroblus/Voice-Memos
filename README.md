# 声历

声历是一个语音优先的自定义日历 Android 应用。用户说出一段自然语言，应用使用系统语音识别转文字，再调用 DeepSeek 将文本解析成一个或多个日程草稿，确认后写入应用内日历。

## 当前范围

- Kotlin + Jetpack Compose 原生 Android 应用
- 自定义应用内日历，不同步系统/Google 日历
- 用户在设置中自填 DeepSeek API key
- DeepSeek API key 只保存在本机加密存储中
- 语音输入使用 Android SpeechRecognizer
- 本地事件使用 Room 保存

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

Debug APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

如果本机缺少 Android SDK，请先用 Android Studio 安装 SDK Platform 36、Build Tools 35.0.0 和 Platform Tools。
