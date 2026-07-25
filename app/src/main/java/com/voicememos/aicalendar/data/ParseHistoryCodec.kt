package com.voicememos.aicalendar.data

import com.voicememos.aicalendar.domain.ParseHistoryItem
import com.voicememos.aicalendar.domain.ParseHistoryCreationStatus
import com.voicememos.aicalendar.domain.ParseHistoryScheduleItemSummary
import com.voicememos.aicalendar.domain.ScheduleItemType
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
        parsedAtEpochMillis = parsedAtEpochMillis,
        parsedItemCount = parsedItemCount,
        itemSummaries = itemSummaries.map { it.toPersisted() },
        creationStatus = creationStatus.name,
        failureReason = failureReason,
        createdItemIds = createdItemIds,
        isUndone = isUndone,
    )

private fun PersistedParseHistoryItem.toDomain(): ParseHistoryItem =
    ParseHistoryItem(
        id = id,
        transcript = transcript,
        summary = summary,
        draftCount = parsedItemCount ?: draftCount,
        createdAtEpochMillis = parsedAtEpochMillis ?: createdAtEpochMillis,
        itemSummaries = itemSummaries.map { it.toDomain() },
        creationStatus = creationStatus.toCreationStatus(),
        failureReason = failureReason,
        createdItemIds = createdItemIds,
        isUndone = isUndone,
    )

private fun ParseHistoryScheduleItemSummary.toPersisted(): PersistedParseHistoryScheduleItemSummary =
    PersistedParseHistoryScheduleItemSummary(
        itemType = itemType.name,
        title = title,
        timeSummary = timeSummary,
        location = location,
        missingFields = missingFields,
        ambiguousFields = ambiguousFields,
    )

private fun PersistedParseHistoryScheduleItemSummary.toDomain(): ParseHistoryScheduleItemSummary =
    ParseHistoryScheduleItemSummary(
        itemType = itemType.toScheduleItemType(),
        title = title,
        timeSummary = timeSummary,
        location = location,
        missingFields = missingFields,
        ambiguousFields = ambiguousFields,
    )

private fun String.toCreationStatus(): ParseHistoryCreationStatus =
    runCatching { ParseHistoryCreationStatus.valueOf(uppercase()) }
        .getOrDefault(ParseHistoryCreationStatus.NOT_ATTEMPTED)

private fun String.toScheduleItemType(): ScheduleItemType =
    runCatching { ScheduleItemType.valueOf(uppercase()) }
        .getOrDefault(ScheduleItemType.EVENT)

@Serializable
private data class PersistedParseHistoryItem(
    val id: String = "",
    val transcript: String = "",
    val summary: String = "",
    val draftCount: Int = 0,
    val createdAtEpochMillis: Long = 0,
    val parsedAtEpochMillis: Long? = null,
    val parsedItemCount: Int? = null,
    val itemSummaries: List<PersistedParseHistoryScheduleItemSummary> = emptyList(),
    val creationStatus: String = ParseHistoryCreationStatus.NOT_ATTEMPTED.name,
    val failureReason: String? = null,
    val createdItemIds: List<String> = emptyList(),
    val isUndone: Boolean = false,
)

@Serializable
private data class PersistedParseHistoryScheduleItemSummary(
    val itemType: String = ScheduleItemType.EVENT.name,
    val title: String = "",
    val timeSummary: String? = null,
    val location: String? = null,
    val missingFields: List<String> = emptyList(),
    val ambiguousFields: List<String> = emptyList(),
)
