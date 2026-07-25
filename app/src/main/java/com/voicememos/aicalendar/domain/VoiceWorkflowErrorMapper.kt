package com.voicememos.aicalendar.domain

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.UnknownHostException
import java.util.Locale

object VoiceWorkflowErrorMapper {
    fun fromThrowable(error: Throwable): VoiceWorkflowError {
        val causes = error.causeChain()

        if (causes.any { it is InterruptedIOException || it.javaClass.simpleName.contains("Timeout") }) {
            return VoiceWorkflowError.TIMEOUT
        }

        causes.firstNotNullOfOrNull { cause ->
            cause.message?.let(::httpStatusFromText)
        }?.let { statusCode ->
            return fromHttpStatus(statusCode)
        }

        if (
            causes.any {
                it is UnknownHostException ||
                    it is ConnectException ||
                    it is NoRouteToHostException ||
                    it is SocketException
            }
        ) {
            return VoiceWorkflowError.NETWORK
        }

        causes.firstNotNullOfOrNull { cause ->
            errorFromClassName(cause.javaClass.simpleName)
        }?.let { classified ->
            return classified
        }

        causes.firstNotNullOfOrNull { cause ->
            cause.message?.take(CLASSIFICATION_TEXT_LIMIT)?.let(::errorFromSafeText)
        }?.let { classified ->
            return classified
        }

        return if (causes.any { it is IOException }) {
            VoiceWorkflowError.NETWORK
        } else {
            VoiceWorkflowError.UNKNOWN
        }
    }

    fun fromHttpStatus(statusCode: Int): VoiceWorkflowError =
        when (statusCode) {
            401, 403 -> VoiceWorkflowError.API_KEY
            408, 504 -> VoiceWorkflowError.TIMEOUT
            429 -> VoiceWorkflowError.RATE_LIMIT
            in 500..599 -> VoiceWorkflowError.SERVER
            else -> VoiceWorkflowError.UNKNOWN
        }

    fun fromHttpText(httpText: String): VoiceWorkflowError {
        val safePrefix = httpText.take(CLASSIFICATION_TEXT_LIMIT)
        val statusCode = httpStatusFromText(safePrefix)
        if (statusCode != null) return fromHttpStatus(statusCode)
        return errorFromSafeText(safePrefix) ?: VoiceWorkflowError.UNKNOWN
    }

    private fun errorFromClassName(simpleName: String): VoiceWorkflowError? {
        val normalized = simpleName.lowercase(Locale.ROOT)
        return when {
            normalized.contains("serialization") ||
                normalized.contains("jsondecoding") ||
                normalized.contains("malformedjson") -> VoiceWorkflowError.INVALID_AI_CONTENT

            normalized.contains("sqlite") ||
                normalized.contains("database") ||
                normalized.contains("room") ||
                normalized.contains("filesystem") -> VoiceWorkflowError.STORAGE

            else -> null
        }
    }

    private fun errorFromSafeText(text: String): VoiceWorkflowError? {
        val normalized = text.lowercase(Locale.ROOT)
        return when {
            normalized.containsAny(
                "no voice content",
                "empty transcript",
                "speech input was empty",
                "没有识别到语音",
            ) -> VoiceWorkflowError.VOICE_EMPTY

            normalized.containsAny(
                "api key",
                "unauthorized",
                "authentication failed",
                "invalid credential",
                "权限不足",
            ) -> VoiceWorkflowError.API_KEY

            normalized.containsAny(
                "rate limit",
                "too many requests",
                "请求过于频繁",
            ) -> VoiceWorkflowError.RATE_LIMIT

            normalized.containsAny(
                "timeout",
                "timed out",
                "超时",
            ) -> VoiceWorkflowError.TIMEOUT

            normalized.containsAny(
                "microphone",
                "record_audio",
                "audio record",
                "麦克风",
            ) -> VoiceWorkflowError.MICROPHONE_UNAVAILABLE

            normalized.containsAny(
                "notification",
                "exact alarm",
                "alarm permission",
                "通知",
            ) -> VoiceWorkflowError.NOTIFICATION_NOT_SCHEDULED

            normalized.containsAny(
                "invalid json",
                "malformed json",
                "invalid ai content",
                "ai returned empty",
                "empty ai response",
                "无效 json",
            ) -> VoiceWorkflowError.INVALID_AI_CONTENT

            normalized.containsAny(
                "sqlite",
                "database",
                "storage",
                "disk full",
                "存储",
                "数据库",
            ) -> VoiceWorkflowError.STORAGE

            normalized.containsAny(
                "service unavailable",
                "server error",
                "服务暂时不可用",
                "服务器",
            ) -> VoiceWorkflowError.SERVER

            normalized.containsAny(
                "network",
                "offline",
                "connection refused",
                "connection reset",
                "网络",
            ) -> VoiceWorkflowError.NETWORK

            normalized.containsAny(
                "missing field",
                "ambiguous",
                "clarification",
                "缺失",
                "歧义",
            ) -> VoiceWorkflowError.MISSING_OR_AMBIGUOUS

            normalized.containsAny(
                "past time",
                "in the past",
                "已过去",
            ) -> VoiceWorkflowError.PAST_TIME

            normalized.containsAny(
                "schedule conflict",
                "time conflict",
                "overlap",
                "冲突",
                "重叠",
            ) -> VoiceWorkflowError.CONFLICT

            else -> null
        }
    }

    private fun httpStatusFromText(text: String): Int? {
        val safePrefix = text.take(CLASSIFICATION_TEXT_LIMIT)
        return HTTP_STATUS_PATTERN.find(safePrefix)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: STATUS_CODE_PATTERN.find(safePrefix)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
    }

    private fun Throwable.causeChain(): List<Throwable> {
        val result = mutableListOf<Throwable>()
        var current: Throwable? = this
        while (
            current != null &&
            result.size < MAX_CAUSE_DEPTH &&
            result.none { seen -> seen === current }
        ) {
            result += current
            current = current.cause
        }
        return result
    }

    private fun String.containsAny(vararg candidates: String): Boolean =
        candidates.any(::contains)

    private const val CLASSIFICATION_TEXT_LIMIT = 512
    private const val MAX_CAUSE_DEPTH = 8
    private val HTTP_STATUS_PATTERN =
        Regex("""(?i)\bHTTP(?:/\d(?:\.\d)?)?\s*[:=]?\s*(\d{3})\b""")
    private val STATUS_CODE_PATTERN =
        Regex("""(?i)\bstatus(?:\s+code)?\s*[:=]\s*(\d{3})\b""")
}
