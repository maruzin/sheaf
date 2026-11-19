package com.sheaf.app

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import com.sheaf.feature.reader.TABS_ROW_TAG
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.core.content.FileProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
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
                compose.onAllNodesWithContentDescription("Open file").fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithText("Get started").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Get started").performClick()
        }
    }

    private fun waitForDescription(description: String) =
        compose.waitUntilAtLeastOneExists(hasContentDescription(description), timeout)

    private fun waitForText(text: String, substring: Boolean = false) {
        try {
            compose.waitUntilAtLeastOneExists(hasText(text, substring = substring), timeout)
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            // Put what is actually on screen into the failure so CI logs show the real state.
            throw AssertionError(
                // One line: Gradle's console shows only the first lines of a failure message.
                "Timed out waiting for text '$text'. Screen: " +
                    runCatching { compose.onRoot(useUnmergedTree = true).printToString(maxDepth = 30) }
                        .getOrElse { "<no compose root: ${it.message}>" }
                        .replace("\n", " | "),
                e,
            )
        }
    }

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
        // Unique names: the test must also pass on a device that already has tabs open (the strip
        // scrolls, and earlier runs leave their own e2e documents behind).
        val run = System.currentTimeMillis()
        val firstName = "e2e-tab-one-$run"
        val first = TestPdfs.plain(context, "$firstName.pdf", pages = 2)
        val second = TestPdfs.plain(context, "e2e-tab-two-$run.pdf", pages = 1)
        launchWith(first).use { waitForDescription("Page 1 of 2") }
        launchWith(second).use {
            waitForDescription("Page 1 of 1")
            // Both documents are open as tabs; scroll the strip to the first and switch back to it.
            compose.waitUntilAtLeastOneExists(hasTestTag(TABS_ROW_TAG), timeout)
            compose.onNodeWithTag(TABS_ROW_TAG).performScrollToNode(hasText(firstName))
            compose.onNodeWithText(firstName).performClick()
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
            waitForDescription("Open file")
            compose.onAllNodesWithText(pdf.name).fetchSemanticsNodes().let { check(it.isNotEmpty()) }
            compose.onNodeWithContentDescription("More options for ${pdf.name}", useUnmergedTree = true)
                .performClick()
            compose.onNodeWithText("Remove from library").performClick()
            compose.onNodeWithText("Remove").performClick()
            compose.waitUntilDoesNotExist(hasText(pdf.name), timeout)
        }
    }

    @Test
    fun drawThenUndoAndRedo() {
        val pdf = TestPdfs.plain(context, "e2e-undo.pdf", pages = 1)
        launchWith(pdf).use {
            waitForDescription("Page 1 of 1")
            compose.onNodeWithContentDescription("Annotate").performClick()
            waitForDescription("Undo")
            compose.onNodeWithContentDescription("Undo").assertIsNotEnabled()

            // Draw one pen stroke across the page.
            compose.onNodeWithContentDescription("Page 1 of 1").performTouchInput {
                swipe(Offset(width * 0.2f, height * 0.3f), Offset(width * 0.8f, height * 0.4f), 400)
            }
            compose.waitUntil(timeout) {
                runCatching { compose.onNodeWithContentDescription("Undo").assertIsEnabled() }.isSuccess
            }

            compose.onNodeWithContentDescription("Undo").performClick()
            compose.waitUntil(timeout) {
                runCatching { compose.onNodeWithContentDescription("Redo").assertIsEnabled() }.isSuccess
            }
            compose.onNodeWithContentDescription("Undo").assertIsNotEnabled()

            compose.onNodeWithContentDescription("Redo").performClick()
            compose.waitUntil(timeout) {
                runCatching { compose.onNodeWithContentDescription("Undo").assertIsEnabled() }.isSuccess
            }
            compose.onNodeWithContentDescription("Redo").assertIsNotEnabled()
        }
    }

    @Test
    fun signWithADrawnMultiStrokeSignatureThenMoveIt() {
        val pdf = TestPdfs.plain(context, "e2e-sign-${System.nanoTime()}.pdf", pages = 1)
        launchWith(pdf).use {
            waitForDescription("Page 1 of 1")
            compose.onNodeWithContentDescription("Sign document").performClick()
            // A device that already has a saved signature goes straight to signing: ask for a new one.
            compose.waitUntil(timeout) {
                compose.onAllNodesWithText("Your signature").fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodesWithText("New").fetchSemanticsNodes().isNotEmpty()
            }
            if (compose.onAllNodesWithText("Your signature").fetchSemanticsNodes().isEmpty()) {
                compose.onNodeWithText("New").performClick()
                waitForText("Your signature")
            }
            compose.onNodeWithText("Draw").performClick()
            val pad = "Signature pad. Draw your signature with your finger."
            waitForDescription(pad)
            compose.onNodeWithText("Save signature").assertIsNotEnabled()
            // Two separate strokes: both must survive (the old pad kept only the last one).
            compose.onNodeWithContentDescription(pad).performTouchInput {
                swipe(Offset(width * 0.1f, height * 0.6f), Offset(width * 0.45f, height * 0.4f), 300)
            }
            compose.onNodeWithContentDescription(pad).performTouchInput {
                swipe(Offset(width * 0.55f, height * 0.5f), Offset(width * 0.9f, height * 0.55f), 300)
            }
            compose.onNodeWithText("Undo").assertIsEnabled()
            compose.onNodeWithText("Save signature").performClick()

            // Signing mode: tap the page to place it, then drag it somewhere else.
            waitForText("Tap the page to place your signature", substring = true)
            compose.onNodeWithContentDescription("Page 1 of 1").performTouchInput { click(center) }
            compose.waitUntil(timeout) {
                runCatching { compose.onNodeWithContentDescription("Undo").assertIsEnabled() }.isSuccess
            }
            compose.onNodeWithContentDescription("Page 1 of 1").performTouchInput {
                swipe(center, Offset(width * 0.3f, height * 0.8f), 400)
            }
            compose.onNodeWithText("Done").performClick()

            // The signature is a real annotation on the page.
            waitForDescription("Annotations")
            compose.onNodeWithContentDescription("Annotations").performClick()
            waitForText("Signature")
        }
    }

    @Test
    fun mergeIsProAndShowsThePaywallWithRestore() {
        val pdf = TestPdfs.plain(context, "e2e-merge.pdf", pages = 1)
        launchWith(pdf).use {
            waitForDescription("Page 1 of 1")
            compose.onNodeWithContentDescription("Back").performClick()
            skipOnboardingIfShown()
            waitForDescription("Open file")
            compose.onNodeWithContentDescription("More options").performClick()
            compose.onNodeWithText("Merge PDFs", substring = true).performClick()
            waitForText("Sheaf Pro")
            compose.onNodeWithText("Merge PDFs", substring = true).assertExists()
            compose.onNodeWithText("Already bought? Restore").assertExists()
            compose.onNodeWithText("Not now").performClick()
            compose.waitUntilDoesNotExist(hasText("Already bought? Restore"), timeout)
        }
    }

    @Test
    fun fileProviderSharesOnlySheafOutputFolders() {
        val authority = context.packageName + ".fileprovider"
        // Library copies and share outputs can be handed to other apps...
        for (path in listOf("opened/a.pdf", "scanned/b.pdf", "edited/c.pdf", "searchable/d.pdf")) {
            val uri = FileProvider.getUriForFile(context, authority, File(context.filesDir, path))
            assertEquals("content", uri.scheme)
        }
        for (path in listOf("share/e.pdf", "compressed/f.pdf", "signed/g.pdf")) {
            FileProvider.getUriForFile(context, authority, File(context.cacheDir, path))
        }
        // ...but never settings, the library database or decrypted copies.
        val private = listOf(
            File(context.filesDir, "datastore/settings.preferences_pb"),
            context.getDatabasePath("sheaf.db"),
            File(context.cacheDir, "unlocked/x.pdf"),
        )
        for (file in private) {
            try {
                FileProvider.getUriForFile(context, authority, file)
                fail("FileProvider must not expose $file")
            } catch (expected: IllegalArgumentException) {
                // ok
            }
        }
    }

    @Test
    fun twoFingerDragInPenModeScrollsInsteadOfDrawing() {
        val pdf = TestPdfs.plain(context, "e2e-twofinger.pdf", pages = 3)
        launchWith(pdf).use {
            waitForDescription("Page 1 of 3")
            compose.onNodeWithContentDescription("Annotate").performClick()
            waitForDescription("Undo")
            compose.onNodeWithContentDescription("Page 1 of 3").performTouchInput {
                down(0, Offset(width * 0.4f, height * 0.8f))
                down(1, Offset(width * 0.6f, height * 0.8f))
                repeat(20) {
                    moveBy(0, Offset(0f, -height * 0.08f))
                    moveBy(1, Offset(0f, -height * 0.08f))
                }
                up(0)
                up(1)
            }
            compose.waitForIdle()
            // No stroke was recorded: two fingers are for scrolling/zooming, not ink...
            compose.onNodeWithContentDescription("Undo").assertIsNotEnabled()
            // ...and the document actually scrolled past page 1.
            compose.waitUntil(timeout) {
                compose.onAllNodesWithText("1 / 3").fetchSemanticsNodes().isEmpty()
            }
        }
    }
}
