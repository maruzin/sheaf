package com.sheaf.app

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Drives the real app on a device/emulator: open a PDF via "Open with", read, search, reading
 * mode, themes, rotation, password-protected files, tabs and removing from the library.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ReaderEndToEndTest {

    @get:Rule val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val timeout = 15_000L

    private fun launchWith(file: File): ActivityScenario<MainActivity> {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(Uri.fromFile(file), "application/pdf")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return ActivityScenario.launch(intent)
    }

    /** Opening via "Open with" on a fresh install stacks the reader over onboarding. */
    private fun skipOnboardingIfShown() {
        // After Back we land on onboarding (fresh install) or the library: wait for either.
        compose.waitUntil(timeout) {
            compose.onAllNodesWithText("Get started").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Open PDF").fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithText("Get started").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Get started").performClick()
        }
    }

    private fun waitForDescription(description: String) =
        compose.waitUntilAtLeastOneExists(hasContentDescription(description), timeout)

    private fun waitForText(text: String, substring: Boolean = false) =
        compose.waitUntilAtLeastOneExists(hasText(text, substring = substring), timeout)

    @Test
    fun readSearchReflowThemeAndRotate() {
        val pdf = TestPdfs.plain(context, "e2e-read.pdf")
        launchWith(pdf).use { scenario ->
            waitForDescription("Page 1 of 3")

            // Search finds the word on the last page.
            compose.onNodeWithContentDescription("Search").performClick()
            compose.onNode(hasSetTextAction()).performTextInput(TestPdfs.SECRET_WORD)
            compose.onNode(hasSetTextAction()).performImeAction()
            waitForDescription("Result 1 of 1")
            compose.onNodeWithContentDescription("Search").performClick() // close search

            // Reading mode shows the extracted text as paragraphs.
            compose.onNodeWithContentDescription("Reading mode").performClick()
            waitForText(TestPdfs.SECRET_WORD, substring = true)
            compose.onNodeWithContentDescription("Larger text").performClick()
            waitForText("110%")
            compose.onNodeWithContentDescription("Reading mode").performClick()

            // Night theme renders without crashing.
            compose.onNodeWithContentDescription("More").performClick()
            compose.onNodeWithText("Dark").performClick()
            waitForDescription("Page 1 of 3")

            // Rotation / recreation keeps the reader open on the same document.
            scenario.recreate()
            waitForDescription("Page 1 of 3")
            compose.onNodeWithText(pdf.name).assertExists()
        }
    }

    @Test
    fun protectedPdfRejectsWrongPasswordThenOpens() {
        val pdf = TestPdfs.protected(context, "e2e-locked.pdf")
        launchWith(pdf).use {
            waitForText("Password required")
            compose.onNode(hasSetTextAction()).performTextInput("wrong")
            compose.onNodeWithText("Open").performClick()
            waitForText("Incorrect password", substring = true)
            compose.onNode(hasSetTextAction()).performTextClearance()
            compose.onNode(hasSetTextAction()).performTextInput(TestPdfs.PASSWORD)
            compose.onNodeWithText("Open").performClick()
            waitForDescription("Page 1 of 1")
        }
    }

    @Test
    fun tabsSwitchBetweenOpenDocuments() {
        val first = TestPdfs.plain(context, "e2e-tab-one.pdf", pages = 2)
        val second = TestPdfs.plain(context, "e2e-tab-two.pdf", pages = 1)
        launchWith(first).use { waitForDescription("Page 1 of 2") }
        launchWith(second).use {
            waitForDescription("Page 1 of 1")
            // Both documents are open as tabs; switch back to the first.
            waitForText("e2e-tab-one")
            compose.onNodeWithText("e2e-tab-one").performClick()
            waitForDescription("Page 1 of 2")
        }
    }

    @Test
    fun removeFromLibrary() {
        val pdf = TestPdfs.plain(context, "e2e-remove.pdf", pages = 1)
        launchWith(pdf).use {
            waitForDescription("Page 1 of 1")
            compose.onNodeWithContentDescription("Back").performClick()
            skipOnboardingIfShown()
            waitForText("Open PDF")
            compose.onAllNodesWithText(pdf.name).fetchSemanticsNodes().let { check(it.isNotEmpty()) }
            compose.onNodeWithContentDescription("More options for ${pdf.name}", useUnmergedTree = true)
                .performClick()
            compose.onNodeWithText("Remove from library").performClick()
            compose.onNodeWithText("Remove").performClick()
            compose.waitUntilDoesNotExist(hasText(pdf.name), timeout)
        }
    }
}
