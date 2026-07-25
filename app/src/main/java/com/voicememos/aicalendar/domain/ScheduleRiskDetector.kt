package com.voicememos.aicalendar.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ScheduleRiskAssessment(
    val eventConflicts: List<EventConflictSummary> = emptyList(),
    val pastTimes: List<PastScheduleTimeSummary> = emptyList(),
) {
    val hasEventConflicts: Boolean
        get() = eventConflicts.isNotEmpty()

    val hasPastStartTime: Boolean
        get() = pastTimes.any { it.field == ScheduleTimeField.START }

    val hasPastTriggerTime: Boolean
        get() = pastTimes.any { it.field == ScheduleTimeField.TRIGGER }

    val hasPastDueTime: Boolean
        get() = pastTimes.any { it.field == ScheduleTimeField.DUE }
}

data class EventConflictSummary(
    val conflictingEventId: String,
    val conflictingEventTitle: String,
    val conflictingStartAt: Instant,
    val conflictingEndAt: Instant,
    val overlapStartAt: Instant,
    val overlapEndAt: Instant,
    val summary: String,
)

enum class ScheduleTimeField {
    START,
    TRIGGER,
    DUE,
}

data class PastScheduleTimeSummary(
    val field: ScheduleTimeField,
    val scheduledAt: Instant,
    val summary: String,
)

object ScheduleRiskDetector {
    fun assess(
        draft: DraftCalendarEvent,
        existingEvents: List<CalendarEvent>,
        now: Instant,
        displayZone: ZoneId,
    ): ScheduleRiskAssessment =
        ScheduleRiskAssessment(
            eventConflicts = detectEventConflicts(
                draft = draft,
                existingEvents = existingEvents,
                displayZone = displayZone,
            ),
            pastTimes = detectPastTimes(
                draft = draft,
                now = now,
                displayZone = displayZone,
            ),
        )

    fun detectEventConflicts(
        draft: DraftCalendarEvent,
        existingEvents: List<CalendarEvent>,
        displayZone: ZoneId,
    ): List<EventConflictSummary> {
        if (draft.itemType != ScheduleItemType.EVENT) return emptyList()
        val candidateInterval = draft.occupancyInterval() ?: return emptyList()

        return existingEvents
            .asSequence()
            .filter { event ->
                event.itemType == ScheduleItemType.EVENT &&
                    event.status != EventStatus.ARCHIVED
            }
            .mapNotNull { event ->
                val existingInterval = event.occupancyInterval() ?: return@mapNotNull null
                if (!candidateInterval.overlaps(existingInterval)) return@mapNotNull null

                EventConflictSummary(
                    conflictingEventId = event.id,
                    conflictingEventTitle = event.title,
                    conflictingStartAt = existingInterval.start,
                    conflictingEndAt = existingInterval.end,
                    overlapStartAt = maxOf(candidateInterval.start, existingInterval.start),
                    overlapEndAt = minOf(candidateInterval.end, existingInterval.end),
                    summary = event.conflictSummary(existingInterval, displayZone),
                )
            }
            .distinctBy(EventConflictSummary::conflictingEventId)
            .sortedBy(EventConflictSummary::conflictingStartAt)
            .toList()
    }

    fun detectPastTimes(
        draft: DraftCalendarEvent,
        now: Instant,
        displayZone: ZoneId,
    ): List<PastScheduleTimeSummary> {
        val fieldAndTime = when (draft.itemType) {
            ScheduleItemType.EVENT -> ScheduleTimeField.START to draft.startAt
            ScheduleItemType.REMINDER -> ScheduleTimeField.TRIGGER to draft.triggerAt
            ScheduleItemType.TODO -> ScheduleTimeField.DUE to draft.dueAt
        }
        val scheduledAt = fieldAndTime.second ?: return emptyList()
        if (!scheduledAt.isBefore(now)) return emptyList()

        return listOf(
            PastScheduleTimeSummary(
                field = fieldAndTime.first,
                scheduledAt = scheduledAt,
                summary = draft.pastTimeSummary(
                    field = fieldAndTime.first,
                    scheduledAt = scheduledAt,
                    displayZone = displayZone,
                ),
            ),
        )
    }

    private fun CalendarEvent.conflictSummary(
        interval: OccupancyInterval,
        displayZone: ZoneId,
    ): String {
        val displayTitle = title.trim().ifBlank { "未命名事件" }
        return "与“$displayTitle”（${interval.displayText(displayZone)}）时间重叠"
    }

    private fun DraftCalendarEvent.pastTimeSummary(
        field: ScheduleTimeField,
        scheduledAt: Instant,
        displayZone: ZoneId,
    ): String {
        val displayTitle = title.trim().ifBlank { "未命名事项" }
        val fieldLabel = when (field) {
            ScheduleTimeField.START -> "开始时间"
            ScheduleTimeField.TRIGGER -> "提醒时间"
            ScheduleTimeField.DUE -> "截止时间"
        }
        return "“$displayTitle”的${fieldLabel}已过去（${scheduledAt.displayText(displayZone)}）"
    }
}

private data class OccupancyInterval(
    val start: Instant,
    val end: Instant,
) {
    fun overlaps(other: OccupancyInterval): Boolean =
        start < other.end && other.start < end

    fun displayText(zoneId: ZoneId): String =
        "${start.displayText(zoneId)} 至 ${end.displayText(zoneId)}"
}

private fun DraftCalendarEvent.occupancyInterval(): OccupancyInterval? {
    val start = startAt ?: return null
    val end = endAt ?: return null
    return OccupancyInterval(start, end).takeIf { start < end }
}

private fun CalendarEvent.occupancyInterval(): OccupancyInterval? {
    val end = endAt ?: return null
    return OccupancyInterval(startAt, end).takeIf { startAt < end }
}

private fun Instant.displayText(zoneId: ZoneId): String =
    DISPLAY_TIME_FORMATTER.withZone(zoneId).format(this)

private val DISPLAY_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
