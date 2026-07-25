package com.voicememos.aicalendar.ui.session

import com.voicememos.aicalendar.domain.ScheduleItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreationBatchSummaryTest {
    @Test
    fun countsCreatedIdsWarningsAndFailuresAcrossMixedOutcomes() {
        val created = outcome(
            draftId = "created",
            status = CreationItemStatus.CREATED,
            createdItemIds = listOf("event-1", "event-2"),
        )
        val warning = outcome(
            draftId = "warning",
            status = CreationItemStatus.CREATED_WITH_WARNING,
            createdItemIds = listOf("event-2", "event-3"),
            message = "Notification was not scheduled",
        )
        val failed = outcome(
            draftId = "failed",
            status = CreationItemStatus.FAILED,
            message = "Storage failed",
        )

        val summary = CreationBatchSummary(listOf(created, warning, failed))

        assertEquals(listOf("event-1", "event-2", "event-3"), summary.createdItemIds)
        assertEquals(3, summary.createdCount)
        assertEquals(2, created.createdCount)
        assertEquals(2, warning.createdCount)
        assertEquals(listOf(warning), summary.warningOutcomes)
        assertEquals(listOf(failed), summary.failedOutcomes)
        assertEquals(1, summary.warningOutcomes.size)
        assertEquals(1, summary.failedCount)
        assertTrue(summary.hasWarnings)
        assertTrue(summary.hasFailures)
    }

    @Test
    fun anyCreatedItemMakesMixedBatchUndoable() {
        val summary = CreationBatchSummary(
            listOf(
                outcome(
                    draftId = "warning",
                    status = CreationItemStatus.CREATED_WITH_WARNING,
                    createdItemIds = listOf("todo-1"),
                ),
                outcome(
                    draftId = "failed",
                    status = CreationItemStatus.FAILED,
                ),
            ),
        )

        assertTrue(summary.canUndo)
        assertEquals(listOf("todo-1"), summary.createdItemIds)
    }

    @Test
    fun failedOrEmptyBatchWithoutCreatedIdsCannotUndo() {
        val failedSummary = CreationBatchSummary(
            listOf(
                outcome(
                    draftId = "failed",
                    status = CreationItemStatus.FAILED,
                ),
            ),
        )
        val emptySummary = CreationBatchSummary(emptyList())

        assertFalse(failedSummary.canUndo)
        assertEquals(0, failedSummary.createdCount)
        assertEquals(1, failedSummary.failedCount)
        assertFalse(failedSummary.hasWarnings)

        assertFalse(emptySummary.canUndo)
        assertEquals(0, emptySummary.createdCount)
        assertEquals(0, emptySummary.failedCount)
        assertFalse(emptySummary.hasFailures)
        assertFalse(emptySummary.hasWarnings)
    }

    @Test
    fun retryMergePreservesEarlierCreatedItemsAndReplacesLatestDraftStatus() {
        val firstAttempt = CreationBatchSummary(
            listOf(
                outcome(
                    draftId = "created",
                    status = CreationItemStatus.CREATED,
                    createdItemIds = listOf("event-1"),
                ),
                outcome(
                    draftId = "retried",
                    status = CreationItemStatus.FAILED,
                    createdItemIds = listOf("event-2"),
                    message = "First failure",
                ),
            ),
        )
        val retry = CreationBatchSummary(
            listOf(
                outcome(
                    draftId = "retried",
                    status = CreationItemStatus.FAILED,
                    message = "Still failing",
                ),
            ),
        )

        val merged = firstAttempt.mergedWith(retry)

        assertEquals(listOf("event-1", "event-2"), merged.createdItemIds)
        assertTrue(merged.canUndo)
        assertEquals(1, merged.failedCount)
        assertEquals("Still failing", merged.failedOutcomes.single().message)
        assertEquals(listOf("event-2"), merged.failedOutcomes.single().createdItemIds)
    }

    @Test
    fun successfulRetryReplacesFailureWithoutHidingEarlierSuccess() {
        val initial = CreationBatchSummary(
            listOf(
                outcome(
                    draftId = "created",
                    status = CreationItemStatus.CREATED,
                    createdItemIds = listOf("event-1"),
                ),
                outcome(
                    draftId = "retried",
                    status = CreationItemStatus.FAILED,
                ),
            ),
        )
        val retry = CreationBatchSummary(
            listOf(
                outcome(
                    draftId = "retried",
                    status = CreationItemStatus.CREATED,
                    createdItemIds = listOf("event-2"),
                ),
            ),
        )

        val merged = initial.mergedWith(retry)

        assertFalse(merged.hasFailures)
        assertEquals(listOf("event-1", "event-2"), merged.createdItemIds)
        assertEquals(2, merged.createdCount)
    }

    private fun outcome(
        draftId: String,
        status: CreationItemStatus,
        createdItemIds: List<String> = emptyList(),
        message: String? = null,
    ): CreationItemOutcome =
        CreationItemOutcome(
            draftId = draftId,
            title = "Item $draftId",
            itemType = ScheduleItemType.EVENT,
            status = status,
            createdItemIds = createdItemIds,
            message = message,
        )
}
