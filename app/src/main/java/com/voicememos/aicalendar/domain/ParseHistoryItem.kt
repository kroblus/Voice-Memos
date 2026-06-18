package com.voicememos.aicalendar.domain

data class ParseHistoryItem(
    val id: String,
    val transcript: String,
    val summary: String,
    val draftCount: Int,
    val createdAtEpochMillis: Long,
)
