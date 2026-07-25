package com.voicememos.aicalendar.domain

import java.time.Instant
import java.util.UUID

enum class RepeatFrequency(val label: String) {
    DAILY("每天"),
    WEEKLY("每周"),
}

data class RepeatRule(
    val frequency: RepeatFrequency,
    val count: Int,
) {
    val normalizedCount: Int
        get() = count.coerceIn(2, 60)

    fun summary(): String = "${frequency.label}，共 $normalizedCount 次"
}

data class DraftCalendarEvent(
    val draftId: String = UUID.randomUUID().toString(),
    val title: String,
    val notes: String?,
    val startAt: Instant?,
    val endAt: Instant?,
    val allDay: Boolean,
    val reminderMinutes: List<Int>,
    val sourceTranscript: String,
    val aiConfidence: Double,
    val missingFields: List<String>,
    val repeatRule: RepeatRule? = null,
    val itemType: ScheduleItemType = ScheduleItemType.EVENT,
    val triggerAt: Instant? = null,
    val dueAt: Instant? = null,
    val location: String? = null,
    val recurrenceRule: String? = null,
    val priority: ScheduleItemPriority? = null,
    val completed: Boolean = false,
    val ambiguousFields: List<String> = emptyList(),
    val timeZoneId: String? = null,
) {
    val reminderOffsets: List<Int>
        get() = reminderMinutes

    val needsClarification: Boolean
        get() = missingFields.isNotEmpty() || ambiguousFields.isNotEmpty()

    val isSemanticallyComplete: Boolean
        get() =
            title.isNotBlank() &&
                !needsClarification &&
                when (itemType) {
                    ScheduleItemType.EVENT -> startAt != null
                    ScheduleItemType.REMINDER -> triggerAt != null
                    ScheduleItemType.TODO -> true
                }

    val canCreate: Boolean
        // The current Room layer still requires startAt. Keep this compatibility
        // guard until typed EVENT/REMINDER/TODO persistence is introduced.
        get() = isSemanticallyComplete && startAt != null
}
