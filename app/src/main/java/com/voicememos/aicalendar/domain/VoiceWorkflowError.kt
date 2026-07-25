package com.voicememos.aicalendar.domain

enum class VoiceWorkflowSettingsTarget {
    MICROPHONE,
    API_KEY,
    NOTIFICATIONS,
}

enum class VoiceWorkflowError(
    val userMessage: String,
    val isRetryable: Boolean,
    val settingsTarget: VoiceWorkflowSettingsTarget? = null,
) {
    VOICE_EMPTY(
        userMessage = "没有识别到语音内容，请再说一次或改用文字输入。",
        isRetryable = true,
    ),
    MICROPHONE_UNAVAILABLE(
        userMessage = "麦克风不可用，请检查麦克风权限后重试。",
        isRetryable = false,
        settingsTarget = VoiceWorkflowSettingsTarget.MICROPHONE,
    ),
    NETWORK(
        userMessage = "网络连接不可用，请检查网络后重试。",
        isRetryable = true,
    ),
    TIMEOUT(
        userMessage = "请求超时，请检查网络后重试。",
        isRetryable = true,
    ),
    API_KEY(
        userMessage = "API key 无效或未配置，请在设置中重新保存。",
        isRetryable = false,
        settingsTarget = VoiceWorkflowSettingsTarget.API_KEY,
    ),
    RATE_LIMIT(
        userMessage = "请求过于频繁，请稍后再试。",
        isRetryable = true,
    ),
    SERVER(
        userMessage = "服务暂时不可用，请稍后再试。",
        isRetryable = true,
    ),
    INVALID_AI_CONTENT(
        userMessage = "AI 返回内容无法识别，请重新生成或改用文字编辑。",
        isRetryable = true,
    ),
    MISSING_OR_AMBIGUOUS(
        userMessage = "事项仍有缺失或歧义信息，请确认后再保存。",
        isRetryable = false,
    ),
    PAST_TIME(
        userMessage = "事项时间已经过去，请修改时间后再保存。",
        isRetryable = false,
    ),
    CONFLICT(
        userMessage = "该事件与现有日程时间冲突，请确认或调整时间。",
        isRetryable = false,
    ),
    NOTIFICATION_NOT_SCHEDULED(
        userMessage = "事项已保存，但通知未能安排，请检查通知和闹钟权限。",
        isRetryable = true,
        settingsTarget = VoiceWorkflowSettingsTarget.NOTIFICATIONS,
    ),
    STORAGE(
        userMessage = "本机保存失败，请检查存储空间后重试。",
        isRetryable = true,
    ),
    UNKNOWN(
        userMessage = "操作未完成，请稍后再试。",
        isRetryable = true,
    ),
    ;

    val shouldOpenSettings: Boolean
        get() = settingsTarget != null
}
