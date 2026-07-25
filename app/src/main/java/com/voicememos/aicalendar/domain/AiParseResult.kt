package com.voicememos.aicalendar.domain

data class AiParseResult(
    val events: List<DraftCalendarEvent>,
    val warnings: List<String>,
    val needsConfirmation: Boolean,
    val requiresClarification: Boolean = false,
    val clarificationQuestions: List<String> = emptyList(),
) {
    val items: List<DraftCalendarEvent>
        get() = events
}
