package com.chuahws.mayalanguageapp.presentation.research

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.chuahws.mayalanguageapp.presentation.theme.MayaTranslateTheme
import org.junit.Rule
import org.junit.Test

class ResearchWorkflowTest {
    @get:Rule val compose = createComposeRule()
    @Test fun searchAndExpandResearchArticle() {
        compose.setContent { MayaTranslateTheme { ResearchScreen() } }
        compose.onNodeWithText("Search topics or terms").performTextReplacement("complements")
        compose.onNodeWithText("Word signs and phonetic complements").performScrollTo().performClick()
        compose.onNodeWithText("Read the source ↗").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Search topics or terms").performTextReplacement("zzzzzz")
        compose.onNodeWithText("No topics found. Try syllables, reading or variants.").assertExists()
    }
    @Test fun assembleTwoSignsAndClearTheirLatinReading() {
        compose.setContent { MayaTranslateTheme { ResearchScreen(builder = true) } }
        compose.onNodeWithTag("assembly-list").performScrollToNode(hasText("Find a syllable, e.g. ka"))
        compose.onNodeWithText("Find a syllable, e.g. ka").performTextReplacement("ka")
        compose.onNodeWithTag("assembly-list").performScrollToNode(hasContentDescription("Drag syllable ka"))
        val tile = compose.onNodeWithContentDescription("Drag syllable ka")
        val target = compose.onNodeWithText("1 · Empty").fetchSemanticsNode().boundsInRoot.center
        val source = tile.fetchSemanticsNode().boundsInRoot.topLeft
        tile.performTouchInput { down(center); advanceEventTime(700); moveTo(target - source, delayMillis = 500); up() }
        compose.onNodeWithTag("assembly-list").performScrollToNode(hasText("Find a syllable, e.g. ka"))
        compose.onNodeWithText("Find a syllable, e.g. ka").performTextReplacement("wa")
        compose.onNodeWithTag("assembly-list").performScrollToNode(hasContentDescription("Drag syllable wa"))
        compose.onNodeWithContentDescription("Drag syllable wa").performClick()
        compose.onAllNodesWithText("ka-wa").onFirst().assertExists()
        compose.onNodeWithText("Clear block").performClick()
        compose.onNodeWithText("Your reading appears here").assertExists()
    }
}
