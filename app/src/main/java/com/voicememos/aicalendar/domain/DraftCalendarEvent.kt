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
        get() =
            isSemanticallyComplete &&
                when (itemType) {
                    ScheduleItemType.EVENT,
                    ScheduleItemType.REMINDER,
                    -> startAt != null

                    // A TODO without a due time is valid. The persistence mapper
                    // supplies a compatibility-only startAt anchor for the legacy
                    // non-null column without changing the TODO's dueAt semantics.
                    ScheduleItemType.TODO -> true
                }
}
