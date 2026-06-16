package com.voicememos.aicalendar.ai

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekCalendarParserTest {
    private val zoneId: ZoneId = ZoneId.of("Asia/Shanghai")
    private val clock: Clock = Clock.fixed(Instant.parse("2026-06-16T01:00:00Z"), zoneId)

    @Test
    fun sendsDeepSeekJsonContractAndMapsEvents() = runTest {
        val interceptor = CapturingInterceptor(
            responseBody = completionResponse(
                """{"events":[{"title":"给张三打电话","start_local":"2026-06-17T09:00:00","confidence":0.91}],"warnings":[]}""",
            ),
        )
        val parser = parserWith(interceptor)

        val result = parser.parse(
            transcript = "明天上午九点提醒我给张三打电话",
            apiKey = "unit-test-key",
            zoneId = zoneId,
        )

        assertEquals(1, result.events.size)
        assertEquals("给张三打电话", result.events.single().title)
        assertEquals(listOf(0, 10), result.events.single().reminderMinutes)
        assertTrue(result.events.single().canCreate)
        assertTrue(result.needsConfirmation)

        val requestBody = interceptor.requestBody
        assertEquals("Bearer unit-test-key", interceptor.authorization)
        assertTrue(requestBody.contains(""""model":"deepseek-v4-flash""""))
        assertTrue(requestBody.contains(""""thinking":{"type":"disabled"}"""))
        assertTrue(requestBody.contains(""""response_format":{"type":"json_object"}"""))
        assertTrue(requestBody.contains(""""max_tokens":1200"""))
        assertTrue(requestBody.contains(""""stream":false"""))
        assertTrue(requestBody.contains("明天中午11点提醒我打开电脑12点提醒我打开手机"))
        assertTrue(requestBody.contains("Split this transcript into as many event objects"))
    }

    @Test
    fun returnsConfirmationWarningWhenDeepSeekContentIsEmpty() = runTest {
        val parser = parserWith(
            CapturingInterceptor(responseBody = completionResponse("")),
        )

        val result = parser.parse(
            transcript = "明天提醒我",
            apiKey = "unit-test-key",
            zoneId = zoneId,
        )

        assertTrue(result.events.isEmpty())
        assertTrue(result.needsConfirmation)
        assertTrue(result.warnings.single().contains("空内容"))
    }

    @Test
    fun acceptsJsonWrappedInMarkdownFence() = runTest {
        val parser = parserWith(
            CapturingInterceptor(
                responseBody = completionResponse(
                    """
                    ```json
                    {"events":[{"title":"牙医","start_local":"2026-06-19T15:00:00","end_local":"2026-06-19T16:00:00","confidence":0.9}],"warnings":[]}
                    ```
                    """.trimIndent(),
                ),
            ),
        )

        val result = parser.parse(
            transcript = "下周五下午三点到四点牙医",
            apiKey = "unit-test-key",
            zoneId = zoneId,
        )

        assertEquals("牙医", result.events.single().title)
        assertTrue(result.events.single().canCreate)
    }

    @Test
    fun returnsConfirmationWarningWhenDeepSeekContentIsInvalidJson() = runTest {
        val parser = parserWith(
            CapturingInterceptor(responseBody = completionResponse("not-json")),
        )

        val result = parser.parse(
            transcript = "明天提醒我",
            apiKey = "unit-test-key",
            zoneId = zoneId,
        )

        assertTrue(result.events.isEmpty())
        assertTrue(result.needsConfirmation)
        assertTrue(result.warnings.single().contains("有效 JSON"))
    }

    @Test
    fun includesHttpStatusWhenDeepSeekRejectsRequest() = runTest {
        val parser = parserWith(
            CapturingInterceptor(
                code = 401,
                responseBody = """{"error":{"message":"bad key"}}""",
            ),
        )

        val error = runCatching {
            parser.parse(
                transcript = "明天提醒我",
                apiKey = "unit-test-key",
                zoneId = zoneId,
            )
        }.exceptionOrNull()

        assertTrue(error is IOException)
        assertTrue(error?.message.orEmpty().contains("HTTP 401"))
        assertTrue(error?.message.orEmpty().contains("API key"))
    }

    private fun parserWith(interceptor: CapturingInterceptor): DeepSeekCalendarParser =
        DeepSeekCalendarParser(
            client = OkHttpClient.Builder()
                .addInterceptor(interceptor)
                .build(),
            clock = clock,
        )

    private fun completionResponse(content: String): String =
        """{"choices":[{"message":{"role":"assistant","content":"${content.escapeJsonString()}"}}]}"""

    private fun String.escapeJsonString(): String =
        replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")

    private class CapturingInterceptor(
        private val code: Int = 200,
        private val responseBody: String,
    ) : Interceptor {
        var requestBody: String = ""
            private set
        var authorization: String? = null
            private set

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            authorization = request.header("Authorization")
            request.body?.let { body ->
                val buffer = Buffer()
                body.writeTo(buffer)
                requestBody = buffer.readString(StandardCharsets.UTF_8)
            }
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code in 200..299) "OK" else "Error")
                .body(responseBody.toResponseBody("application/json; charset=utf-8".toMediaType()))
                .build()
        }
    }
}
