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
import com.voicememos.aicalendar.domain.CalendarEvent
import com.voicememos.aicalendar.domain.DraftCalendarEvent
import com.voicememos.aicalendar.domain.EventStatus
import com.voicememos.aicalendar.domain.ParseHistoryCreationStatus
import com.voicememos.aicalendar.domain.ParseHistoryItem
import com.voicememos.aicalendar.domain.ParseHistoryScheduleItemSummary
import com.voicememos.aicalendar.domain.ScheduleRiskDetector
import com.voicememos.aicalendar.domain.ScheduleItemType
import com.voicememos.aicalendar.domain.VoiceWorkflowErrorMapper
import com.voicememos.aicalendar.domain.VoiceWorkflowError
import com.voicememos.aicalendar.domain.expandOccurrences
import com.voicememos.aicalendar.reminder.ReminderScheduler
import com.voicememos.aicalendar.reminder.ScheduleResult
import com.voicememos.aicalendar.reminder.toNotificationStatus
import com.voicememos.aicalendar.security.SecureApiKeyStore
import com.voicememos.aicalendar.ui.session.CreationBatchSummary
import com.voicememos.aicalendar.ui.session.CreationItemOutcome
import com.voicememos.aicalendar.ui.session.CreationItemStatus
import com.voicememos.aicalendar.ui.session.VoiceScheduleSession
import com.voicememos.aicalendar.ui.session.VoiceTaskState
import com.voicememos.aicalendar.widget.ShengliWidgetProvider
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private var parseJob: Job? = null
    private var saveJob: Job? = null
    private var connectionTestJob: Job? = null
    private var lastCreatedEvents: List<CalendarEvent> = emptyList()
    private var lastCreationSessionId: String? = null
    private var returnToCreationResultAfterSettings = false

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

    fun openInputCurtain() {
        if (hasActiveVoiceWork()) {
            _uiState.update { it.copy(errorMessage = "当前任务仍在处理中，请稍候。") }
            return
        }
        val shouldStartFresh = _uiState.value.creationSummary != null &&
            _uiState.value.draftEditors.isEmpty()
        if (shouldStartFresh) {
            lastCreatedEvents = emptyList()
            lastCreationSessionId = null
        }
        _uiState.update { state ->
            val session = if (
                shouldStartFresh ||
                (
                    state.voiceTaskState == VoiceTaskState.Success &&
                        state.draftEditors.isEmpty()
                    )
            ) {
                VoiceScheduleSession.fresh()
            } else {
                state.voiceSession
            }
            state.copy(
                voiceSession = session,
                showInputCurtain = true,
                creationSummary = if (shouldStartFresh) null else state.creationSummary,
                showCreationResult = false,
                errorMessage = null,
            )
        }
    }

    fun closeInputCurtain() {
        _uiState.update { it.copy(showInputCurtain = false) }
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
        _uiState.update {
            it.copy(
                voiceSession = it.voiceSession.updateEditedTranscript(value),
                errorMessage = null,
                workflowError = null,
            )
        }
    }

    fun reuseTranscript(value: String) {
        if (hasActiveVoiceWork()) {
            _uiState.update { it.copy(errorMessage = "当前任务仍在处理中，暂时不能切换识别记录。") }
            return
        }
        lastCreatedEvents = emptyList()
        lastCreationSessionId = null
        _uiState.update {
            it.copy(
                voiceSession = VoiceScheduleSession.fresh().prepareTranscript(value),
                draftEditors = emptyList(),
                draftRiskMessages = emptyMap(),
                acceptedRiskDraftIds = emptySet(),
                showDraftSheet = false,
                creationSummary = null,
                showCreationResult = false,
                showHistory = false,
                showInputCurtain = true,
                errorMessage = null,
            )
        }
    }

    fun regenerateTranscript(value: String) {
        if (hasActiveVoiceWork()) {
            _uiState.update { it.copy(errorMessage = "当前任务仍在处理中，暂时不能重新解析。") }
            return
        }
        lastCreatedEvents = emptyList()
        lastCreationSessionId = null
        _uiState.update {
            it.copy(
                voiceSession = VoiceScheduleSession.fresh().prepareTranscript(value),
                draftEditors = emptyList(),
                draftRiskMessages = emptyMap(),
                acceptedRiskDraftIds = emptySet(),
                showDraftSheet = false,
                creationSummary = null,
                showCreationResult = false,
                showHistory = false,
                errorMessage = null,
            )
        }
        parseText(value)
    }

    fun onListeningChanged(value: Boolean) {
        _uiState.update { state ->
            if (value &&
                state.voiceTaskState in setOf(VoiceTaskState.Parsing, VoiceTaskState.Saving)
            ) {
                return@update state.copy(errorMessage = "当前任务仍在处理中，请稍候。")
            }
            val session = if (value) {
                val base = if (
                    state.voiceTaskState == VoiceTaskState.Success &&
                    state.draftEditors.isEmpty()
                ) {
                    VoiceScheduleSession.fresh()
                } else {
                    state.voiceSession
                }
                base.startListening()
            } else {
                state.voiceSession.beginTranscribing()
            }
            state.copy(
                voiceSession = session,
                errorMessage = if (value) null else state.errorMessage,
            )
        }
    }

    fun onPartialTranscript(value: String) {
        _uiState.update { state ->
            if (state.voiceTaskState in setOf(VoiceTaskState.Parsing, VoiceTaskState.Saving)) {
                state
            } else {
                state.copy(voiceSession = state.voiceSession.updatePartialTranscript(value))
            }
        }
    }

    fun onFinalTranscript(value: String) {
        _uiState.update { state ->
            if (state.voiceTaskState in setOf(VoiceTaskState.Parsing, VoiceTaskState.Saving)) {
                state
            } else {
                state.copy(
                    voiceSession = state.voiceSession.completeTranscript(value),
                    errorMessage = null,
                )
            }
        }
    }

    fun switchToTextInput() {
        _uiState.update { state ->
            if (state.voiceTaskState in setOf(VoiceTaskState.Parsing, VoiceTaskState.Saving)) {
                state
            } else {
                state.copy(
                    voiceSession = state.voiceSession.switchToTextInput(),
                    errorMessage = null,
                    workflowError = null,
                )
            }
        }
    }

    fun showError(message: String) {
        val classified = VoiceWorkflowErrorMapper.fromHttpText(message)
        _uiState.update { state ->
            val session = if (
                state.voiceTaskState in setOf(
                    VoiceTaskState.Listening,
                    VoiceTaskState.Transcribing,
                    VoiceTaskState.Parsing,
                    VoiceTaskState.Saving,
                )
            ) {
                state.voiceSession.fail(message)
            } else {
                state.voiceSession
            }
            state.copy(
                voiceSession = session,
                errorMessage = message,
                workflowError = classified.takeUnless { it == VoiceWorkflowError.UNKNOWN },
            )
        }
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
        if (parseJob?.isActive == true ||
            _uiState.value.voiceTaskState in setOf(
                VoiceTaskState.Listening,
                VoiceTaskState.Transcribing,
                VoiceTaskState.Parsing,
                VoiceTaskState.Saving,
            )
        ) {
            val message = if (
                _uiState.value.voiceTaskState in setOf(
                    VoiceTaskState.Listening,
                    VoiceTaskState.Transcribing,
                )
            ) {
                "正在完成转写，请稍候。"
            } else {
                "正在理解当前内容，请稍候。"
            }
            _uiState.update { it.copy(errorMessage = message) }
            return
        }

        _uiState.update {
            val prepared = it.voiceSession.prepareTranscript(transcript)
            it.copy(
                voiceSession = prepared.beginParsing(),
                draftEditors = emptyList(),
                draftRiskMessages = emptyMap(),
                acceptedRiskDraftIds = emptySet(),
                showDraftSheet = false,
                creationSummary = null,
                showCreationResult = false,
                errorMessage = null,
                workflowError = null,
            )
        }

        parseJob = viewModelScope.launch {
            try {
                val apiKey = secureApiKeyStore.read()
                if (apiKey.isNullOrBlank()) {
                    val message = "先在设置里保存 DeepSeek API key。"
                    _uiState.update {
                        it.copy(
                            voiceSession = it.voiceSession.fail(message),
                            showInputCurtain = false,
                            showSettings = true,
                            errorMessage = message,
                            workflowError = VoiceWorkflowError.API_KEY,
                        )
                    }
                    return@launch
                }

                val result = parser.parse(
                    transcript = transcript,
                    apiKey = apiKey,
                    defaultDurationMinutes = settingsRepository.getDefaultDurationMinutes(),
                    defaultReminderMinutes = settingsRepository.getDefaultReminderMinutes(),
                    zoneId = zoneId,
                )
                val editors = result.events.map { draft -> draft.toEditor(zoneId) }
                val historyId = _uiState.value.voiceSession.sessionId
                settingsRepository.addParseHistory(result.toHistoryItem(transcript, historyId))
                val sessionWarnings = (result.warnings + result.clarificationQuestions).distinct()
                val errorMessage = if (result.events.isEmpty()) {
                    sessionWarnings.firstOrNull() ?: "没有解析出可创建的日程。"
                } else {
                    null
                }
                _uiState.update {
                    val session = when {
                        result.events.isEmpty() -> it.voiceSession.fail(requireNotNull(errorMessage))
                        result.requiresClarification ||
                            result.events.any { draft -> draft.needsClarification } ->
                            it.voiceSession.needsClarification(sessionWarnings)
                        else -> it.voiceSession.showPreview(sessionWarnings)
                    }
                    it.copy(
                        voiceSession = session,
                        draftEditors = editors,
                        draftRiskMessages = assessDraftRisks(editors, it.events),
                        acceptedRiskDraftIds = emptySet(),
                        showDraftSheet = result.events.isNotEmpty(),
                        creationSummary = null,
                        showCreationResult = false,
                        showInputCurtain = false,
                        errorMessage = errorMessage,
                        workflowError = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val workflowError = VoiceWorkflowErrorMapper.fromThrowable(error)
                val message = workflowError.userMessage
                _uiState.update {
                    it.copy(
                        voiceSession = it.voiceSession.fail(message),
                        errorMessage = message,
                        workflowError = workflowError,
                    )
                }
            } finally {
                parseJob = null
            }
        }
    }

    fun updateDraft(draftId: String, transform: (DraftEditor) -> DraftEditor) {
        _uiState.update { state ->
            if (state.voiceTaskState == VoiceTaskState.Saving) return@update state
            val updatedDrafts = state.draftEditors.map { draft ->
                if (draft.draftId == draftId) transform(draft) else draft
            }
            val needsClarification = updatedDrafts.any { draft -> !draft.canCreate(zoneId) }
            state.copy(
                voiceSession = if (needsClarification) {
                    state.voiceSession.needsClarification(state.voiceSession.warnings)
                } else {
                    state.voiceSession.showPreview(state.voiceSession.warnings)
                },
                draftEditors = updatedDrafts,
                draftRiskMessages = assessDraftRisks(updatedDrafts, state.events),
                acceptedRiskDraftIds = state.acceptedRiskDraftIds - draftId,
                errorMessage = null,
            )
        }
    }

    fun discardDraft(draftId: String) {
        _uiState.update { state ->
            val remaining = state.draftEditors.filterNot { it.draftId == draftId }
            state.copy(
                voiceSession = when {
                    remaining.isEmpty() -> state.voiceSession.returnToTranscript()
                    remaining.any { draft -> !draft.canCreate(zoneId) } ->
                        state.voiceSession.needsClarification(state.voiceSession.warnings)
                    else -> state.voiceSession.showPreview(state.voiceSession.warnings)
                },
                draftEditors = remaining,
                draftRiskMessages = state.draftRiskMessages - draftId,
                acceptedRiskDraftIds = state.acceptedRiskDraftIds - draftId,
                showDraftSheet = state.showDraftSheet && remaining.isNotEmpty(),
                errorMessage = null,
            )
        }
    }

    fun clearDrafts() {
        _uiState.update {
            it.copy(
                voiceSession = it.voiceSession.returnToTranscript(),
                draftEditors = emptyList(),
                draftRiskMessages = emptyMap(),
                acceptedRiskDraftIds = emptySet(),
                showDraftSheet = false,
            )
        }
    }

    fun showDraftSheet() {
        _uiState.update {
            if (it.draftEditors.isEmpty()) it else it.copy(showDraftSheet = true)
        }
    }

    fun hideDraftSheet() {
        _uiState.update { it.copy(showDraftSheet = false) }
    }

    fun acceptDraftRisks(draftId: String) {
        _uiState.update { state ->
            if (state.draftRiskMessages[draftId].isNullOrEmpty()) {
                state
            } else {
                state.copy(
                    acceptedRiskDraftIds = state.acceptedRiskDraftIds + draftId,
                    errorMessage = null,
                )
            }
        }
    }

    fun confirmDraft(draftId: String) {
        saveDrafts(setOf(draftId))
    }

    fun confirmAllDrafts() {
        saveDrafts(_uiState.value.draftEditors.mapTo(linkedSetOf()) { it.draftId })
    }

    private fun saveDrafts(draftIds: Set<String>) {
        if (saveJob?.isActive == true || _uiState.value.voiceTaskState == VoiceTaskState.Saving) {
            _uiState.update { it.copy(errorMessage = "正在保存，请勿重复提交。") }
            return
        }
        val stateBeforeSave = _uiState.value
        if (
            stateBeforeSave.voiceTaskState !in setOf(
                VoiceTaskState.Preview,
                VoiceTaskState.NeedsClarification,
            )
        ) {
            _uiState.update { it.copy(errorMessage = "当前预览尚未就绪，请稍候。") }
            return
        }
        val editors = stateBeforeSave.draftEditors.filter { it.draftId in draftIds }
        if (editors.isEmpty()) return
        val preparedDrafts = editors.map { editor -> editor to editor.toDraft(zoneId) }
        val blockedEditors = editors.filterNot { stateBeforeSave.canCreateDraft(it, zoneId) }
        if (blockedEditors.isNotEmpty()) {
            val hasUnacceptedRisk = blockedEditors.any { editor ->
                !stateBeforeSave.draftRiskMessages[editor.draftId].isNullOrEmpty() &&
                    editor.draftId !in stateBeforeSave.acceptedRiskDraftIds
            }
            val message = if (hasUnacceptedRisk) {
                "仍有项目存在时间冲突或过去时间，请先确认风险。"
            } else {
                "仍有项目缺少关键信息或存在歧义，请先逐项确认。"
            }
            _uiState.update {
                it.copy(
                    voiceSession = it.voiceSession.needsClarification(listOf(message)),
                    errorMessage = message,
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                voiceSession = it.voiceSession.beginSaving(),
                showDraftSheet = false,
                showCreationResult = false,
                errorMessage = null,
                workflowError = null,
            )
        }
        saveJob = viewModelScope.launch {
            val savedEvents = mutableListOf<CalendarEvent>()
            val savedDraftIds = mutableSetOf<String>()
            val outcomes = mutableListOf<CreationItemOutcome>()
            val notificationAllowed = hasNotificationPermission()
            try {
                preparedDrafts.forEach { (editor, draft) ->
                    val eventsForDraft = mutableListOf<CalendarEvent>()
                    val schedulingWarnings = mutableListOf<String>()
                    try {
                        draft.expandOccurrences().forEachIndexed { occurrenceIndex, occurrence ->
                            val event = calendarRepository.createFromDraft(
                                draft = occurrence,
                                creationKey = "${stateBeforeSave.voiceSession.sessionId}:${editor.draftId}:$occurrenceIndex",
                            )
                            eventsForDraft += event
                            savedEvents += event
                            val scheduleResults = reminderScheduler.scheduleWithResults(event)
                            val notificationStatus = scheduleResults.toNotificationStatus(notificationAllowed)
                            calendarRepository.updateNotificationStatus(event.id, notificationStatus)
                            scheduleResults.toSchedulingWarning(notificationAllowed)
                                ?.let(schedulingWarnings::add)
                        }
                        savedDraftIds += editor.draftId
                        outcomes += CreationItemOutcome(
                            draftId = editor.draftId,
                            title = draft.title,
                            itemType = draft.itemType,
                            status = if (schedulingWarnings.isEmpty()) {
                                CreationItemStatus.CREATED
                            } else {
                                CreationItemStatus.CREATED_WITH_WARNING
                            },
                            createdItemIds = eventsForDraft.map { it.id },
                            message = schedulingWarnings.distinct().joinToString("；").ifBlank { null },
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        outcomes += CreationItemOutcome(
                            draftId = editor.draftId,
                            title = draft.title,
                            itemType = draft.itemType,
                            status = CreationItemStatus.FAILED,
                            createdItemIds = eventsForDraft.map { it.id },
                            message = error.toUserMessage(),
                        )
                    }
                }
                val currentBatchSummary = CreationBatchSummary(outcomes)
                val previousSummary = stateBeforeSave.creationSummary.takeIf {
                    lastCreationSessionId == stateBeforeSave.voiceSession.sessionId
                }
                val summary = previousSummary?.mergedWith(currentBatchSummary)
                    ?: currentBatchSummary
                val failureMessages = summary.failedOutcomes
                    .mapNotNull { outcome -> outcome.message?.let { "${outcome.title}：$it" } }
                lastCreatedEvents = if (
                    lastCreationSessionId == stateBeforeSave.voiceSession.sessionId
                ) {
                    (lastCreatedEvents + savedEvents).distinctBy { it.id }
                } else {
                    savedEvents.distinctBy { it.id }
                }
                lastCreationSessionId = stateBeforeSave.voiceSession.sessionId
                _uiState.update { current ->
                    val remainingDrafts = current.draftEditors.filterNot { it.draftId in savedDraftIds }
                    val session = when {
                        summary.createdCount == 0 -> current.voiceSession.fail(
                            failureMessages.firstOrNull() ?: "没有项目创建成功。",
                        )
                        summary.hasFailures || summary.hasWarnings -> current.voiceSession.partiallySucceed(
                            createdIds = summary.createdItemIds,
                            errorMessages = (
                                failureMessages +
                                    summary.warningOutcomes.mapNotNull(CreationItemOutcome::message)
                                ).ifEmpty { listOf("部分提醒未能按预期安排。") },
                        )
                        else -> current.voiceSession.succeed(summary.createdItemIds)
                    }
                    current.copy(
                        voiceSession = session,
                        selectedDate = savedEvents.firstOrNull()
                            ?.displayAnchor()
                            ?.atZone(zoneId)
                            ?.toLocalDate()
                            ?: current.selectedDate,
                        draftEditors = remainingDrafts,
                        draftRiskMessages = current.draftRiskMessages
                            .filterKeys { it !in savedDraftIds },
                        acceptedRiskDraftIds = current.acceptedRiskDraftIds - savedDraftIds,
                        showDraftSheet = false,
                        creationSummary = summary,
                        showCreationResult = true,
                        errorMessage = failureMessages.firstOrNull(),
                        successMessage = null,
                    )
                }
                val historyStatus = when {
                    summary.createdCount == 0 -> ParseHistoryCreationStatus.FAILURE
                    summary.hasFailures || summary.hasWarnings -> ParseHistoryCreationStatus.PARTIAL_SUCCESS
                    else -> ParseHistoryCreationStatus.SUCCESS
                }
                settingsRepository.updateParseHistoryCreationResult(
                    id = stateBeforeSave.voiceSession.sessionId,
                    status = historyStatus,
                    createdItemIds = summary.createdItemIds,
                    failureReason = summary.outcomes.mapNotNull(CreationItemOutcome::message)
                        .distinct()
                        .joinToString("；")
                        .ifBlank { null },
                )
                if (savedEvents.isNotEmpty()) ShengliWidgetProvider.updateAll(appContext)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val message = error.toUserMessage()
                _uiState.update { current ->
                    current.copy(
                        voiceSession = current.voiceSession.fail(message),
                        showDraftSheet = current.draftEditors.isNotEmpty(),
                        errorMessage = message,
                    )
                }
            } finally {
                saveJob = null
            }
        }
    }

    fun dismissCreationResult() {
        _uiState.update { it.copy(showCreationResult = false) }
    }

    fun retryFailedCreation() {
        _uiState.update { state ->
            state.copy(
                showCreationResult = false,
                showDraftSheet = state.draftEditors.isNotEmpty(),
                voiceSession = if (state.draftEditors.any { !it.canCreate(zoneId) }) {
                    state.voiceSession.needsClarification(state.voiceSession.warnings)
                } else {
                    state.voiceSession.showPreview(state.voiceSession.warnings)
                },
                errorMessage = null,
            )
        }
    }

    fun undoLastCreation() {
        if (saveJob?.isActive == true || lastCreatedEvents.isEmpty()) return
        val eventsToUndo = lastCreatedEvents
        val historyId = lastCreationSessionId ?: return
        viewModelScope.launch {
            try {
                eventsToUndo.forEach { event ->
                    reminderScheduler.cancel(event.id, event.reminderMinutes)
                    calendarRepository.delete(event.id)
                }
                settingsRepository.updateParseHistoryUndone(historyId, isUndone = true)
                lastCreatedEvents = emptyList()
                lastCreationSessionId = null
                _uiState.update { state ->
                    state.copy(
                        voiceSession = when {
                            state.draftEditors.isEmpty() ->
                                state.voiceSession.returnToTranscript()
                            state.draftEditors.any { !it.canCreate(zoneId) } ->
                                state.voiceSession.needsClarification(state.voiceSession.warnings)
                            else -> state.voiceSession.showPreview(state.voiceSession.warnings)
                        },
                        creationSummary = state.creationSummary?.let { summary ->
                            summary.copy(
                                outcomes = summary.outcomes.map { outcome ->
                                    outcome.copy(createdItemIds = emptyList())
                                },
                            )
                        },
                        showCreationResult = false,
                        successMessage = "已撤销本次创建",
                        errorMessage = null,
                    )
                }
                ShengliWidgetProvider.updateAll(appContext)
            } catch (error: Throwable) {
                showError("撤销失败：${error.toUserMessage()}")
            }
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
            var schedulingWarning: String? = null
            if (updated.status == EventStatus.SCHEDULED) {
                val notificationAllowed = hasNotificationPermission()
                val scheduleResults = reminderScheduler.scheduleWithResults(updated)
                calendarRepository.updateNotificationStatus(
                    updated.id,
                    scheduleResults.toNotificationStatus(notificationAllowed),
                )
                schedulingWarning = scheduleResults.toSchedulingWarning(notificationAllowed)
            }
            _uiState.update {
                it.copy(
                    selectedDate = updated.startAt.atZone(zoneId).toLocalDate(),
                    eventEditor = null,
                    errorMessage = null,
                    successMessage = schedulingWarning ?: "事件已更新",
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

    fun openHistory() {
        if (hasActiveVoiceWork()) {
            _uiState.update { it.copy(errorMessage = "当前任务仍在处理中，完成后可查看识别历史。") }
            return
        }
        _uiState.update { it.copy(showHistory = true) }
    }

    fun closeHistory() {
        _uiState.update { it.copy(showHistory = false) }
    }

    fun deleteParseHistory(historyId: String) {
        viewModelScope.launch {
            settingsRepository.deleteParseHistory(historyId)
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
        _uiState.update {
            it.copy(
                showPrivacyDetails = false,
                showSettings = true,
            )
        }
    }

    fun requestResetConfirmation() {
        _uiState.update { it.copy(showResetConfirmation = true) }
    }

    fun dismissResetConfirmation() {
        _uiState.update { it.copy(showResetConfirmation = false) }
    }

    fun deleteAllLocalData() {
        returnToCreationResultAfterSettings = false
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
        if (hasActiveVoiceWork()) {
            _uiState.update { it.copy(errorMessage = "当前任务仍在处理中，完成后可打开设置。") }
            return
        }
        returnToCreationResultAfterSettings = false
        refreshPermissionStatus()
        _uiState.update {
            it.copy(
                showSettings = true,
                showInputCurtain = false,
                showDraftSheet = false,
                showHistory = false,
                showCreationResult = false,
                apiKeyInput = "",
            )
        }
    }

    fun openSettingsFromCreationResult() {
        returnToCreationResultAfterSettings = _uiState.value.creationSummary != null
        refreshPermissionStatus()
        _uiState.update {
            it.copy(
                showSettings = true,
                showInputCurtain = false,
                showDraftSheet = false,
                showHistory = false,
                showCreationResult = false,
                apiKeyInput = "",
            )
        }
    }

    fun closeSettings() {
        val restoreCreationResult = returnToCreationResultAfterSettings
        returnToCreationResultAfterSettings = false
        _uiState.update {
            it.copy(
                showSettings = false,
                showCreationResult = restoreCreationResult && it.creationSummary != null,
                apiKeyInput = "",
                errorMessage = null,
            )
        }
    }

    fun updateApiKeyInput(value: String) {
        _uiState.update {
            it.copy(
                apiKeyInput = value,
                aiConnectionState = AiConnectionState.UNVERIFIED,
                aiConnectionMessage = null,
            )
        }
    }

    fun updateDefaultReminderMinutesInput(value: String) {
        _uiState.update { it.copy(defaultReminderMinutesInput = value.filter(Char::isDigit)) }
    }

    fun updateDefaultDurationMinutesInput(value: String) {
        _uiState.update { it.copy(defaultDurationMinutesInput = value.filter(Char::isDigit)) }
    }

    fun saveApiKey() {
        saveUserSettings(closeSettings = true)
    }

    fun clearApiKey() {
        secureApiKeyStore.clear()
        _uiState.update {
            it.copy(
                apiKeyInput = "",
                apiKeySaved = false,
                aiConnectionState = AiConnectionState.UNVERIFIED,
                aiConnectionMessage = "本机密钥已清除。",
                errorMessage = null,
            )
        }
    }

    fun testAiConnection() {
        if (connectionTestJob?.isActive == true) return
        val state = _uiState.value
        val apiKey = state.apiKeyInput.trim().takeIf(String::isNotEmpty)
            ?: secureApiKeyStore.read()
        if (apiKey.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    aiConnectionState = AiConnectionState.FAILED,
                    aiConnectionMessage = "请先输入或保存 DeepSeek API key。",
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                aiConnectionState = AiConnectionState.CHECKING,
                aiConnectionMessage = "正在验证 AI 服务连接…",
            )
        }
        connectionTestJob = viewModelScope.launch {
            try {
                val result = parser.parse(
                    transcript = "明天上午九点提醒我测试连接",
                    apiKey = apiKey,
                    defaultDurationMinutes = settingsRepository.getDefaultDurationMinutes(),
                    defaultReminderMinutes = settingsRepository.getDefaultReminderMinutes(),
                    zoneId = zoneId,
                )
                val connected = result.events.isNotEmpty()
                _uiState.update {
                    it.copy(
                        aiConnectionState = if (connected) {
                            AiConnectionState.CONNECTED
                        } else {
                            AiConnectionState.FAILED
                        },
                        aiConnectionMessage = if (connected) {
                            "DeepSeek 连接成功。"
                        } else {
                            result.warnings.firstOrNull() ?: "服务已响应，但返回内容无法使用。"
                        },
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        aiConnectionState = AiConnectionState.FAILED,
                        aiConnectionMessage = error.toUserMessage(),
                    )
                }
            } finally {
                connectionTestJob = null
            }
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
        val restoreCreationResult =
            closeSettings && returnToCreationResultAfterSettings && state.creationSummary != null
        if (closeSettings) {
            returnToCreationResultAfterSettings = false
        }
        _uiState.update {
            it.copy(
                apiKeyInput = "",
                apiKeySaved = secureApiKeyStore.hasKey(),
                showSettings = if (closeSettings) false else it.showSettings,
                showCreationResult = if (closeSettings) {
                    restoreCreationResult
                } else {
                    it.showCreationResult
                },
                errorMessage = null,
                successMessage = "设置已保存",
            )
        }
        return true
    }

    private fun hasActiveVoiceWork(): Boolean =
        parseJob?.isActive == true ||
            saveJob?.isActive == true ||
            _uiState.value.voiceTaskState in setOf(
                VoiceTaskState.Listening,
                VoiceTaskState.Transcribing,
                VoiceTaskState.Parsing,
                VoiceTaskState.Saving,
            )

    private fun startSettingsIntent(intent: Intent) {
        runCatching {
            appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            showError("无法打开系统设置，请从系统设置里找到声历。")
        }
    }

    private fun Throwable.toUserMessage(): String =
        VoiceWorkflowErrorMapper.fromThrowable(this).userMessage

    private fun com.voicememos.aicalendar.domain.AiParseResult.toHistoryItem(
        transcript: String,
        historyId: String,
    ): ParseHistoryItem {
        val titleSummary = events
            .map { it.title.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(3)
            .joinToString("、")
        val extraCount = (events.size - 3).takeIf { it > 0 }?.let { " 等 $it 项" }.orEmpty()
        return ParseHistoryItem(
            id = historyId,
            transcript = transcript,
            summary = when {
                titleSummary.isNotBlank() -> titleSummary + extraCount
                warnings.isNotEmpty() -> warnings.first()
                else -> "未生成可保存草稿"
            },
            draftCount = events.size,
            createdAtEpochMillis = System.currentTimeMillis(),
            itemSummaries = events.map { draft ->
                ParseHistoryScheduleItemSummary(
                    itemType = draft.itemType,
                    title = draft.title,
                    timeSummary = draft.displayAnchor()
                        ?.atZone(zoneId)
                        ?.toLocalDateTime()
                        ?.toString(),
                    location = draft.location,
                    missingFields = draft.missingFields,
                    ambiguousFields = draft.ambiguousFields,
                )
            },
        )
    }

    private fun assessDraftRisks(
        editors: List<DraftEditor>,
        existingEvents: List<CalendarEvent>,
    ): Map<String, List<String>> {
        val now = java.time.Instant.now()
        return editors.mapNotNull { editor ->
            val assessment = ScheduleRiskDetector.assess(
                draft = editor.toDraft(zoneId),
                existingEvents = existingEvents,
                now = now,
                displayZone = zoneId,
            )
            val messages = (
                assessment.eventConflicts.map { it.summary } +
                    assessment.pastTimes.map { it.summary }
                ).distinct()
            messages.takeIf(List<String>::isNotEmpty)?.let { editor.draftId to it }
        }.toMap()
    }

    private fun Map<Int, ScheduleResult>.toSchedulingWarning(
        notificationAllowed: Boolean,
    ): String? =
        when {
            values.all { it == ScheduleResult.NOT_REQUIRED } -> null
            !notificationAllowed -> "事项已保存，但通知权限未开启，提醒未能送达。"
            values.any { it == ScheduleResult.FAILED } -> "事项已保存，但提醒安排失败。"
            values.any { it == ScheduleResult.SKIPPED_PAST } -> "事项已保存，但过去时间的提醒未安排。"
            values.any { it == ScheduleResult.INEXACT } -> "事项已保存；未开启准点提醒，系统将使用近似时间。"
            else -> null
        }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun CalendarEvent.displayAnchor(): java.time.Instant =
        when (itemType) {
            ScheduleItemType.EVENT -> startAt
            ScheduleItemType.REMINDER -> triggerAt ?: startAt
            ScheduleItemType.TODO -> dueAt ?: startAt
        }

    private fun DraftCalendarEvent.displayAnchor(): java.time.Instant? =
        when (itemType) {
            ScheduleItemType.EVENT -> startAt
            ScheduleItemType.REMINDER -> triggerAt
            ScheduleItemType.TODO -> dueAt
        }

}
