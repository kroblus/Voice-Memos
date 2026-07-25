package com.voicememos.aicalendar.domain

data class ParseHistoryItem(
    val id: String,
    val transcript: String,
    val summary: String,
    val draftCount: Int,
    val createdAtEpochMillis: Long,
    val itemSummaries: List<ParseHistoryScheduleItemSummary> = emptyList(),
    val creationStatus: ParseHistoryCreationStatus = ParseHistoryCreationStatus.NOT_ATTEMPTED,
    val failureReason: String? = null,
    val createdItemIds: List<String> = emptyList(),
    val isUndone: Boolean = false,
) {
    val parsedAtEpochMillis: Long
        get() = createdAtEpochMillis

    val parsedItemCount: Int
        get() = draftCount
}

data class ParseHistoryScheduleItemSummary(
    val itemType: ScheduleItemType = ScheduleItemType.EVENT,
    val title: String = "",
    val timeSummary: String? = null,
    val location: String? = null,
    val missingFields: List<String> = emptyList(),
    val ambiguousFields: List<String> = emptyList(),
)

enum class ParseHistoryCreationStatus {
    NOT_ATTEMPTED,
    SUCCESS,
    FAILURE,
    PARTIAL_SUCCESS,
}
