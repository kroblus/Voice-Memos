package com.voicememos.aicalendar.data

import com.voicememos.aicalendar.domain.ParseHistoryCreationStatus
import com.voicememos.aicalendar.domain.ParseHistoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {
    @Test
    fun deleteParseHistoryRemovesOnlyMatchingId() = runTest {
        val dao = FakeAppSettingDao()
        val repository = SettingsRepository(dao)
        repository.addParseHistory(historyItem(id = "first"))
        repository.addParseHistory(historyItem(id = "second"))

        repository.deleteParseHistory("first")

        assertEquals(listOf("second"), dao.parseHistory().map { it.id })
    }

    @Test
    fun updateCreationResultAndUndoneStatePreservesOtherHistoryData() = runTest {
        val dao = FakeAppSettingDao()
        val repository = SettingsRepository(dao)
        repository.addParseHistory(historyItem(id = "target"))

        repository.updateParseHistoryCreationResult(
            id = "target",
            status = ParseHistoryCreationStatus.PARTIAL_SUCCESS,
            createdItemIds = listOf("created-1", "", "created-1", "created-2"),
            failureReason = "Reminder scheduling failed",
        )
        repository.updateParseHistoryUndone(id = "target", isUndone = true)

        val partiallyCreated = dao.parseHistory().single()
        assertEquals("Transcript target", partiallyCreated.transcript)
        assertEquals(ParseHistoryCreationStatus.PARTIAL_SUCCESS, partiallyCreated.creationStatus)
        assertEquals(listOf("created-1", "created-2"), partiallyCreated.createdItemIds)
        assertEquals("Reminder scheduling failed", partiallyCreated.failureReason)
        assertTrue(partiallyCreated.isUndone)

        repository.updateParseHistoryCreationResult(
            id = "target",
            status = ParseHistoryCreationStatus.SUCCESS,
            createdItemIds = listOf("created-3"),
            failureReason = "Ignored for success",
        )

        val successful = dao.parseHistory().single()
        assertEquals(ParseHistoryCreationStatus.SUCCESS, successful.creationStatus)
        assertEquals(listOf("created-3"), successful.createdItemIds)
        assertNull(successful.failureReason)
        assertFalse(successful.isUndone)
    }

    @Test
    fun addParseHistoryKeepsLatestFiftyAndClearRemovesAll() = runTest {
        val dao = FakeAppSettingDao()
        val repository = SettingsRepository(dao)

        repeat(55) { index ->
            repository.addParseHistory(historyItem(id = "history-$index"))
        }

        val retained = dao.parseHistory()
        assertEquals(50, retained.size)
        assertEquals("history-54", retained.first().id)
        assertEquals("history-5", retained.last().id)

        repository.clearParseHistory()

        assertTrue(dao.parseHistory().isEmpty())
    }

    private fun historyItem(id: String): ParseHistoryItem =
        ParseHistoryItem(
            id = id,
            transcript = "Transcript $id",
            summary = "Summary $id",
            draftCount = 1,
            createdAtEpochMillis = 1_789_000_000_000,
        )

    private class FakeAppSettingDao : AppSettingDao {
        private val values = MutableStateFlow<Map<String, String>>(emptyMap())

        override suspend fun set(setting: AppSettingEntity) {
            values.value = values.value + (setting.key to setting.value)
        }

        override fun observeValue(key: String): Flow<String?> =
            values.map { current -> current[key] }

        override suspend fun getValue(key: String): String? = values.value[key]

        override suspend fun deleteAll() {
            values.value = emptyMap()
        }

        fun parseHistory(): List<ParseHistoryItem> =
            values.value["parse_history"].let(::decodeParseHistory)
    }
}
