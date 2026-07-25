# 声历

声历是一款语音优先的本地 AI 日历 Android 应用。用户说出一段自然语言后，可以先编辑完整转写，再由 DeepSeek 整理为一个或多个事件、提醒或待办；模糊信息会被标出，用户预览确认后才写入应用内日历。

## 当前范围

- Kotlin + Jetpack Compose 原生 Android 应用
- 自定义应用内日历，不同步系统或 Google 日历
- 用户在设置中自填 DeepSeek API key
- API key 仅保存在本机 Android Keystore 加密存储中
- 语音输入使用 Android `SpeechRecognizer`
- 本地事项使用 Room 保存，并明确区分 `EVENT`、`REMINDER`、`TODO`
- 关键时间缺失或歧义时阻止创建，冲突和过去时间需要显式确认
- 多事项逐项保存，失败项可重试，同一会话使用幂等键防止重复创建
- 创建结果可撤销；识别历史提供编辑原文、重新解析、查看结果和删除
- 每个事件默认包含 `0` 分钟准点提醒
- 无精确闹钟能力时会明确降级为近似提醒；重启、升级和时区变化后重建未来提醒
- 支持 JSON 备份、JSON 导入和 ICS 导出
- 隐式系统备份已关闭，应用只使用显式本地备份
- `compileSdk` / `targetSdk` 36，当前版本 `0.2.0`

## 设计稿

- 商用完整设计稿：[docs/commercial-design-language.md](docs/commercial-design-language.md)
- UI 执行规范：[docs/ui-style-guide.md](docs/ui-style-guide.md)

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

语音任务流审计、迁移、权限边界和验收结果见：

- [docs/voice-workflow-audit-and-plan.md](docs/voice-workflow-audit-and-plan.md)
- [docs/voice-workflow-delivery-report.md](docs/voice-workflow-delivery-report.md)

真实 DeepSeek 设备流程测试需要在仓库根目录放置已忽略的 `deepseekapi.txt`，然后运行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -PrunRealDeepSeekTests=true --console=plain
```

## Release 签名

Release 构建会优先读取根目录未提交的 `keystore.properties`：

```properties
storeFile=D:/path/to/release.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

也可以使用环境变量：

```text
SHENGLI_RELEASE_STORE_FILE
SHENGLI_RELEASE_STORE_PASSWORD
SHENGLI_RELEASE_KEY_ALIAS
SHENGLI_RELEASE_KEY_PASSWORD
```

未配置正式证书时，release 会使用 debug signing config 产出内测 APK；正式上架前必须配置 release keystore。

APK 输出位置：

```text
app/build/outputs/apk/release/app-release.apk
app/build/outputs/apk/debug/app-debug.apk
```

## Codex 项目记忆

后续 Codex 对话接手本项目时，先阅读 [docs/codex-project-memory.md](docs/codex-project-memory.md)。不要提交或打印真实 DeepSeek key，也不要提交 `deepseekapi.txt`。
