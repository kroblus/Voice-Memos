package com.voicememos.aicalendar.ui

import com.voicememos.aicalendar.domain.CalendarEvent
import com.voicememos.aicalendar.domain.DraftCalendarEvent
import com.voicememos.aicalendar.domain.ParseHistoryItem
import com.voicememos.aicalendar.domain.RepeatFrequency
import com.voicememos.aicalendar.domain.RepeatRule
import com.voicememos.aicalendar.domain.ScheduleItemPriority
import com.voicememos.aicalendar.domain.ScheduleItemType
import com.voicememos.aicalendar.ui.session.VoiceScheduleSession
import com.voicememos.aicalendar.ui.session.VoiceTaskState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

enum class CalendarViewMode(val label: String) {
    MONTH("月"),
    WEEK("周"),
    DAY("日"),
    AGENDA("清单"),
}

enum class AgendaFilter(val label: String) {
    ALL("全部"),
    SCHEDULED("未完成"),
    DONE("已完成"),
}

enum class PermissionHealth(val label: String) {
    READY("已开启"),
    NEEDS_ACTION("待开启"),
    UNAVAILABLE("不可用"),
}

data class CalendarUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val viewMode: CalendarViewMode = CalendarViewMode.MONTH,
    val themeMode: AppThemeMode = AppThemeMode.LIGHT,
    val onboardingSeen: Boolean = false,
    val events: List<CalendarEvent> = emptyList(),
    val voiceSession: VoiceScheduleSession = VoiceScheduleSession.fresh(),
    val showInputCurtain: Boolean = false,
    val draftEditors: List<DraftEditor> = emptyList(),
    val showDraftSheet: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val showSettings: Boolean = false,
    val apiKeyInput: String = "",
    val apiKeySaved: Boolean = false,
    val defaultReminderMinutesInput: String = "10",
    val defaultDurationMinutesInput: String = "30",
    val agendaQuery: String = "",
    val agendaFilter: AgendaFilter = AgendaFilter.ALL,
    val eventEditor: EventEditor? = null,
    val parseHistory: List<ParseHistoryItem> = emptyList(),
    val microphonePermission: PermissionHealth = PermissionHealth.NEEDS_ACTION,
    val notificationPermission: PermissionHealth = PermissionHealth.NEEDS_ACTION,
    val exactAlarmPermission: PermissionHealth = PermissionHealth.NEEDS_ACTION,
    val showPrivacyDetails: Boolean = false,
    val showResetConfirmation: Boolean = false,
    val backupMessage: String? = null,
) {
    val voiceTaskState: VoiceTaskState
        get() = voiceSession.state

    val textInput: String
        get() = voiceSession.editedTranscript

    val partialTranscript: String
        get() = voiceSession.partialTranscript

    val isListening: Boolean
        get() = voiceSession.isListening

    val isParsing: Boolean
        get() = voiceSession.isParsing

    val isSaving: Boolean
        get() = voiceSession.isSaving

    val warnings: List<String>
        get() = voiceSession.warnings
}

data class DraftEditor(
    val draftId: String,
    val title: String,
    val notes: String,
    val startLocal: String,
    val endLocal: String,
    val allDay: Boolean,
    val reminderMinutes: String,
    val sourceTranscript: String,
    val aiConfidence: Double,
    val missingFields: List<String>,
    val repeatFrequency: RepeatFrequency? = null,
    val repeatCount: String = "",
    val expanded: Boolean = false,
    val itemType: ScheduleItemType = ScheduleItemType.EVENT,
    val triggerLocal: String = "",
    val dueLocal: String = "",
    val location: String = "",
    val priority: ScheduleItemPriority = ScheduleItemPriority.NORMAL,
    val ambiguousFields: List<String> = emptyList(),
    val timeZoneId: String = ZoneId.systemDefault().id,
    val recurrenceRule: String = "",
    val completed: Boolean = false,
) {
    fun toDraft(zoneId: ZoneId): DraftCalendarEvent {
        val editorZone = runCatching { ZoneId.of(timeZoneId.trim()) }.getOrNull() ?: zoneId
        val start = parseLocalInstant(startLocal, allDay, editorZone)
        val end = parseLocalInstant(endLocal, allDay, editorZone)
        val trigger = parseLocalInstant(triggerLocal, allDay = false, zoneId = editorZone)
        val due = parseLocalInstant(dueLocal, allDay, editorZone)
        val missing = missingFields.toMutableSet()
        if (title.isBlank()) missing += "title" else missing.removeFields("title")
        if (runCatching { ZoneId.of(timeZoneId.trim()) }.isFailure) {
            missing += "time_zone"
        } else {
            missing.removeFields("time_zone", "timeZone", "timeZoneId")
        }
        when (itemType) {
            ScheduleItemType.EVENT -> {
                if (start == null) {
                    missing += "start_local"
                } else {
                    missing.removeFields("start", "start_local", "startAt", "startDateTime")
                }
                if (start != null && end != null && !end.isAfter(start)) {
                    missing += "end_local"
                } else {
                    missing.removeFields("end", "end_local", "endAt", "endDateTime")
                }
                missing.removeFields("trigger", "trigger_local", "triggerAt", "triggerDateTime")
                missing.removeFields("due", "due_local", "dueAt", "dueDateTime")
            }
            ScheduleItemType.REMINDER -> {
                if (trigger == null) {
                    missing += "trigger"
                } else {
                    missing.removeFields("trigger", "trigger_local", "triggerAt", "triggerDateTime")
                }
                missing.removeFields("start", "start_local", "startAt", "startDateTime")
                missing.removeFields("end", "end_local", "endAt", "endDateTime")
                missing.removeFields("due", "due_local", "dueAt", "dueDateTime")
            }
            ScheduleItemType.TODO -> {
                // The current persistence layer still requires a due instant as
                // the compatibility startAt value.
                if (due == null) {
                    missing += "due"
                } else {
                    missing.removeFields("due", "due_local", "dueAt", "dueDateTime")
                }
                missing.removeFields("start", "start_local", "startAt", "startDateTime")
                missing.removeFields("end", "end_local", "endAt", "endDateTime")
                missing.removeFields("trigger", "trigger_local", "triggerAt", "triggerDateTime")
            }
        }
        val compatibilityStart = when (itemType) {
            ScheduleItemType.EVENT -> start
            ScheduleItemType.REMINDER -> trigger
            ScheduleItemType.TODO -> due
        }
        return DraftCalendarEvent(
            draftId = draftId,
            title = title,
            notes = notes.takeIf { it.isNotBlank() },
            startAt = compatibilityStart,
            endAt = end.takeIf { itemType == ScheduleItemType.EVENT },
            allDay = allDay,
            reminderMinutes = reminderMinutes.split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it >= 0 }
                .ifEmpty { listOf(10) }
                .plus(0)
                .distinct()
                .sorted(),
            sourceTranscript = sourceTranscript,
            aiConfidence = aiConfidence,
            missingFields = missing.sorted(),
            repeatRule = repeatFrequency?.let { frequency ->
                RepeatRule(
                    frequency = frequency,
                    count = repeatCount.toIntOrNull() ?: when (frequency) {
                        RepeatFrequency.DAILY -> 7
                        RepeatFrequency.WEEKLY -> 8
                    },
                )
            },
            itemType = itemType,
            triggerAt = trigger.takeIf { itemType == ScheduleItemType.REMINDER },
            dueAt = due.takeIf { itemType == ScheduleItemType.TODO },
            location = location.trim().takeIf { it.isNotEmpty() },
            recurrenceRule = recurrenceRule.trim().takeIf { it.isNotEmpty() },
            priority = priority.takeIf { itemType == ScheduleItemType.TODO },
            completed = completed,
            ambiguousFields = ambiguousFields.distinct().sorted(),
            timeZoneId = editorZone.id,
        )
    }

    fun withItemType(type: ScheduleItemType): DraftEditor {
        val availableTime = startLocal.ifBlank { triggerLocal.ifBlank { dueLocal } }
        return copy(
            itemType = type,
            startLocal = if (type == ScheduleItemType.EVENT) availableTime else startLocal,
            triggerLocal = if (type == ScheduleItemType.REMINDER) availableTime else triggerLocal,
            dueLocal = if (type == ScheduleItemType.TODO) availableTime else dueLocal,
            missingFields = missingFields.resolveFields("type", "item_type"),
            ambiguousFields = ambiguousFields.resolveFields("type", "item_type"),
        )
    }

    fun resolveFields(vararg fields: String): DraftEditor =
        copy(
            missingFields = missingFields.resolveFields(*fields),
            ambiguousFields = ambiguousFields.resolveFields(*fields),
        )

    fun blockingIssueLabels(zoneId: ZoneId): List<String> {
        val draft = toDraft(zoneId)
        return (draft.missingFields + draft.ambiguousFields)
            .distinct()
            .map(::fieldDisplayName)
            .distinct()
    }

    fun canCreate(zoneId: ZoneId): Boolean = toDraft(zoneId).canCreate
}

fun DraftCalendarEvent.toEditor(zoneId: ZoneId): DraftEditor {
    val editorZone = timeZoneId
        ?.let { value -> runCatching { ZoneId.of(value) }.getOrNull() }
        ?: zoneId
    return DraftEditor(
        draftId = draftId,
        title = title,
        notes = notes.orEmpty(),
        startLocal = startAt
            ?.takeIf { itemType == ScheduleItemType.EVENT }
            ?.formatForEditor(allDay, editorZone)
            .orEmpty(),
        endLocal = endAt?.formatForEditor(allDay, editorZone).orEmpty(),
        allDay = allDay,
        reminderMinutes = reminderMinutes.joinToString(","),
        sourceTranscript = sourceTranscript,
        aiConfidence = aiConfidence,
        missingFields = missingFields,
        repeatFrequency = repeatRule?.frequency,
        repeatCount = repeatRule?.normalizedCount?.toString().orEmpty(),
        itemType = itemType,
        triggerLocal = (triggerAt ?: startAt.takeIf { itemType == ScheduleItemType.REMINDER })
            ?.formatForEditor(allDay = false, zoneId = editorZone)
            .orEmpty(),
        dueLocal = (dueAt ?: startAt.takeIf { itemType == ScheduleItemType.TODO })
            ?.formatForEditor(allDay, editorZone)
            .orEmpty(),
        location = location.orEmpty(),
        priority = priority ?: ScheduleItemPriority.NORMAL,
        ambiguousFields = ambiguousFields,
        timeZoneId = editorZone.id,
        recurrenceRule = recurrenceRule.orEmpty(),
        completed = completed,
    )
}

data class EventEditor(
    val eventId: String,
    val title: String,
    val notes: String,
    val startLocal: String,
    val endLocal: String,
    val allDay: Boolean,
    val reminderMinutes: String,
) {
    fun parsedStart(zoneId: ZoneId): Instant? = parseLocalInstant(startLocal, allDay, zoneId)

    fun parsedEnd(zoneId: ZoneId): Instant? = parseLocalInstant(endLocal, allDay, zoneId)

    fun parsedReminders(): List<Int> =
        reminderMinutes.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it >= 0 }
            .ifEmpty { listOf(10) }
            .plus(0)
            .distinct()
            .sorted()

    fun canSave(zoneId: ZoneId): Boolean =
        title.isNotBlank() && parsedStart(zoneId) != null
}

fun CalendarEvent.toEditor(zoneId: ZoneId): EventEditor =
    EventEditor(
        eventId = id,
        title = title,
        notes = notes.orEmpty(),
        startLocal = startAt.formatForEditor(allDay, zoneId),
        endLocal = endAt?.formatForEditor(allDay, zoneId).orEmpty(),
        allDay = allDay,
        reminderMinutes = reminderMinutes.joinToString(","),
    )

private fun Instant.formatForEditor(allDay: Boolean, zoneId: ZoneId): String {
    val zoned = atZone(zoneId)
    return if (allDay) {
        zoned.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
    } else {
        zoned.toLocalDateTime().format(EditorDateTimeFormatter)
    }
}

private fun parseLocalInstant(value: String, allDay: Boolean, zoneId: ZoneId): Instant? {
    val cleaned = value.trim()
    if (cleaned.isEmpty()) return null
    return if (allDay) {
        runCatching { LocalDate.parse(cleaned).atStartOfDay(zoneId).toInstant() }.getOrNull()
    } else {
        parseDateTime(cleaned, zoneId)
    }
}

private fun parseDateTime(value: String, zoneId: ZoneId): Instant? {
    val normalized = value.replace("T", " ")
    return try {
        LocalDateTime.parse(normalized, EditorDateTimeFormatter).atZone(zoneId).toInstant()
    } catch (_: DateTimeParseException) {
        runCatching { LocalDateTime.parse(value).atZone(zoneId).toInstant() }.getOrNull()
    }
}

private fun List<String>.resolveFields(vararg fields: String): List<String> {
    val normalized = fields.map(::fieldKey).toSet()
    return filterNot { fieldKey(it) in normalized }
}

private fun MutableSet<String>.removeFields(vararg fields: String) {
    val normalized = fields.map(::fieldKey).toSet()
    removeAll { fieldKey(it) in normalized }
}

private fun fieldKey(field: String): String =
    field.trim().lowercase().filter(Char::isLetterOrDigit)

private fun fieldDisplayName(field: String): String =
    when (fieldKey(field)) {
        "title" -> "标题"
        "type", "itemtype" -> "类型"
        "start", "startlocal", "startat", "startdatetime" -> "开始时间"
        "end", "endlocal", "endat", "enddatetime" -> "结束时间"
        "trigger", "triggerlocal", "triggerat", "triggerdatetime" -> "提醒时间"
        "due", "duelocal", "dueat", "duedatetime" -> "截止时间"
        "location" -> "地点"
        "notes" -> "备注"
        "recurrencerule" -> "重复规则"
        "priority" -> "优先级"
        "timezone", "timezoneid" -> "时区"
        "allday" -> "全天设置"
        "clarification" -> "整体信息"
        else -> field
    }

private val EditorDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
