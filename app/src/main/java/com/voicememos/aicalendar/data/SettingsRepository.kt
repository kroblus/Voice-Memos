package com.voicememos.aicalendar.data

import com.voicememos.aicalendar.domain.ParseHistoryItem
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

    fun observeParseHistory(): Flow<List<ParseHistoryItem>> =
        dao.observeValue(PARSE_HISTORY).map(::decodeParseHistory)

    suspend fun setThemeMode(value: String) {
        dao.set(AppSettingEntity(THEME_MODE, value))
    }

    suspend fun setOnboardingSeen(value: Boolean) {
        dao.set(AppSettingEntity(ONBOARDING_SEEN, value.toString()))
    }

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
        val updated = (listOf(item) + existing.filterNot { it.transcript == item.transcript })
            .take(MAX_PARSE_HISTORY)
        dao.set(AppSettingEntity(PARSE_HISTORY, encodeParseHistory(updated)))
    }

    suspend fun clearParseHistory() {
        dao.set(AppSettingEntity(PARSE_HISTORY, encodeParseHistory(emptyList())))
    }

    suspend fun clearAll() {
        dao.deleteAll()
    }

    private companion object {
        const val DEFAULT_REMINDER_MINUTES = "default_reminder_minutes"
        const val DEFAULT_DURATION_MINUTES = "default_duration_minutes"
        const val THEME_MODE = "theme_mode"
        const val ONBOARDING_SEEN = "onboarding_seen"
        const val PARSE_HISTORY = "parse_history"
        const val MAX_PARSE_HISTORY = 5
    }
}
