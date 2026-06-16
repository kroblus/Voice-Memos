package com.voicememos.aicalendar

import android.Manifest
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

        composeRule.onNodeWithTag("settings-button").performClick()
        composeRule.waitUntilNodeExists("api-key-field")
        composeRule.onNodeWithTag("api-key-field").performTextReplacement("mock-deepseek")
        composeRule.onNodeWithTag("api-key-field").assertTextContains("mock-deepseek")
        composeRule.onNodeWithTag("theme-dark-button").performClick()
        composeRule.onNodeWithTag("theme-light-button").performClick()
        closeSoftKeyboard()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings-save-button").performClick()
        composeRule.waitUntilNodeDoesNotExist("api-key-field")

        composeRule.onNodeWithTag("transcript-field")
            .performTextReplacement("明天上午九点提醒我给张三打电话，下午三点开项目会")
        composeRule.onNodeWithTag("transcript-field").assertTextContains("给张三打电话", substring = true)
        closeSoftKeyboard()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("generate-button")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitUntilNodeExists("draft-panel")
        composeRule.assertAnyTextDisplayed("给张三打电话")
        composeRule.assertAnyTextExists("项目会")

        composeRule.saveAllDrafts()

        composeRule.assertAnyTextDisplayed("给张三打电话")
        composeRule.scrollEventListToText("项目会")
        composeRule.assertAnyTextDisplayed("项目会")

        composeRule.onNodeWithTag("settings-button").performClick()
        composeRule.waitUntilNodeExists("api-key-field")
        composeRule.onNodeWithTag("settings-clear-key-button").performClick()
        composeRule.waitUntilNodeDoesNotExist("api-key-field")
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
        repeat(6) {
            val saveButtons = onAllNodesWithTag("draft-save-button").fetchSemanticsNodes()
            if (saveButtons.isEmpty()) {
                waitUntilNodeDoesNotExist("draft-panel")
                return
            }
            onAllNodesWithTag("draft-save-button")[0].performSemanticsAction(SemanticsActions.OnClick)
            waitForIdle()
        }
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
}
