package com.chuahws.mayalanguageapp.presentation.research

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import com.chuahws.mayalanguageapp.presentation.theme.MayaTranslateTheme
import org.junit.Rule
import org.junit.Test

class ResearchWorkflowTest {
    @get:Rule val compose = createComposeRule()
    private fun find(value: String) {
        compose.onNodeWithTag("syllable-palette").performScrollToNode(hasText("Find a syllable"))
        compose.onNodeWithText("Find a syllable").performTextReplacement(value)
        compose.onNodeWithTag("syllable-palette").performScrollToNode(hasContentDescription("Syllable card $value"))
    }
    private fun shot(name: String) {
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val folder=requireNotNull(context.getExternalFilesDir("screenshots")).apply { mkdirs() }
        val image=compose.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(folder,"two-syllables-$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun dragTwoCardsMoveOneAndRevealReadingOnlyAfterNext() {
        compose.setContent { MayaTranslateTheme { GlyphWorkspaceScreen() } }
        compose.onNodeWithText("Next").assertIsNotEnabled()
        find("ka")
        val board=compose.onNodeWithTag("syllable-canvas").fetchSemanticsNode().boundsInRoot
        val first=compose.onNodeWithContentDescription("Syllable card ka")
        val origin=first.fetchSemanticsNode().boundsInRoot.topLeft
        val left=Offset(board.left+board.width*.25f,board.center.y)
        first.performTouchInput { down(center);advanceEventTime(700);moveTo(left-origin,delayMillis=500);up() }
        compose.onNodeWithText("Next").assertIsNotEnabled()
        find("wa")
        compose.onNodeWithContentDescription("Syllable card wa").performClick()
        compose.onNodeWithText("ka-wa").assertDoesNotExist()
        compose.onNodeWithText("Next").assertIsEnabled()
        val placed=compose.onNodeWithTag("placed-0")
        val placedOrigin=placed.fetchSemanticsNode().boundsInRoot.topLeft
        val right=Offset(board.left+board.width*.84f,board.center.y)
        placed.performTouchInput { down(center);advanceEventTime(700);moveTo(right-placedOrigin,delayMillis=500);up() }
        shot("canvas")
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("wa-ka").assertExists()
        compose.onNodeWithText("Your combination").assertExists()
        shot("result")
        compose.onNodeWithText("Edit combination").performClick()
        compose.onNodeWithTag("placed-0").assertExists()
        compose.onNodeWithText("wa-ka").assertDoesNotExist()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Start again").performClick()
        compose.onNodeWithText("Next").assertIsNotEnabled()
        compose.onNodeWithText("Drop two syllable cards here").assertExists()
    }
    @Test fun clearRequiresTwoNewPartsAndSearchHasEmptyState() {
        compose.setContent { MayaTranslateTheme { GlyphWorkspaceScreen() } }
        find("ka")
        compose.onNodeWithContentDescription("Syllable card ka").performClick()
        compose.onNodeWithText("Clear canvas").performClick()
        compose.onNodeWithText("Next").assertIsNotEnabled()
        compose.onNodeWithTag("syllable-palette").performScrollToNode(hasText("Find a syllable"))
        compose.onNodeWithText("Find a syllable").performTextReplacement("zzzzzz")
        compose.onNodeWithTag("syllable-palette").performScrollToNode(hasText("No syllables found"))
        compose.onNodeWithText("No syllables found").assertIsDisplayed()
    }
}
