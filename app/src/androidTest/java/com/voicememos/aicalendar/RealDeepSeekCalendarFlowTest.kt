package com.voicememos.aicalendar

import android.Manifest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class RealDeepSeekCalendarFlowTest {
    @get:Rule(order = 0)
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun realDeepSeekFlowCreatesEventsInCalendar() {
        val apiKey = InstrumentationRegistry.getArguments().getString("deepseekApiKey").orEmpty()
        assumeTrue("Pass -PrunRealDeepSeekTests=true with deepseekapi.txt to run this test.", apiKey.isNotBlank())

        composeRule.onNodeWithTag("settings-button").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntilNodeExists("api-key-field")
        composeRule.onNodeWithTag("api-key-field").performTextReplacement(apiKey)
        closeSoftKeyboard()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings-save-button").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntilNodeDoesNotExist("api-key-field")

        composeRule.onNodeWithTag("transcript-field")
            .performTextReplacement("明天上午九点提醒我给张三打电话，下午三点开项目会")
        closeSoftKeyboard()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("generate-button").performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitUntilNodeExists("draft-panel", timeoutMillis = 90_000)
        composeRule.assertAnyTextDisplayed("给张三打电话", substring = true)
        composeRule.assertAnyTextExists("项目", substring = true)

        composeRule.onAllNodesWithTag("draft-save-button")[0]
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("draft-save-button")[0]
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        composeRule.assertAnyTextDisplayed("给张三打电话", substring = true)
        composeRule.assertAnyTextExists("项目", substring = true)
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
                "Timed out waiting for node tag '$tag'. Current semantics:\n$semantics",
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

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.assertAnyTextDisplayed(
        text: String,
        substring: Boolean = false,
    ) {
        onAllNodesWithText(text, substring = substring).onFirst().assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.assertAnyTextExists(
        text: String,
        substring: Boolean = false,
    ) {
        onAllNodesWithText(text, substring = substring).onFirst().assertExists()
    }
}
