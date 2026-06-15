package com.voicememos.aicalendar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class VoiceCalendarFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mockDeepSeekFlowCreatesEventsInCalendar() {
        composeRule.onNodeWithTag("settings-button").performClick()
        composeRule.waitUntilNodeExists("api-key-field")
        composeRule.onNodeWithTag("api-key-field").performTextInput("mock-deepseek")
        composeRule.onNodeWithTag("settings-save-button").performClick()

        composeRule.onNodeWithTag("transcript-field")
            .performTextInput("明天上午九点提醒我给张三打电话，下午三点开项目会")
        composeRule.onNodeWithTag("generate-button").performClick()

        composeRule.waitUntilNodeExists("draft-panel")
        composeRule.assertAnyTextDisplayed("给张三打电话")
        composeRule.assertAnyTextDisplayed("项目会")

        composeRule.onAllNodesWithTag("draft-save-button")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("draft-save-button")[0].performClick()
        composeRule.waitForIdle()

        composeRule.assertAnyTextDisplayed("给张三打电话")
        composeRule.assertAnyTextDisplayed("项目会")
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitUntilNodeExists(
        tag: String,
        timeoutMillis: Long = 10_000,
    ) {
        waitUntil(timeoutMillis) {
            runCatching {
                onNodeWithTag(tag).assertExists()
            }.isSuccess
        }
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.assertAnyTextDisplayed(
        text: String,
    ) {
        onAllNodesWithText(text).onFirst().assertIsDisplayed()
    }
}
