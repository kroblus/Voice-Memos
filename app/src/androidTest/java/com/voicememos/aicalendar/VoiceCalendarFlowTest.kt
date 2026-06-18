package com.voicememos.aicalendar

import android.Manifest
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.rule.GrantPermissionRule
import com.voicememos.aicalendar.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VoiceCalendarFlowTest {
    @get:Rule(order = 0)
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mockDeepSeekFlowCreatesEventsInCalendar() {
        composeRule.assertInputActionButtonsDoNotOverlap()
        composeRule.assertMonthHeaderAlignsWithDates()
        composeRule.onNodeWithTag("theme-toggle-button").performClick()
        composeRule.onNodeWithTag("theme-toggle-button").performClick()

        composeRule.onNodeWithTag("settings-button").performClick()
        composeRule.waitUntilNodeExists("api-key-field")
        composeRule.onNodeWithTag("api-key-field").performTextReplacement("mock-deepseek")
        composeRule.onNodeWithTag("api-key-field").assertTextContains("mock-deepseek")
        closeSoftKeyboard()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings-save-button").performClick()
        composeRule.waitUntilNodeDoesNotExist("api-key-field")

        composeRule.onNodeWithTag("transcript-field")
            .performTextReplacement("明天中午11点提醒我打开电脑12点提醒我打开手机")
        composeRule.onNodeWithTag("transcript-field").assertTextContains("打开电脑", substring = true)
        closeSoftKeyboard()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("generate-button")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitUntilNodeExists("draft-panel")
        composeRule.assertAnyTextDisplayed("打开电脑")
        composeRule.assertAnyTextExists("打开手机")

        composeRule.saveAllDrafts()

        composeRule.assertAnyTextDisplayed("打开电脑")
        composeRule.scrollEventListToText("打开手机")
        composeRule.assertAnyTextDisplayed("打开手机")

        composeRule.onNodeWithTag("settings-button").performClick()
        composeRule.waitUntilNodeExists("api-key-field")
        composeRule.onNodeWithTag("settings-clear-key-button").performClick()
        composeRule.waitUntilNodeDoesNotExist("api-key-field")
    }

    @Test
    fun mockDailyRepeatCreatesMultipleLocalEvents() {
        composeRule.onNodeWithTag("settings-button").performClick()
        composeRule.waitUntilNodeExists("api-key-field")
        composeRule.onNodeWithTag("api-key-field").performTextReplacement("mock-deepseek")
        closeSoftKeyboard()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings-save-button").performClick()
        composeRule.waitUntilNodeDoesNotExist("api-key-field")

        composeRule.onNodeWithTag("transcript-field")
            .performTextReplacement("每天9点提醒我喝水")
        closeSoftKeyboard()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("generate-button")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitUntilNodeExists("draft-panel")
        composeRule.assertAnyTextDisplayed("喝水")
        composeRule.assertAnyTextContains("每天，共 7 次")
        composeRule.saveAllDrafts()

        val savedCount = runBlocking {
            AppDatabase.get(ApplicationProvider.getApplicationContext())
                .calendarEventDao()
                .countByTitle("喝水")
        }
        assertTrue("Expected at least 7 repeated events, found $savedCount", savedCount >= 7)
    }

    @Test
    fun dayTimelineAndExportControlsAreAvailable() {
        composeRule.onNodeWithTag("settings-button").performClick()
        composeRule.waitUntilNodeExists("api-key-field")
        composeRule.onNodeWithTag("api-key-field").performTextReplacement("mock-deepseek")
        closeSoftKeyboard()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings-save-button").performClick()
        composeRule.waitUntilNodeDoesNotExist("api-key-field")

        composeRule.onNodeWithTag("transcript-field")
            .performTextReplacement("明天中午11点提醒我打开电脑")
        closeSoftKeyboard()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("generate-button")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitUntilNodeExists("draft-panel")
        composeRule.saveAllDrafts()

        composeRule.onNodeWithTag("view-mode-day").performClick()
        composeRule.onNodeWithTag("day-next-event-banner").assertIsDisplayed()
        composeRule.onAllNodesWithTag("day-timeline-row").onFirst().assertIsDisplayed()

        composeRule.onNodeWithTag("settings-button").performClick()
        composeRule.waitUntilNodeExists("api-key-field")
        composeRule.onNodeWithTag("settings-export-json-button").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-export-ics-button").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.assertInputActionButtonsDoNotOverlap() {
        val voiceBounds = onNodeWithTag("voice-input-button").fetchSemanticsNode().boundsInRoot
        val sendBounds = onNodeWithTag("generate-button").fetchSemanticsNode().boundsInRoot
        val separated = voiceBounds.right <= sendBounds.left ||
            sendBounds.right <= voiceBounds.left ||
            voiceBounds.bottom <= sendBounds.top ||
            sendBounds.bottom <= voiceBounds.top
        assertTrue(
            "Voice and send buttons overlap. voice=$voiceBounds send=$sendBounds",
            separated,
        )
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.assertMonthHeaderAlignsWithDates() {
        val mondayCenter = onNodeWithTag("weekday-1").fetchSemanticsNode().boundsInRoot.center.x
        val firstDayCenter = onNodeWithTag("day-1").fetchSemanticsNode().boundsInRoot.center.x
        assertTrue(
            "Weekday header and date column are misaligned. weekday=$mondayCenter day=$firstDayCenter",
            kotlin.math.abs(mondayCenter - firstDayCenter) <= 1f,
        )
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.scrollEventListToText(text: String) {
        onNodeWithTag("event-list").performScrollToNode(hasText(text))
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitUntilNodeExists(
        tag: String,
        timeoutMillis: Long = 10_000,
    ) {
        try {
            waitUntil(timeoutMillis) {
                runCatching {
                    onNodeWithTag(tag).assertExists()
                }.isSuccess
            }
        } catch (error: Throwable) {
            val semantics = runCatching {
                onRoot(useUnmergedTree = true).printToString()
            }.getOrElse { treeError ->
                "Compose semantics unavailable: ${treeError.message}"
            }
            throw AssertionError(
                "Timed out waiting for node tag '$tag'. Current semantics:\n" +
                    semantics,
                error,
            )
        }
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitUntilNodeDoesNotExist(
        tag: String,
        timeoutMillis: Long = 10_000,
    ) {
        waitUntil(timeoutMillis) {
            runCatching {
                onNodeWithTag(tag).assertDoesNotExist()
            }.isSuccess
        }
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.saveAllDrafts() {
        onNodeWithTag("draft-save-all-button").performSemanticsAction(SemanticsActions.OnClick)
        waitUntilNodeDoesNotExist("draft-panel")
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.assertAnyTextDisplayed(
        text: String,
    ) {
        assertTrue(
            "Expected at least one node with text '$text'.",
            onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.assertAnyTextExists(
        text: String,
    ) {
        onAllNodesWithText(text).onFirst().assertExists()
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.assertAnyTextContains(
        text: String,
    ) {
        assertTrue(
            "Expected at least one node containing text '$text'.",
            onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty(),
        )
    }
}
