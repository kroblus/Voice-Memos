package com.voicememos.aicalendar.ui.session

import java.time.Instant
import java.util.UUID

data class VoiceScheduleSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val state: VoiceTaskState = VoiceTaskState.Idle,
    val rawTranscript: String = "",
    val editedTranscript: String = "",
    val partialTranscript: String = "",
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val createdItemIds: List<String> = emptyList(),
    val startedAt: Instant = Instant.now(),
    val updatedAt: Instant = startedAt,
) {
    val isListening: Boolean
        get() = state == VoiceTaskState.Listening

    val isParsing: Boolean
        get() = state == VoiceTaskState.Parsing

    val isSaving: Boolean
        get() = state == VoiceTaskState.Saving

    val canBeginParsing: Boolean
        get() = editedTranscript.isNotBlank() &&
            state in setOf(
                VoiceTaskState.TranscriptReady,
                VoiceTaskState.NeedsClarification,
                VoiceTaskState.Failure,
            )

    fun startListening(now: Instant = Instant.now()): VoiceScheduleSession =
        copy(
            state = VoiceTaskState.Listening,
            partialTranscript = "",
            warnings = emptyList(),
            errors = emptyList(),
            updatedAt = now,
        )

    fun updatePartialTranscript(
        value: String,
        now: Instant = Instant.now(),
    ): VoiceScheduleSession =
        copy(
            partialTranscript = value,
            updatedAt = now,
        )

    fun beginTranscribing(now: Instant = Instant.now()): VoiceScheduleSession =
        if (state == VoiceTaskState.Listening) {
            copy(
                state = VoiceTaskState.Transcribing,
                updatedAt = now,
            )
        } else {
            this
        }

    fun completeTranscript(
        value: String,
        now: Instant = Instant.now(),
    ): VoiceScheduleSession {
        val transcript = value.trim()
        return copy(
            state = if (transcript.isBlank()) VoiceTaskState.Failure else VoiceTaskState.TranscriptReady,
            rawTranscript = transcript.ifBlank { rawTranscript },
            editedTranscript = transcript.ifBlank { editedTranscript },
            partialTranscript = "",
            errors = if (transcript.isBlank()) listOf("没有识别到语音。") else emptyList(),
            updatedAt = now,
        )
    }

    fun updateEditedTranscript(
        value: String,
        now: Instant = Instant.now(),
    ): VoiceScheduleSession =
        copy(
            state = if (value.isBlank()) VoiceTaskState.Idle else VoiceTaskState.TranscriptReady,
            editedTranscript = value,
            partialTranscript = "",
            warnings = emptyList(),
            errors = emptyList(),
            updatedAt = now,
        )

    fun prepareTranscript(
        value: String,
        now: Instant = Instant.now(),
    ): VoiceScheduleSession {
        val transcript = value.trim()
        return copy(
            state = if (transcript.isBlank()) VoiceTaskState.Idle else VoiceTaskState.TranscriptReady,
            rawTranscript = rawTranscript.ifBlank { transcript },
            editedTranscript = transcript,
            partialTranscript = "",
            warnings = emptyList(),
            errors = emptyList(),
            updatedAt = now,
        )
    }

    fun beginParsing(now: Instant = Instant.now()): VoiceScheduleSession {
        check(canBeginParsing) { "Voice session is not ready to parse." }
        return copy(
            state = VoiceTaskState.Parsing,
            warnings = emptyList(),
            errors = emptyList(),
            updatedAt = now,
        )
    }

    fun needsClarification(
        warnings: List<String>,
        now: Instant = Instant.now(),
    ): VoiceScheduleSession =
        copy(
            state = VoiceTaskState.NeedsClarification,
            warnings = warnings,
            errors = emptyList(),
            updatedAt = now,
        )

    fun showPreview(
        warnings: List<String>,
        now: Instant = Instant.now(),
    ): VoiceScheduleSession =
        copy(
            state = VoiceTaskState.Preview,
            warnings = warnings,
            errors = emptyList(),
            updatedAt = now,
        )

    fun beginSaving(now: Instant = Instant.now()): VoiceScheduleSession =
        copy(
            state = VoiceTaskState.Saving,
            errors = emptyList(),
            updatedAt = now,
        )

    fun succeed(
        createdIds: List<String>,
        now: Instant = Instant.now(),
    ): VoiceScheduleSession =
        copy(
            state = VoiceTaskState.Success,
            createdItemIds = (createdItemIds + createdIds).distinct(),
            warnings = emptyList(),
            errors = emptyList(),
            updatedAt = now,
        )

    fun partiallySucceed(
        createdIds: List<String>,
        errorMessages: List<String>,
        now: Instant = Instant.now(),
    ): VoiceScheduleSession =
        copy(
            state = VoiceTaskState.PartialSuccess,
            createdItemIds = (createdItemIds + createdIds).distinct(),
            errors = errorMessages,
            updatedAt = now,
        )

    fun fail(
        message: String,
        now: Instant = Instant.now(),
    ): VoiceScheduleSession =
        copy(
            state = VoiceTaskState.Failure,
            errors = listOf(message),
            updatedAt = now,
        )

    fun returnToTranscript(now: Instant = Instant.now()): VoiceScheduleSession =
        copy(
            state = if (editedTranscript.isBlank()) VoiceTaskState.Idle else VoiceTaskState.TranscriptReady,
            warnings = emptyList(),
            errors = emptyList(),
            updatedAt = now,
        )

    companion object {
        fun fresh(now: Instant = Instant.now()): VoiceScheduleSession =
            VoiceScheduleSession(startedAt = now, updatedAt = now)
    }
}
