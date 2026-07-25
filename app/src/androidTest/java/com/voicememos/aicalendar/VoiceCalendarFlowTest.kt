package com.voicememos.aicalendar

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
import com.voicememos.aicalendar.security.SecureApiKeyStore
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VoiceCalendarFlowTest {
    @get:Rule(order = 0)
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.RECORD_AUDIO)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mockDeepSeekFlowCreatesEventsInCalendar() {
        composeRule.resetLocalState()
        composeRule.dismissOnboardingIfPresent()
        composeRule.assertMainInputIsCollapsed()
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

        composeRule.openInputCurtainForTextEntry()
        composeRule.onNodeWithTag("transcript-field")
            .performTextReplacement("明天中午11点提醒我打开电脑12点提醒我打开手机")
        composeRule.onNodeWithTag("transcript-field").assertTextContains("打开电脑", substring = true)
        composeRule.onNodeWithTag("generate-button").assertTextContains("整理日程")
        closeSoftKeyboard()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("generate-button")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitUntilNodeExists("draft-panel")
        composeRule.waitUntilAnyTextContains("打开电脑")
        composeRule.assertAnyTextContains("打开手机")
        composeRule.onNodeWithTag("draft-save-all-button").assertTextContains("创建 2 项")

        composeRule.saveAllDrafts("打开电脑", "打开手机")

        composeRule.waitUntilAnyTextContains("打开电脑")
        composeRule.scrollEventListToText("打开手机")
        composeRule.assertAnyTextContains("打开手机")
        composeRule.openInputCurtainForTextEntry()
        composeRule.onNodeWithTag("parse-history-row").assertDoesNotExist()
        assertTrue(
            "Recording curtain must not embed recognition history actions.",
            composeRule.onAllNodesWithText("查看上次结果").fetchSemanticsNodes().isEmpty(),
        )
        composeRule.closeInputCurtain()

        composeRule.onNodeWithTag("history-button").performClick()
        composeRule.waitUntilNodeExists("parse-history-row")
        composeRule.waitUntilAnyTextContains(
            "明天中午11点提醒我打开电脑12点提醒我打开手机",
        )
        composeRule.onAllNodesWithText("查看上次结果").onFirst().performClick()
        composeRule.waitUntilAnyTextContains("提醒 · 打开电脑")
        composeRule.assertAnyTextContains("提醒 · 打开手机")
        composeRule.onNodeWithContentDescription("关闭识别历史").performClick()
        composeRule.waitUntilNodeDoesNotExist("parse-history-row")

        composeRule.onNodeWithTag("settings-button").performClick()
        composeRule.waitUntilNodeExists("api-key-field")
        composeRule.onNodeWithTag("settings-clear-key-button").performClick()
        composeRule.onNodeWithTag("settings-cancel-button").performClick()
        composeRule.waitUntilNodeDoesNotExist("api-key-field")
    }

    @Test
    fun mockDailyRepeatCreatesMultipleLocalEvents() {
        composeRule.resetLocalState()
        composeRule.dismissOnboardingIfPresent()
        composeRule.onNodeWithTag("settings-button").performClick()
        composeRule.waitUntilNodeExists("api-key-field")
        composeRule.onNodeWithTag("api-key-field").performTextReplacement("mock-deepseek")
        closeSoftKeyboard()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings-save-button").performClick()
        composeRule.waitUntilNodeDoesNotExist("api-key-field")

        composeRule.openInputCurtainForTextEntry()
        composeRule.onNodeWithTag("transcript-field")
            .performTextReplacement("每天9点提醒我喝水")
        closeSoftKeyboard()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("generate-button")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitUntilNodeExists("draft-panel")
        composeRule.assertAnyTextDisplayed("喝水")
        composeRule.assertAnyTextContains("每天，共 7 次")
        composeRule.saveAllDrafts("喝水")

        val savedCount = runBlocking {
            AppDatabase.get(ApplicationProvider.getApplicationContext())
                .calendarEventDao()
                .countByTitle("喝水")
        }
        assertTrue("Expected at least 7 repeated events, found $savedCount", savedCount >= 7)
    }

    @Test
    fun dayTimelineAndExportControlsAreAvailable() {
        composeRule.resetLocalState()
        composeRule.dismissOnboardingIfPresent()
        composeRule.onNodeWithTag("settings-button").performClick()
        composeRule.waitUntilNodeExists("api-key-field")
        composeRule.onNodeWithTag("api-key-field").performTextReplacement("mock-deepseek")
        closeSoftKeyboard()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings-save-button").performClick()
        composeRule.waitUntilNodeDoesNotExist("api-key-field")

        composeRule.openInputCurtainForTextEntry()
        composeRule.onNodeWithTag("transcript-field")
            .performTextReplacement("明天中午11点提醒我打开电脑")
        closeSoftKeyboard()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("generate-button")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitUntilNodeExists("draft-panel")
        composeRule.saveAllDrafts("打开电脑")

        composeRule.onNodeWithTag("view-mode-day").performClick()
        composeRule.onNodeWithTag("day-next-event-banner").assertIsDisplayed()
        composeRule.onAllNodesWithTag("day-timeline-row").onFirst().assertIsDisplayed()

        composeRule.onNodeWithTag("settings-button").performClick()
        composeRule.waitUntilNodeExists("api-key-field")
        composeRule.onNodeWithTag("settings-export-json-button").assertExists()
        composeRule.onNodeWithTag("settings-import-json-button").assertExists()
        composeRule.onNodeWithTag("settings-export-ics-button").assertExists()
        composeRule.onNodeWithTag("settings-privacy-button").assertExists()
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.assertMainInputIsCollapsed() {
        onNodeWithTag("voice-input-button").assertIsDisplayed()
        onNodeWithTag("voice-input-curtain").assertDoesNotExist()
        onNodeWithTag("transcript-field").assertDoesNotExist()
        onNodeWithTag("generate-button").assertDoesNotExist()
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.openInputCurtainForTextEntry() {
        onNodeWithTag("voice-input-button").performSemanticsAction(SemanticsActions.OnClick)
        waitUntilNodeExists("voice-input-curtain")
        onNodeWithTag("curtain-text-input-button")
            .performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.closeInputCurtain() {
        onNodeWithTag("voice-input-curtain-close-button").performSemanticsAction(SemanticsActions.OnClick)
        waitUntilNodeDoesNotExist("voice-input-curtain")
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.assertMonthHeaderAlignsWithDates() {
        val firstDayColumn = YearMonth.now().atDay(1).dayOfWeek.value % 7
        val weekdayCenter = onNodeWithTag("weekday-$firstDayColumn")
            .fetchSemanticsNode()
            .boundsInRoot
            .center
            .x
        val firstDayCenter = onNodeWithTag("day-1").fetchSemanticsNode().boundsInRoot.center.x
        assertTrue(
            "Weekday header and date column are misaligned. " +
                "weekday=$weekdayCenter day=$firstDayCenter",
            kotlin.math.abs(weekdayCenter - firstDayCenter) <= 1f,
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

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.saveAllDrafts(
        vararg expectedTitles: String,
    ) {
        onNodeWithTag("draft-save-all-button").performSemanticsAction(SemanticsActions.OnClick)
        waitUntilNodeExists("creation-view-schedule-button")
        waitUntilAnyTextContains("结果已逐项记录")
        expectedTitles.forEach { title -> assertCreatedOutcome(title) }
        onNodeWithTag("creation-view-schedule-button")
            .assertTextContains("查看日程")
            .performSemanticsAction(SemanticsActions.OnClick)
        waitUntilNodeDoesNotExist("creation-view-schedule-button")
        waitUntilNodeDoesNotExist("draft-panel")
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.assertCreatedOutcome(
        title: String,
    ) {
        waitUntil(10_000) {
            onAllNodesWithText(title, substring = true).fetchSemanticsNodes().isNotEmpty() &&
                onAllNodesWithText("已创建", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        onAllNodesWithText(title, substring = true).onFirst().assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.dismissOnboardingIfPresent() {
        waitForIdle()
        runCatching {
            waitUntil(5_000) {
                onAllNodesWithTag("onboarding-complete-button").fetchSemanticsNodes().isNotEmpty()
            }
        }
        val buttons = onAllNodesWithTag("onboarding-complete-button").fetchSemanticsNodes()
        if (buttons.isNotEmpty()) {
            onNodeWithTag("onboarding-complete-button")
                .performSemanticsAction(SemanticsActions.OnClick)
            waitUntilNodeDoesNotExist("onboarding-sheet")
        }
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.resetLocalState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking {
            AppDatabase.get(context).clearAllTables()
        }
        SecureApiKeyStore(context).apply {
            clear()
            save("mock-deepseek")
        }
        waitForIdle()
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

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitUntilAnyTextContains(
        text: String,
        timeoutMillis: Long = 10_000,
    ) {
        waitUntil(timeoutMillis) {
            onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
