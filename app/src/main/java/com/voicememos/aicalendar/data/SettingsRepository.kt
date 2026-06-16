package com.voicememos.aicalendar.data

import kotlinx.coroutines.flow.Flow

class SettingsRepository(
    private val dao: AppSettingDao,
) {
    fun observeDefaultReminderMinutes(): Flow<String?> = dao.observeValue(DEFAULT_REMINDER_MINUTES)

    fun observeThemeMode(): Flow<String?> = dao.observeValue(THEME_MODE)

    suspend fun setThemeMode(value: String) {
        dao.set(AppSettingEntity(THEME_MODE, value))
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

    private companion object {
        const val DEFAULT_REMINDER_MINUTES = "default_reminder_minutes"
        const val DEFAULT_DURATION_MINUTES = "default_duration_minutes"
        const val THEME_MODE = "theme_mode"
    }
}
