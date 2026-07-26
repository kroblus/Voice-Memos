package com.voicememos.aicalendar.data

import com.voicememos.aicalendar.domain.ParseHistoryItem
import com.voicememos.aicalendar.domain.ParseHistoryCreationStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(
    private val dao: AppSettingDao,
) {
    fun observeDefaultReminderMinutes(): Flow<String?> = dao.observeValue(DEFAULT_REMINDER_MINUTES)

    fun observeDefaultDurationMinutes(): Flow<String?> = dao.observeValue(DEFAULT_DURATION_MINUTES)

    fun observeThemeMode(): Flow<String?> = dao.observeValue(THEME_MODE)

    fun observeOnboardingSeen(): Flow<Boolean> =
        dao.observeValue(ONBOARDING_SEEN).map { it == "true" }

    fun observeSystemCalendarEnabled(): Flow<Boolean> =
        dao.observeValue(SYSTEM_CALENDAR_ENABLED).map { it == "true" }

    fun observeParseHistory(): Flow<List<ParseHistoryItem>> =
        dao.observeValue(PARSE_HISTORY).map(::decodeParseHistory)

    suspend fun setThemeMode(value: String) {
        dao.set(AppSettingEntity(THEME_MODE, value))
    }

    suspend fun setOnboardingSeen(value: Boolean) {
        dao.set(AppSettingEntity(ONBOARDING_SEEN, value.toString()))
    }

    suspend fun setSystemCalendarEnabled(value: Boolean) {
        dao.set(AppSettingEntity(SYSTEM_CALENDAR_ENABLED, value.toString()))
    }

    suspend fun isSystemCalendarEnabled(): Boolean =
        dao.getValue(SYSTEM_CALENDAR_ENABLED) == "true"

    suspend fun setDefaultReminderMinutes(value: Int) {
        dao.set(AppSettingEntity(DEFAULT_REMINDER_MINUTES, value.coerceAtLeast(0).toString()))
    }

    suspend fun getDefaultReminderMinutes(): Int =
        dao.getValue(DEFAULT_REMINDER_MINUTES)?.toIntOrNull() ?: 10

    suspend fun getDefaultDurationMinutes(): Int =
        dao.getValue(DEFAULT_DURATION_MINUTES)?.toIntOrNull() ?: 30

    suspend fun setDefaultDurationMinutes(value: Int) {
        dao.set(AppSettingEntity(DEFAULT_DURATION_MINUTES, value.coerceAtLeast(1).toString()))
    }

    suspend fun addParseHistory(item: ParseHistoryItem) {
        val existing = decodeParseHistory(dao.getValue(PARSE_HISTORY))
        val updated = (
            listOf(item) + existing.filterNot {
                it.id == item.id || it.transcript == item.transcript
            }
        )
            .take(MAX_PARSE_HISTORY)
        dao.set(AppSettingEntity(PARSE_HISTORY, encodeParseHistory(updated)))
    }

    suspend fun deleteParseHistory(id: String) {
        updateParseHistory { history -> history.filterNot { it.id == id } }
    }

    suspend fun updateParseHistoryCreationResult(
        id: String,
        status: ParseHistoryCreationStatus,
        createdItemIds: List<String> = emptyList(),
        failureReason: String? = null,
    ) {
        updateParseHistory { history ->
            history.map { item ->
                if (item.id != id) {
                    item
                } else {
                    item.copy(
                        creationStatus = status,
                        createdItemIds = when (status) {
                            ParseHistoryCreationStatus.NOT_ATTEMPTED,
                            ParseHistoryCreationStatus.FAILURE,
                            -> emptyList()

                            ParseHistoryCreationStatus.SUCCESS,
                            ParseHistoryCreationStatus.PARTIAL_SUCCESS,
                            -> createdItemIds.filter(String::isNotBlank).distinct()
                        },
                        failureReason = when (status) {
                            ParseHistoryCreationStatus.NOT_ATTEMPTED,
                            ParseHistoryCreationStatus.SUCCESS,
                            -> null

                            ParseHistoryCreationStatus.FAILURE,
                            ParseHistoryCreationStatus.PARTIAL_SUCCESS,
                            -> failureReason?.takeIf(String::isNotBlank)
                        },
                        isUndone = false,
                    )
                }
            }
        }
    }

    suspend fun updateParseHistoryUndone(id: String, isUndone: Boolean) {
        updateParseHistory { history ->
            history.map { item ->
                if (item.id == id) item.copy(isUndone = isUndone) else item
            }
        }
    }

    suspend fun clearParseHistory() {
        dao.set(AppSettingEntity(PARSE_HISTORY, encodeParseHistory(emptyList())))
    }

    suspend fun clearAll() {
        dao.deleteAll()
    }

    private suspend fun updateParseHistory(
        transform: (List<ParseHistoryItem>) -> List<ParseHistoryItem>,
    ) {
        val existing = decodeParseHistory(dao.getValue(PARSE_HISTORY))
        val updated = transform(existing).take(MAX_PARSE_HISTORY)
        if (updated != existing) {
            dao.set(AppSettingEntity(PARSE_HISTORY, encodeParseHistory(updated)))
        }
    }

    private companion object {
        const val DEFAULT_REMINDER_MINUTES = "default_reminder_minutes"
        const val DEFAULT_DURATION_MINUTES = "default_duration_minutes"
        const val THEME_MODE = "theme_mode"
        const val ONBOARDING_SEEN = "onboarding_seen"
        const val SYSTEM_CALENDAR_ENABLED = "system_calendar_enabled"
        const val PARSE_HISTORY = "parse_history"
        const val MAX_PARSE_HISTORY = 50
    }
}
