package com.voicememos.aicalendar

import android.Manifest
import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.GrantPermissionRule
import com.voicememos.aicalendar.data.AppDatabase
import com.voicememos.aicalendar.security.SecureApiKeyStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
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

        composeRule.resetLocalState()
        composeRule.dismissOnboardingIfPresent()
        composeRule.onNodeWithTag("settings-button").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntilNodeExists("api-key-field")
        composeRule.onNodeWithTag("api-key-field").performTextReplacement(apiKey)
        closeSoftKeyboard()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings-save-button").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntilNodeDoesNotExist("api-key-field")

        composeRule.onNodeWithTag("transcript-field")
            .performTextReplacement("明天中午11点提醒我打开电脑12点提醒我打开手机")
        closeSoftKeyboard()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("generate-button").performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitUntilNodeExists("draft-panel", timeoutMillis = 90_000)
        composeRule.assertAnyTextDisplayed("电脑", substring = true)
        composeRule.assertAnyTextExists("手机", substring = true)

        composeRule.saveAllDrafts()

        composeRule.assertAnyTextDisplayed("电脑", substring = true)
        composeRule.scrollEventListToText("手机", substring = true)
        composeRule.assertAnyTextExists("手机", substring = true)
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

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.scrollEventListToText(
        text: String,
        substring: Boolean = false,
    ) {
        onNodeWithTag("event-list").performScrollToNode(hasText(text, substring = substring))
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
        SecureApiKeyStore(context).clear()
        waitForIdle()
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.assertAnyTextDisplayed(
        text: String,
        substring: Boolean = false,
    ) {
        assertTrue(
            "Expected at least one node with text '$text'.",
            onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.assertAnyTextExists(
        text: String,
        substring: Boolean = false,
    ) {
        onAllNodesWithText(text, substring = substring).onFirst().assertExists()
    }
}
