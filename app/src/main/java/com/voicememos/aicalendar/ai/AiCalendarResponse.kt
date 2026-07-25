package com.voicememos.aicalendar.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AiCalendarResponse(
    val items: List<AiCalendarEvent> = emptyList(),
    val events: List<AiCalendarEvent> = emptyList(),
    val warnings: List<String> = emptyList(),
    @SerialName("requiresClarification") val requiresClarification: Boolean = false,
    @SerialName("clarificationQuestions") val clarificationQuestions: List<String> = emptyList(),
) {
    val effectiveItems: List<AiCalendarEvent>
        get() = items.ifEmpty { events }

    val usesLegacyEvents: Boolean
        get() = items.isEmpty() && events.isNotEmpty()
}

@Serializable
data class AiCalendarEvent(
    val type: String? = null,
    val title: String = "",
    val notes: String? = null,
    val location: String? = null,
    @SerialName("time_zone") val timeZone: String? = null,
    val start: String? = null,
    val end: String? = null,
    val trigger: String? = null,
    val due: String? = null,
    @SerialName("startDateTime") val startDateTime: String? = null,
    @SerialName("endDateTime") val endDateTime: String? = null,
    @SerialName("triggerDateTime") val triggerDateTime: String? = null,
    @SerialName("dueDateTime") val dueDateTime: String? = null,
    @SerialName("start_local") val startLocal: String? = null,
    @SerialName("end_local") val endLocal: String? = null,
    @SerialName("trigger_local") val triggerLocal: String? = null,
    @SerialName("due_local") val dueLocal: String? = null,
    @SerialName("all_day") val allDay: Boolean = false,
    @SerialName("reminder_offsets") val reminderOffsets: List<Int> = emptyList(),
    @SerialName("reminder_minutes") val reminderMinutes: List<Int> = emptyList(),
    @SerialName("recurrence_rule") val recurrenceRule: String? = null,
    @SerialName("repeat_frequency") val repeatFrequency: String? = null,
    @SerialName("repeat_count") val repeatCount: Int? = null,
    val priority: String? = null,
    val completed: Boolean = false,
    val confidence: Double = 0.0,
    @SerialName("missing_fields") val missingFields: List<String> = emptyList(),
    @SerialName("ambiguous_fields") val ambiguousFields: List<String> = emptyList(),
)
