package com.voicememos.aicalendar.data

import com.voicememos.aicalendar.domain.ParseHistoryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseHistoryCodecTest {
    @Test
    fun encodeAndDecodeRoundTripsHistoryItems() {
        val history = listOf(
            ParseHistoryItem(
                id = "history-1",
                transcript = "明天11点打开电脑\n12点打开手机",
                summary = "打开电脑、打开手机",
                draftCount = 2,
                createdAtEpochMillis = 1_787_000_000_000,
            ),
            ParseHistoryItem(
                id = "history-2",
                transcript = "每天9点喝水",
                summary = "喝水",
                draftCount = 7,
                createdAtEpochMillis = 1_787_000_010_000,
            ),
        )

        val decoded = decodeParseHistory(encodeParseHistory(history))

        assertEquals(history, decoded)
    }

    @Test
    fun decodeInvalidHistoryReturnsEmptyList() {
        assertTrue(decodeParseHistory("not json").isEmpty())
        assertTrue(decodeParseHistory(null).isEmpty())
    }
}
