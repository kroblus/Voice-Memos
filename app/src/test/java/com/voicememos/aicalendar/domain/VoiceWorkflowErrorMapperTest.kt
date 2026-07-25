package com.voicememos.aicalendar.domain

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceWorkflowErrorMapperTest {
    @Test
    fun everyErrorHasReadableMessageAndExpectedActionMetadata() {
        val settingsErrors = mapOf(
            VoiceWorkflowError.MICROPHONE_UNAVAILABLE to VoiceWorkflowSettingsTarget.MICROPHONE,
            VoiceWorkflowError.API_KEY to VoiceWorkflowSettingsTarget.API_KEY,
            VoiceWorkflowError.NOTIFICATION_NOT_SCHEDULED to VoiceWorkflowSettingsTarget.NOTIFICATIONS,
        )
        val retryableErrors = setOf(
            VoiceWorkflowError.VOICE_EMPTY,
            VoiceWorkflowError.NETWORK,
            VoiceWorkflowError.TIMEOUT,
            VoiceWorkflowError.RATE_LIMIT,
            VoiceWorkflowError.SERVER,
            VoiceWorkflowError.INVALID_AI_CONTENT,
            VoiceWorkflowError.NOTIFICATION_NOT_SCHEDULED,
            VoiceWorkflowError.STORAGE,
            VoiceWorkflowError.UNKNOWN,
        )

        VoiceWorkflowError.entries.forEach { error ->
            assertTrue(error.userMessage.isNotBlank())
            assertEquals(error in retryableErrors, error.isRetryable)
            assertEquals(error in settingsErrors, error.shouldOpenSettings)
            assertEquals(settingsErrors[error], error.settingsTarget)
        }
    }

    @Test
    fun mapsHttpStatusCodesWithoutKeepingResponseContent() {
        assertEquals(VoiceWorkflowError.API_KEY, VoiceWorkflowErrorMapper.fromHttpStatus(401))
        assertEquals(VoiceWorkflowError.API_KEY, VoiceWorkflowErrorMapper.fromHttpStatus(403))
        assertEquals(VoiceWorkflowError.TIMEOUT, VoiceWorkflowErrorMapper.fromHttpStatus(408))
        assertEquals(VoiceWorkflowError.TIMEOUT, VoiceWorkflowErrorMapper.fromHttpStatus(504))
        assertEquals(VoiceWorkflowError.RATE_LIMIT, VoiceWorkflowErrorMapper.fromHttpStatus(429))
        assertEquals(VoiceWorkflowError.SERVER, VoiceWorkflowErrorMapper.fromHttpStatus(503))
        assertEquals(VoiceWorkflowError.UNKNOWN, VoiceWorkflowErrorMapper.fromHttpStatus(400))

        val sensitiveText =
            "DeepSeek request failed: HTTP 401, transcript=private appointment, token=secret-token"
        val mapped = VoiceWorkflowErrorMapper.fromHttpText(sensitiveText)

        assertEquals(VoiceWorkflowError.API_KEY, mapped)
        assertFalse(mapped.userMessage.contains("private appointment"))
        assertFalse(mapped.toString().contains("secret-token"))
    }

    @Test
    fun mapsTimeoutAndNetworkThrowableTypes() {
        assertEquals(
            VoiceWorkflowError.TIMEOUT,
            VoiceWorkflowErrorMapper.fromThrowable(SocketTimeoutException("read timed out")),
        )
        assertEquals(
            VoiceWorkflowError.NETWORK,
            VoiceWorkflowErrorMapper.fromThrowable(UnknownHostException("api.deepseek.com")),
        )
        assertEquals(
            VoiceWorkflowError.NETWORK,
            VoiceWorkflowErrorMapper.fromThrowable(ConnectException("connection refused")),
        )
        assertEquals(
            VoiceWorkflowError.NETWORK,
            VoiceWorkflowErrorMapper.fromThrowable(IOException()),
        )
    }

    @Test
    fun throwableHttpMessageMapsThroughNestedCauses() {
        val error = IllegalStateException(
            "request wrapper",
            IOException("DeepSeek 请求失败：HTTP 429，请求过于频繁。"),
        )

        assertEquals(
            VoiceWorkflowError.RATE_LIMIT,
            VoiceWorkflowErrorMapper.fromThrowable(error),
        )
    }

    @Test
    fun mapsSafeMessageSignalsToWorkflowCategories() {
        val cases = listOf(
            IllegalStateException("No voice content was recognized") to VoiceWorkflowError.VOICE_EMPTY,
            SecurityException("RECORD_AUDIO microphone permission denied") to
                VoiceWorkflowError.MICROPHONE_UNAVAILABLE,
            IllegalArgumentException("Invalid JSON returned by AI") to
                VoiceWorkflowError.INVALID_AI_CONTENT,
            IllegalArgumentException("Required field is ambiguous") to
                VoiceWorkflowError.MISSING_OR_AMBIGUOUS,
            IllegalArgumentException("The selected start is in the past") to
                VoiceWorkflowError.PAST_TIME,
            IllegalStateException("Schedule conflict: intervals overlap") to
                VoiceWorkflowError.CONFLICT,
            IllegalStateException("Exact alarm notification was not scheduled") to
                VoiceWorkflowError.NOTIFICATION_NOT_SCHEDULED,
            IllegalStateException("Database is locked") to VoiceWorkflowError.STORAGE,
            IllegalStateException("Server error") to VoiceWorkflowError.SERVER,
        )

        cases.forEach { (throwable, expected) ->
            assertEquals(expected, VoiceWorkflowErrorMapper.fromThrowable(throwable))
        }
    }

    @Test
    fun unknownThrowableDoesNotExposeItsSensitiveMessage() {
        val secret = "Meet a confidential client at 09:00"
        val mapped = VoiceWorkflowErrorMapper.fromThrowable(IllegalStateException(secret))

        assertEquals(VoiceWorkflowError.UNKNOWN, mapped)
        assertEquals("操作未完成，请稍后再试。", mapped.userMessage)
        assertFalse(mapped.userMessage.contains(secret))
        assertFalse(mapped.toString().contains(secret))
    }

    @Test
    fun httpTextSupportsStatusCodeAndKeywordFallbacks() {
        assertEquals(
            VoiceWorkflowError.SERVER,
            VoiceWorkflowErrorMapper.fromHttpText("status code: 502"),
        )
        assertEquals(
            VoiceWorkflowError.API_KEY,
            VoiceWorkflowErrorMapper.fromHttpText("authentication failed"),
        )
        assertEquals(
            VoiceWorkflowError.UNKNOWN,
            VoiceWorkflowErrorMapper.fromHttpText("unexpected upstream response"),
        )
    }

    @Test
    fun classNameBasedMappingHandlesDecodeAndStorageFailures() {
        assertEquals(
            VoiceWorkflowError.INVALID_AI_CONTENT,
            VoiceWorkflowErrorMapper.fromThrowable(FakeJsonDecodingException()),
        )
        assertEquals(
            VoiceWorkflowError.STORAGE,
            VoiceWorkflowErrorMapper.fromThrowable(FakeDatabaseException()),
        )
    }

    private class FakeJsonDecodingException : RuntimeException()

    private class FakeDatabaseException : RuntimeException()
}
