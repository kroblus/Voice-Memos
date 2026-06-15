package com.voicememos.aicalendar.domain

data class AiParseResult(
    val events: List<DraftCalendarEvent>,
    val warnings: List<String>,
    val needsConfirmation: Boolean,
)

