package com.voicememos.aicalendar.data

import com.voicememos.aicalendar.domain.ParseHistoryItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val ParseHistoryJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal fun encodeParseHistory(items: List<ParseHistoryItem>): String =
    ParseHistoryJson.encodeToString(
        ListSerializer(PersistedParseHistoryItem.serializer()),
        items.map { it.toPersisted() },
    )

internal fun decodeParseHistory(raw: String?): List<ParseHistoryItem> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        ParseHistoryJson.decodeFromString(
            ListSerializer(PersistedParseHistoryItem.serializer()),
            raw,
        ).map { it.toDomain() }
    }.getOrDefault(emptyList())
}

private fun ParseHistoryItem.toPersisted(): PersistedParseHistoryItem =
    PersistedParseHistoryItem(
        id = id,
        transcript = transcript,
        summary = summary,
        draftCount = draftCount,
        createdAtEpochMillis = createdAtEpochMillis,
    )

private fun PersistedParseHistoryItem.toDomain(): ParseHistoryItem =
    ParseHistoryItem(
        id = id,
        transcript = transcript,
        summary = summary,
        draftCount = draftCount,
        createdAtEpochMillis = createdAtEpochMillis,
    )

@Serializable
private data class PersistedParseHistoryItem(
    val id: String,
    val transcript: String,
    val summary: String,
    val draftCount: Int,
    val createdAtEpochMillis: Long,
)
