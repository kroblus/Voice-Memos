package com.voicememos.aicalendar.data

import com.voicememos.aicalendar.domain.ParseHistoryItem
import com.voicememos.aicalendar.domain.ParseHistoryCreationStatus
import com.voicememos.aicalendar.domain.ParseHistoryScheduleItemSummary
import com.voicememos.aicalendar.domain.ScheduleItemType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun encodeAndDecodeRoundTripsExtendedHistoryFields() {
        val history = listOf(
            ParseHistoryItem(
                id = "extended-history",
                transcript = "Tomorrow at nine remind me to call Alex, then meet at ten.",
                summary = "Call Alex, meet",
                draftCount = 2,
                createdAtEpochMillis = 1_789_000_000_000,
                itemSummaries = listOf(
                    ParseHistoryScheduleItemSummary(
                        itemType = ScheduleItemType.REMINDER,
                        title = "Call Alex",
                        timeSummary = "2026-09-10 09:00 Asia/Shanghai",
                        location = null,
                        missingFields = emptyList(),
                        ambiguousFields = listOf("contact"),
                    ),
                    ParseHistoryScheduleItemSummary(
                        itemType = ScheduleItemType.EVENT,
                        title = "Meet Alex",
                        timeSummary = "2026-09-10 10:00-11:00 Asia/Shanghai",
                        location = "Office",
                        missingFields = listOf("attendees"),
                        ambiguousFields = emptyList(),
                    ),
                ),
                creationStatus = ParseHistoryCreationStatus.PARTIAL_SUCCESS,
                failureReason = "One item could not be created",
                createdItemIds = listOf("created-1"),
                isUndone = true,
            ),
        )

        val decoded = decodeParseHistory(encodeParseHistory(history))

        assertEquals(history, decoded)
        assertEquals(history.single().createdAtEpochMillis, decoded.single().parsedAtEpochMillis)
        assertEquals(history.single().draftCount, decoded.single().parsedItemCount)
    }

    @Test
    fun decodeLegacyHistoryUsesDefaultsForExtendedFields() {
        val legacyJson = """
            [
              {
                "id": "legacy-history",
                "transcript": "Legacy transcript",
                "summary": "Legacy summary",
                "draftCount": 2,
                "createdAtEpochMillis": 123456789
              }
            ]
        """.trimIndent()

        val decoded = decodeParseHistory(legacyJson).single()

        assertEquals("legacy-history", decoded.id)
        assertEquals("Legacy transcript", decoded.transcript)
        assertEquals(2, decoded.parsedItemCount)
        assertEquals(123456789, decoded.parsedAtEpochMillis)
        assertTrue(decoded.itemSummaries.isEmpty())
        assertEquals(ParseHistoryCreationStatus.NOT_ATTEMPTED, decoded.creationStatus)
        assertNull(decoded.failureReason)
        assertTrue(decoded.createdItemIds.isEmpty())
        assertFalse(decoded.isUndone)
    }
}
