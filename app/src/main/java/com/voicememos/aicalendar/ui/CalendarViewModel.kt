package com.voicememos.aicalendar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicememos.aicalendar.ai.DeepSeekCalendarParser
import com.voicememos.aicalendar.data.CalendarRepository
import com.voicememos.aicalendar.data.SettingsRepository
import com.voicememos.aicalendar.domain.EventStatus
import com.voicememos.aicalendar.reminder.ReminderScheduler
import com.voicememos.aicalendar.security.SecureApiKeyStore
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CalendarViewModel(
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
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun selectToday() {
        _uiState.update { it.copy(selectedDate = LocalDate.now()) }
    }

    fun setViewMode(mode: CalendarViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    fun updateTextInput(value: String) {
        _uiState.update { it.copy(textInput = value, errorMessage = null) }
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
        parseText(_uiState.value.textInput)
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
                _uiState.update {
                    it.copy(
                        isParsing = false,
                        draftEditors = result.events.map { draft -> draft.toEditor(zoneId) },
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
                        errorMessage = error.message ?: "解析失败，请稍后再试。",
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
            state.copy(draftEditors = state.draftEditors.filterNot { it.draftId == draftId })
        }
    }

    fun clearDrafts() {
        _uiState.update { it.copy(draftEditors = emptyList(), warnings = emptyList()) }
    }

    fun confirmDraft(draftId: String) {
        val editor = _uiState.value.draftEditors.firstOrNull { it.draftId == draftId } ?: return
        val draft = editor.toDraft(zoneId)
        if (!draft.canCreate) {
            showError("这个草稿还缺少时间或标题。")
            return
        }

        viewModelScope.launch {
            val event = calendarRepository.createFromDraft(draft)
            reminderScheduler.schedule(event)
            _uiState.update { state ->
                state.copy(
                    selectedDate = event.startAt.atZone(zoneId).toLocalDate(),
                    draftEditors = state.draftEditors.filterNot { it.draftId == draftId },
                    textInput = "",
                    errorMessage = null,
                )
            }
        }
    }

    fun markDone(eventId: String) {
        viewModelScope.launch {
            calendarRepository.updateStatus(eventId, EventStatus.DONE)
        }
    }

    fun deleteEvent(eventId: String) {
        viewModelScope.launch {
            calendarRepository.delete(eventId)
        }
    }

    fun openSettings() {
        _uiState.update { it.copy(showSettings = true, apiKeyInput = "") }
    }

    fun closeSettings() {
        _uiState.update { it.copy(showSettings = false, apiKeyInput = "", errorMessage = null) }
    }

    fun updateApiKeyInput(value: String) {
        _uiState.update { it.copy(apiKeyInput = value) }
    }

    fun saveApiKey() {
        secureApiKeyStore.save(_uiState.value.apiKeyInput)
        _uiState.update {
            it.copy(
                apiKeyInput = "",
                apiKeySaved = secureApiKeyStore.hasKey(),
                showSettings = false,
                errorMessage = null,
            )
        }
    }
}

