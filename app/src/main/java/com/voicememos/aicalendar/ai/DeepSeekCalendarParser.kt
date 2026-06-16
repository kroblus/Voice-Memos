package com.voicememos.aicalendar.ai

import com.voicememos.aicalendar.BuildConfig
import com.voicememos.aicalendar.domain.AiParseResult
import java.io.IOException
import java.time.Clock
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class DeepSeekCalendarParser(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    },
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    suspend fun parse(
        transcript: String,
        apiKey: String,
        defaultDurationMinutes: Int = 30,
        defaultReminderMinutes: Int = 10,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): AiParseResult = withContext(Dispatchers.IO) {
        if (BuildConfig.DEBUG && apiKey.trim() == MOCK_API_KEY) {
            return@withContext mockParseResult(
                transcript = transcript,
                zoneId = zoneId,
                defaultDurationMinutes = defaultDurationMinutes,
                defaultReminderMinutes = defaultReminderMinutes,
            )
        }

        val requestBody = ChatCompletionRequest(
            messages = listOf(
                ChatMessage(role = "system", content = DeepSeekPrompt.systemPrompt()),
                ChatMessage(
                    role = "user",
                    content = DeepSeekPrompt.userPrompt(
                        transcript = transcript,
                        now = clock.instant(),
                        zoneId = zoneId,
                        defaultDurationMinutes = defaultDurationMinutes,
                        defaultReminderMinutes = defaultReminderMinutes,
                    ),
                ),
            ),
        )

        val request = Request.Builder()
            .url(DEEPSEEK_CHAT_COMPLETIONS_URL)
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .addHeader("Content-Type", JSON_MEDIA_TYPE.toString())
            .post(json.encodeToString(requestBody).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw IOException(deepSeekErrorMessage(response.code, body))
        }

        val content = json.decodeFromString<ChatCompletionResponse>(body)
            .choices
            .firstOrNull()
            ?.message
            ?.content
            ?.trim()
            .orEmpty()
        if (content.isEmpty()) {
            return@withContext AiParseResult(
                events = emptyList(),
                warnings = listOf("DeepSeek 返回了空内容，请稍后重试或改用文字输入。"),
                needsConfirmation = true,
            )
        }

        val parsed = runCatching { json.decodeFromString<AiCalendarResponse>(content.unwrapJsonContent()) }
            .getOrElse {
                return@withContext AiParseResult(
                    events = emptyList(),
                    warnings = listOf("DeepSeek 返回内容不是有效 JSON，请编辑原文后重试。"),
                    needsConfirmation = true,
                )
            }

        AiResponseMapper.map(
            response = parsed,
            transcript = transcript,
            zoneId = zoneId,
            defaultDurationMinutes = defaultDurationMinutes,
            defaultReminderMinutes = defaultReminderMinutes,
        )
    }

    private fun deepSeekErrorMessage(code: Int, body: String): String =
        when (code) {
            401, 403 -> "DeepSeek 请求失败：HTTP $code，API key 无效或权限不足，请在设置里重新保存。"
            429 -> "DeepSeek 请求失败：HTTP 429，请求过于频繁，请稍后再试。"
            in 500..599 -> "DeepSeek 请求失败：HTTP $code，服务暂时不可用，请稍后再试。"
            else -> "DeepSeek 请求失败：HTTP $code，${body.take(ERROR_BODY_LIMIT)}"
        }

    private fun String.unwrapJsonContent(): String {
        val trimmed = trim()
        if (!trimmed.startsWith("```")) return trimmed

        val lines = trimmed.lines()
        val firstLine = lines.firstOrNull()?.trim().orEmpty()
        val lastLine = lines.lastOrNull()?.trim().orEmpty()
        return if (firstLine.startsWith("```") && lastLine == "```" && lines.size >= 3) {
            lines.drop(1).dropLast(1).joinToString("\n").trim()
        } else {
            trimmed
        }
    }

    private fun mockParseResult(
        transcript: String,
        zoneId: ZoneId,
        defaultDurationMinutes: Int,
        defaultReminderMinutes: Int,
    ): AiParseResult {
        val baseDate = clock.instant().atZone(zoneId).toLocalDate().plusDays(1)
        val response = AiCalendarResponse(
            events = listOf(
                AiCalendarEvent(
                    title = "给张三打电话",
                    notes = "由 mock DeepSeek 回应生成",
                    startLocal = "${baseDate}T09:00:00",
                    endLocal = "${baseDate}T09:30:00",
                    allDay = false,
                    reminderMinutes = listOf(defaultReminderMinutes),
                    confidence = 0.95,
                    missingFields = emptyList(),
                ),
                AiCalendarEvent(
                    title = "项目会",
                    notes = "由 mock DeepSeek 回应生成",
                    startLocal = "${baseDate}T15:00:00",
                    endLocal = "${baseDate}T15:30:00",
                    allDay = false,
                    reminderMinutes = listOf(defaultReminderMinutes),
                    confidence = 0.93,
                    missingFields = emptyList(),
                ),
            ),
            warnings = listOf("使用本地 mock DeepSeek 回应。"),
        )

        return AiResponseMapper.map(
            response = response,
            transcript = transcript,
            zoneId = zoneId,
            defaultDurationMinutes = defaultDurationMinutes,
            defaultReminderMinutes = defaultReminderMinutes,
        )
    }

    private companion object {
        const val MOCK_API_KEY = "mock-deepseek"
        const val DEEPSEEK_CHAT_COMPLETIONS_URL = "https://api.deepseek.com/chat/completions"
        const val ERROR_BODY_LIMIT = 240
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

@Serializable
private data class ChatCompletionRequest(
    val model: String = "deepseek-v4-flash",
    val messages: List<ChatMessage>,
    val thinking: ThinkingConfig = ThinkingConfig(),
    @SerialName("response_format") val responseFormat: ResponseFormat = ResponseFormat(),
    @SerialName("max_tokens") val maxTokens: Int = 1200,
    val stream: Boolean = false,
)

@Serializable
private data class ThinkingConfig(
    val type: String = "disabled",
)

@Serializable
private data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class ResponseFormat(
    val type: String = "json_object",
)

@Serializable
private data class ChatCompletionResponse(
    val choices: List<ChatChoice> = emptyList(),
)

@Serializable
private data class ChatChoice(
    val message: ChatMessage,
)
