package com.voicememos.aicalendar.ui

import android.content.Context
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.app.AlarmManager
import android.Manifest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicememos.aicalendar.ai.DeepSeekCalendarParser
import com.voicememos.aicalendar.data.CalendarRepository
import com.voicememos.aicalendar.data.SettingsRepository
import com.voicememos.aicalendar.data.decodeCalendarBackup
import com.voicememos.aicalendar.data.exportCalendarBackup
import com.voicememos.aicalendar.domain.DraftCalendarEvent
import com.voicememos.aicalendar.domain.EventStatus
import com.voicememos.aicalendar.domain.ParseHistoryItem
import com.voicememos.aicalendar.domain.RepeatFrequency
import com.voicememos.aicalendar.reminder.ReminderScheduler
import com.voicememos.aicalendar.security.SecureApiKeyStore
import com.voicememos.aicalendar.widget.ShengliWidgetProvider
import java.io.InterruptedIOException
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CalendarViewModel(
    private val appContext: Context,
    private val calendarRepository: CalendarRepository,
    private val settingsRepository: SettingsRepository,
    private val secureApiKeyStore: SecureApiKeyStore,
    private val parser: DeepSeekCalendarParser,
    private val reminderScheduler: ReminderScheduler,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        CalendarUiState(apiKeySaved = secureApiKeyStore.hasKey()),
    )
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            calendarRepository.observeAllActive().collect { events ->
                _uiState.update { it.copy(events = events) }
            }
        }
        viewModelScope.launch {
            settingsRepository.observeThemeMode()
                .distinctUntilChanged()
                .collect { value ->
                    _uiState.update {
                        it.copy(themeMode = runCatching { AppThemeMode.valueOf(value.orEmpty()) }
                            .getOrDefault(AppThemeMode.LIGHT))
                    }
                }
        }
        viewModelScope.launch {
            settingsRepository.observeOnboardingSeen()
                .distinctUntilChanged()
                .collect { seen ->
                    _uiState.update { it.copy(onboardingSeen = seen) }
                }
        }
        viewModelScope.launch {
            settingsRepository.observeDefaultReminderMinutes()
                .distinctUntilChanged()
                .collect { value ->
                    _uiState.update {
                        it.copy(defaultReminderMinutesInput = value?.toIntOrNull()?.coerceAtLeast(0)?.toString() ?: "10")
                    }
                }
        }
        viewModelScope.launch {
            settingsRepository.observeDefaultDurationMinutes()
                .distinctUntilChanged()
                .collect { value ->
                    _uiState.update {
                        it.copy(defaultDurationMinutesInput = value?.toIntOrNull()?.coerceAtLeast(1)?.toString() ?: "30")
                    }
                }
        }
        viewModelScope.launch {
            settingsRepository.observeParseHistory()
                .distinctUntilChanged()
                .collect { history ->
                    _uiState.update { it.copy(parseHistory = history) }
                }
        }
        refreshPermissionStatus()
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun selectToday() {
        _uiState.update { it.copy(selectedDate = LocalDate.now(zoneId)) }
    }

    fun setViewMode(mode: CalendarViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode.name)
        }
    }

    fun completeOnboarding() {
        if (!saveUserSettings(closeSettings = false)) return
        viewModelScope.launch {
            settingsRepository.setOnboardingSeen(true)
        }
        _uiState.update {
            it.copy(
                onboardingSeen = true,
                showSettings = false,
                errorMessage = null,
                successMessage = "准备好了",
            )
        }
    }

    fun refreshPermissionStatus() {
        val microphoneStatus = when {
            !appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE) -> PermissionHealth.UNAVAILABLE
            appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED ->
                PermissionHealth.READY
            else -> PermissionHealth.NEEDS_ACTION
        }
        val notificationStatus = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            PermissionHealth.READY
        } else if (appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            PermissionHealth.READY
        } else {
            PermissionHealth.NEEDS_ACTION
        }
        val exactAlarmStatus = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            PermissionHealth.READY
        } else {
            val alarmManager = appContext.getSystemService(AlarmManager::class.java)
            if (alarmManager.canScheduleExactAlarms()) PermissionHealth.READY else PermissionHealth.NEEDS_ACTION
        }
        _uiState.update {
            it.copy(
                microphonePermission = microphoneStatus,
                notificationPermission = notificationStatus,
                exactAlarmPermission = exactAlarmStatus,
            )
        }
    }

    fun openAppSettings() {
        startSettingsIntent(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${appContext.packageName}")),
        )
    }

    fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${appContext.packageName}"))
        }
        startSettingsIntent(intent)
    }

    fun openExactAlarmSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.parse("package:${appContext.packageName}"))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${appContext.packageName}"))
        }
        startSettingsIntent(intent)
    }

    fun updateTextInput(value: String) {
        _uiState.update { it.copy(textInput = value, partialTranscript = "", errorMessage = null) }
    }

    fun reuseTranscript(value: String) {
        _uiState.update { it.copy(textInput = value, partialTranscript = "", errorMessage = null) }
    }

    fun regenerateTranscript(value: String) {
        _uiState.update { it.copy(textInput = value, partialTranscript = "", errorMessage = null) }
        parseText(value)
    }

    fun onListeningChanged(value: Boolean) {
        _uiState.update { it.copy(isListening = value) }
    }

    fun onPartialTranscript(value: String) {
        _uiState.update { it.copy(partialTranscript = value) }
    }

    fun onFinalTranscript(value: String) {
        _uiState.update { it.copy(textInput = value, partialTranscript = "") }
        parseText(value)
    }

    fun showError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun parseCurrentText() {
        val state = _uiState.value
        parseText(state.partialTranscript.ifBlank { state.textInput })
    }

    fun parseText(rawText: String) {
        val transcript = rawText.trim()
        if (transcript.isEmpty()) {
            showError("先说一句或输入一段日程。")
            return
        }

        viewModelScope.launch {
            val apiKey = secureApiKeyStore.read()
            if (apiKey.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        showSettings = true,
                        errorMessage = "先在设置里保存 DeepSeek API key。",
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(isParsing = true, warnings = emptyList(), errorMessage = null)
            }

            runCatching {
                parser.parse(
                    transcript = transcript,
                    apiKey = apiKey,
                    defaultDurationMinutes = settingsRepository.getDefaultDurationMinutes(),
                    defaultReminderMinutes = settingsRepository.getDefaultReminderMinutes(),
                    zoneId = zoneId,
                )
            }.onSuccess { result ->
                settingsRepository.addParseHistory(result.toHistoryItem(transcript))
                _uiState.update {
                    it.copy(
                        isParsing = false,
                        draftEditors = result.events.map { draft -> draft.toEditor(zoneId) },
                        showDraftSheet = result.events.isNotEmpty(),
                        warnings = result.warnings,
                        errorMessage = if (result.events.isEmpty()) {
                            result.warnings.firstOrNull() ?: "没有解析出可创建的日程。"
                        } else {
                            null
                        },
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isParsing = false,
                        errorMessage = error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun updateDraft(draftId: String, transform: (DraftEditor) -> DraftEditor) {
        _uiState.update { state ->
            state.copy(
                draftEditors = state.draftEditors.map { draft ->
                    if (draft.draftId == draftId) transform(draft) else draft
                },
            )
        }
    }

    fun discardDraft(draftId: String) {
        _uiState.update { state ->
            val remaining = state.draftEditors.filterNot { it.draftId == draftId }
            state.copy(
                draftEditors = remaining,
                showDraftSheet = state.showDraftSheet && remaining.isNotEmpty(),
            )
        }
    }

    fun clearDrafts() {
        _uiState.update { it.copy(draftEditors = emptyList(), showDraftSheet = false, warnings = emptyList()) }
    }

    fun showDraftSheet() {
        _uiState.update {
            if (it.draftEditors.isEmpty()) it else it.copy(showDraftSheet = true)
        }
    }

    fun hideDraftSheet() {
        _uiState.update { it.copy(showDraftSheet = false) }
    }

    fun confirmDraft(draftId: String) {
        val editor = _uiState.value.draftEditors.firstOrNull { it.draftId == draftId } ?: return
        val draft = editor.toDraft(zoneId)
        if (!draft.canCreate) {
            showError("这个草稿还缺少时间或标题。")
            return
        }

        viewModelScope.launch {
            val events = draft.expandRepeats().map { occurrence ->
                calendarRepository.createFromDraft(occurrence).also(reminderScheduler::schedule)
            }
            _uiState.update { state ->
                val remainingDrafts = state.draftEditors.filterNot { it.draftId == draftId }
                state.copy(
                    selectedDate = events.first().startAt.atZone(zoneId).toLocalDate(),
                    draftEditors = remainingDrafts,
                    showDraftSheet = remainingDrafts.isNotEmpty(),
                    textInput = "",
                    partialTranscript = "",
                    errorMessage = null,
                    successMessage = "已保存 ${events.size} 项",
                )
            }
            ShengliWidgetProvider.updateAll(appContext)
        }
    }

    fun confirmAllDrafts() {
        val editors = _uiState.value.draftEditors
        val preparedDrafts = editors.map { editor -> editor to editor.toDraft(zoneId) }
        val creatableDrafts = preparedDrafts.filter { (_, draft) -> draft.canCreate }
        if (creatableDrafts.isEmpty()) {
            showError("有草稿缺少标题或时间，请先编辑。")
            return
        }

        viewModelScope.launch {
            val savedEvents = creatableDrafts.flatMap { (_, draft) ->
                draft.expandRepeats().map { occurrence ->
                    calendarRepository.createFromDraft(occurrence).also(reminderScheduler::schedule)
                }
            }
            val savedDraftIds = creatableDrafts.map { (editor, _) -> editor.draftId }.toSet()
            _uiState.update { state ->
                val remainingDrafts = state.draftEditors.filterNot { it.draftId in savedDraftIds }
                state.copy(
                    selectedDate = savedEvents.first().startAt.atZone(zoneId).toLocalDate(),
                    draftEditors = remainingDrafts,
                    showDraftSheet = remainingDrafts.isNotEmpty(),
                    textInput = if (remainingDrafts.isEmpty()) "" else state.textInput,
                    partialTranscript = if (remainingDrafts.isEmpty()) "" else state.partialTranscript,
                    warnings = if (remainingDrafts.isEmpty()) emptyList() else state.warnings,
                    errorMessage = if (remainingDrafts.isEmpty()) {
                        null
                    } else {
                        "还有 ${remainingDrafts.size} 个草稿缺少标题或时间。"
                    },
                    successMessage = "已保存 ${savedEvents.size} 项",
                )
            }
            ShengliWidgetProvider.updateAll(appContext)
        }
    }

    fun markDone(eventId: String) {
        _uiState.value.events.firstOrNull { it.id == eventId }?.let { event ->
            reminderScheduler.cancel(event.id, event.reminderMinutes)
        }
        viewModelScope.launch {
            calendarRepository.updateStatus(eventId, EventStatus.DONE)
            ShengliWidgetProvider.updateAll(appContext)
        }
    }

    fun deleteEvent(eventId: String) {
        _uiState.value.events.firstOrNull { it.id == eventId }?.let { event ->
            reminderScheduler.cancel(event.id, event.reminderMinutes)
        }
        viewModelScope.launch {
            calendarRepository.delete(eventId)
            ShengliWidgetProvider.updateAll(appContext)
        }
    }

    fun openEventEditor(eventId: String) {
        val event = _uiState.value.events.firstOrNull { it.id == eventId } ?: return
        _uiState.update { it.copy(eventEditor = event.toEditor(zoneId), errorMessage = null) }
    }

    fun updateEventEditor(transform: (EventEditor) -> EventEditor) {
        _uiState.update { state ->
            state.copy(eventEditor = state.eventEditor?.let(transform))
        }
    }

    fun closeEventEditor() {
        _uiState.update { it.copy(eventEditor = null, errorMessage = null) }
    }

    fun saveEventEditor() {
        val editor = _uiState.value.eventEditor ?: return
        val original = _uiState.value.events.firstOrNull { it.id == editor.eventId } ?: return
        val start = editor.parsedStart(zoneId)
        val end = editor.parsedEnd(zoneId)
        if (editor.title.isBlank() || start == null) {
            showError("事件需要标题和开始时间。")
            return
        }
        if (end != null && end.isBefore(start)) {
            showError("结束时间不能早于开始时间。")
            return
        }

        viewModelScope.launch {
            val updated = calendarRepository.updateDetails(
                original = original,
                title = editor.title,
                notes = editor.notes,
                startAt = start,
                endAt = end,
                allDay = editor.allDay,
                reminderMinutes = editor.parsedReminders(),
            )
            reminderScheduler.cancel(original.id, original.reminderMinutes)
            if (updated.status == EventStatus.SCHEDULED) {
                reminderScheduler.schedule(updated)
            }
            _uiState.update {
                it.copy(
                    selectedDate = updated.startAt.atZone(zoneId).toLocalDate(),
                    eventEditor = null,
                    errorMessage = null,
                    successMessage = "事件已更新",
                )
            }
            ShengliWidgetProvider.updateAll(appContext)
        }
    }

    fun updateAgendaQuery(value: String) {
        _uiState.update { it.copy(agendaQuery = value) }
    }

    fun setAgendaFilter(filter: AgendaFilter) {
        _uiState.update { it.copy(agendaFilter = filter) }
    }

    fun clearCompletedEvents() {
        val completed = _uiState.value.events.count { it.status == EventStatus.DONE }
        if (completed == 0) return
        viewModelScope.launch {
            calendarRepository.deleteCompleted()
            _uiState.update { it.copy(successMessage = "已清理 $completed 项") }
            ShengliWidgetProvider.updateAll(appContext)
        }
    }

    fun clearParseHistory() {
        viewModelScope.launch {
            settingsRepository.clearParseHistory()
            _uiState.update { it.copy(successMessage = "复核历史已清空") }
        }
    }

    fun exportBackup(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val payload = exportCalendarBackup(_uiState.value.events)
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { output ->
                        output.bufferedWriter(Charsets.UTF_8).use { writer ->
                            writer.write(payload)
                        }
                    } ?: error("无法写入备份文件")
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        backupMessage = "备份已导出",
                        successMessage = "备份已导出",
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                showError(error.message ?: "导出失败，请重试。")
            }
        }
    }

    fun importBackup(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val raw = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
                    } ?: error("无法读取备份文件")
                }
                val result = decodeCalendarBackup(raw)
                val importedCount = calendarRepository.importEvents(result.events)
                result.events
                    .filter { it.status == EventStatus.SCHEDULED }
                    .forEach(reminderScheduler::schedule)
                importedCount to result.skippedCount
            }.onSuccess { (importedCount, skippedCount) ->
                _uiState.update {
                    it.copy(
                        backupMessage = "已导入 $importedCount 项",
                        successMessage = if (skippedCount > 0) {
                            "已导入 $importedCount 项，跳过 $skippedCount 项"
                        } else {
                            "已导入 $importedCount 项"
                        },
                        errorMessage = null,
                    )
                }
                ShengliWidgetProvider.updateAll(appContext)
            }.onFailure { error ->
                showError(error.message ?: "导入失败，请检查备份文件。")
            }
        }
    }

    fun showPrivacyDetails() {
        _uiState.update { it.copy(showPrivacyDetails = true, showSettings = false) }
    }

    fun hidePrivacyDetails() {
        _uiState.update { it.copy(showPrivacyDetails = false) }
    }

    fun requestResetConfirmation() {
        _uiState.update { it.copy(showResetConfirmation = true) }
    }

    fun dismissResetConfirmation() {
        _uiState.update { it.copy(showResetConfirmation = false) }
    }

    fun deleteAllLocalData() {
        val existingEvents = _uiState.value.events
        viewModelScope.launch {
            existingEvents.forEach { event ->
                reminderScheduler.cancel(event.id, event.reminderMinutes)
            }
            calendarRepository.deleteAll()
            settingsRepository.clearAll()
            secureApiKeyStore.clear()
            _uiState.update {
                CalendarUiState(
                    apiKeySaved = false,
                    successMessage = "本地数据已清空",
                    microphonePermission = it.microphonePermission,
                    notificationPermission = it.notificationPermission,
                    exactAlarmPermission = it.exactAlarmPermission,
                )
            }
            ShengliWidgetProvider.updateAll(appContext)
        }
    }

    fun openSettings() {
        refreshPermissionStatus()
        _uiState.update { it.copy(showSettings = true, apiKeyInput = "") }
    }

    fun closeSettings() {
        _uiState.update { it.copy(showSettings = false, apiKeyInput = "", errorMessage = null) }
    }

    fun updateApiKeyInput(value: String) {
        _uiState.update { it.copy(apiKeyInput = value) }
    }

    fun updateDefaultReminderMinutesInput(value: String) {
        _uiState.update { it.copy(defaultReminderMinutesInput = value.filter(Char::isDigit)) }
    }

    fun updateDefaultDurationMinutesInput(value: String) {
        _uiState.update { it.copy(defaultDurationMinutesInput = value.filter(Char::isDigit)) }
    }

    fun saveApiKey() {
        saveUserSettings(closeSettings = true)
        return
        val state = _uiState.value
        val defaultReminder = state.defaultReminderMinutesInput.toIntOrNull()
        val defaultDuration = state.defaultDurationMinutesInput.toIntOrNull()
        if (defaultReminder == null || defaultDuration == null || defaultDuration <= 0) {
            showError("默认提醒和默认时长需要填写有效数字。")
            return
        }
        state.apiKeyInput.trim().takeIf { it.isNotEmpty() }?.let(secureApiKeyStore::save)
        viewModelScope.launch {
            settingsRepository.setDefaultReminderMinutes(defaultReminder ?: 10)
            settingsRepository.setDefaultDurationMinutes(defaultDuration ?: 30)
        }
        _uiState.update {
            it.copy(
                apiKeyInput = "",
                apiKeySaved = secureApiKeyStore.hasKey(),
                showSettings = false,
                errorMessage = null,
                successMessage = "设置已保存",
            )
        }
    }

    fun clearApiKey() {
        secureApiKeyStore.clear()
        _uiState.update {
            it.copy(
                apiKeyInput = "",
                apiKeySaved = false,
                showSettings = false,
                errorMessage = null,
            )
        }
    }

    fun consumeSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }

    private fun saveUserSettings(closeSettings: Boolean): Boolean {
        val state = _uiState.value
        val defaultReminder = state.defaultReminderMinutesInput.toIntOrNull()
        val defaultDuration = state.defaultDurationMinutesInput.toIntOrNull()
        if (defaultReminder == null || defaultDuration == null || defaultDuration <= 0) {
            showError("默认提醒和默认时长需要填写有效数字。")
            return false
        }
        state.apiKeyInput.trim().takeIf { it.isNotEmpty() }?.let(secureApiKeyStore::save)
        viewModelScope.launch {
            settingsRepository.setDefaultReminderMinutes(defaultReminder)
            settingsRepository.setDefaultDurationMinutes(defaultDuration)
        }
        _uiState.update {
            it.copy(
                apiKeyInput = "",
                apiKeySaved = secureApiKeyStore.hasKey(),
                showSettings = if (closeSettings) false else it.showSettings,
                errorMessage = null,
                successMessage = "设置已保存",
            )
        }
        return true
    }

    private fun startSettingsIntent(intent: Intent) {
        runCatching {
            appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            showError("无法打开系统设置，请从系统设置里找到声历。")
        }
    }

    private fun Throwable.toUserMessage(): String =
        when (this) {
            is InterruptedIOException -> "DeepSeek 响应超时，请检查网络后重试。"
            else -> message?.takeIf { it.isNotBlank() } ?: "解析失败，请稍后再试。"
        }

    private fun com.voicememos.aicalendar.domain.AiParseResult.toHistoryItem(transcript: String): ParseHistoryItem {
        val titleSummary = events
            .map { it.title.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(3)
            .joinToString("、")
        val extraCount = (events.size - 3).takeIf { it > 0 }?.let { " 等 $it 项" }.orEmpty()
        return ParseHistoryItem(
            id = UUID.randomUUID().toString(),
            transcript = transcript,
            summary = when {
                titleSummary.isNotBlank() -> titleSummary + extraCount
                warnings.isNotEmpty() -> warnings.first()
                else -> "未生成可保存草稿"
            },
            draftCount = events.size,
            createdAtEpochMillis = System.currentTimeMillis(),
        )
    }

    private fun DraftCalendarEvent.expandRepeats(): List<DraftCalendarEvent> {
        val repeat = repeatRule ?: return listOf(this)
        val start = startAt ?: return listOf(this)
        val count = repeat.normalizedCount
        return (0 until count).map { index ->
            val offset = when (repeat.frequency) {
                RepeatFrequency.DAILY -> Duration.ofDays(index.toLong())
                RepeatFrequency.WEEKLY -> Duration.ofDays(index.toLong() * 7)
            }
            copy(
                startAt = start.plus(offset),
                endAt = endAt?.plus(offset),
                notes = listOfNotNull(notes, repeat.summary()).joinToString("\n").takeIf { it.isNotBlank() },
            )
        }
    }
}
