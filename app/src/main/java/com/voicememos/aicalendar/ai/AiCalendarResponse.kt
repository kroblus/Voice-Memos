package com.voicememos.aicalendar.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AiCalendarResponse(
    val events: List<AiCalendarEvent> = emptyList(),
    val warnings: List<String> = emptyList(),
)

@Serializable
data class AiCalendarEvent(
    val title: String = "",
    val notes: String? = null,
    @SerialName("start_local") val startLocal: String? = null,
    @SerialName("end_local") val endLocal: String? = null,
    @SerialName("all_day") val allDay: Boolean = false,
    @SerialName("reminder_minutes") val reminderMinutes: List<Int> = emptyList(),
    @SerialName("repeat_frequency") val repeatFrequency: String? = null,
    @SerialName("repeat_count") val repeatCount: Int? = null,
    val confidence: Double = 0.0,
    @SerialName("missing_fields") val missingFields: List<String> = emptyList(),
)
