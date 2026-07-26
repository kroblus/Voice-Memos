package com.voicememos.aicalendar.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.voicememos.aicalendar.ai.DeepSeekCalendarParser
import com.voicememos.aicalendar.calendar.AndroidCalendarMirrorGateway
import com.voicememos.aicalendar.data.AppDatabase
import com.voicememos.aicalendar.data.CalendarRepository
import com.voicememos.aicalendar.data.SettingsRepository
import com.voicememos.aicalendar.reminder.ReminderCoordinator
import com.voicememos.aicalendar.reminder.ReminderScheduler
import com.voicememos.aicalendar.security.SecureApiKeyStore
import com.voicememos.aicalendar.ui.CalendarViewModel

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.get(appContext)
    private val calendarRepository = CalendarRepository(
        dao = database.calendarEventDao(),
        tombstoneDao = database.calendarMirrorTombstoneDao(),
    )
    private val settingsRepository = SettingsRepository(database.appSettingDao())
    private val secureApiKeyStore = SecureApiKeyStore(appContext)
    private val parser = DeepSeekCalendarParser()
    private val reminderScheduler = ReminderScheduler(appContext)
    private val calendarMirrorGateway = AndroidCalendarMirrorGateway(appContext)
    val reminderCoordinator = ReminderCoordinator(
        context = appContext,
        calendarRepository = calendarRepository,
        settingsRepository = settingsRepository,
        calendarMirrorGateway = calendarMirrorGateway,
        localReminderGateway = reminderScheduler,
    )

    fun calendarViewModelFactory(): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(CalendarViewModel::class.java)) {
                    return CalendarViewModel(
                        appContext = appContext,
                        calendarRepository = calendarRepository,
                        settingsRepository = settingsRepository,
                        secureApiKeyStore = secureApiKeyStore,
                        parser = parser,
                        reminderCoordinator = reminderCoordinator,
                        calendarMirrorGateway = calendarMirrorGateway,
                    ) as T
                }
                error("Unknown ViewModel class: ${modelClass.name}")
            }
        }
}
