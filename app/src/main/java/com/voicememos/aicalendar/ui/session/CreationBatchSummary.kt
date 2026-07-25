package com.voicememos.aicalendar.ui.session

import com.voicememos.aicalendar.domain.ScheduleItemType

enum class CreationItemStatus {
    CREATED,
    CREATED_WITH_WARNING,
    FAILED,
}

data class CreationItemOutcome(
    val draftId: String,
    val title: String,
    val itemType: ScheduleItemType,
    val status: CreationItemStatus,
    val createdItemIds: List<String> = emptyList(),
    val message: String? = null,
) {
    val createdCount: Int
        get() = createdItemIds.size
}

data class CreationBatchSummary(
    val outcomes: List<CreationItemOutcome>,
) {
    val createdItemIds: List<String>
        get() = outcomes.flatMap(CreationItemOutcome::createdItemIds).distinct()

    val createdCount: Int
        get() = createdItemIds.size

    val failedOutcomes: List<CreationItemOutcome>
        get() = outcomes.filter { it.status == CreationItemStatus.FAILED }

    val warningOutcomes: List<CreationItemOutcome>
        get() = outcomes.filter { it.status == CreationItemStatus.CREATED_WITH_WARNING }

    val failedCount: Int
        get() = failedOutcomes.size

    val hasFailures: Boolean
        get() = failedOutcomes.isNotEmpty()

    val hasWarnings: Boolean
        get() = warningOutcomes.isNotEmpty()

    val canUndo: Boolean
        get() = createdItemIds.isNotEmpty()

    fun mergedWith(newer: CreationBatchSummary): CreationBatchSummary {
        val mergedByDraftId = linkedMapOf<String, CreationItemOutcome>()
        outcomes.forEach { outcome -> mergedByDraftId[outcome.draftId] = outcome }
        newer.outcomes.forEach { outcome ->
            val previous = mergedByDraftId[outcome.draftId]
            mergedByDraftId[outcome.draftId] = outcome.copy(
                createdItemIds = (
                    previous?.createdItemIds.orEmpty() + outcome.createdItemIds
                    ).distinct(),
            )
        }
        return CreationBatchSummary(mergedByDraftId.values.toList())
    }
}
