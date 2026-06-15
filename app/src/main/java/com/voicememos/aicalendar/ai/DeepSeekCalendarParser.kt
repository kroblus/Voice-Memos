package com.voicememos.aicalendar.ai

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
        .callTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json {
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
            throw IOException("DeepSeek request failed: HTTP ${response.code}")
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

        val parsed = runCatching { json.decodeFromString<AiCalendarResponse>(content) }
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

    private companion object {
        const val DEEPSEEK_CHAT_COMPLETIONS_URL = "https://api.deepseek.com/chat/completions"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

@Serializable
private data class ChatCompletionRequest(
    val model: String = "deepseek-v4-flash",
    val messages: List<ChatMessage>,
    @SerialName("response_format") val responseFormat: ResponseFormat = ResponseFormat(),
    @SerialName("max_tokens") val maxTokens: Int = 1200,
    val stream: Boolean = false,
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

