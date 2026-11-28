package com.chuahws.mayalanguageapp.presentation.research

import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.chuahws.mayalanguageapp.presentation.dictionary.DictionaryNotebook
import com.chuahws.mayalanguageapp.presentation.dictionary.DictionaryNotebookStore
import com.chuahws.mayalanguageapp.presentation.dictionary.GlyphDictionaryHubScreen
import com.chuahws.mayalanguageapp.presentation.dictionary.ResearchNote
import com.chuahws.mayalanguageapp.presentation.theme.MayaTranslateTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class ResearchWorkflowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun notesPageReadsAndEditsTheExistingDictionaryNotebook() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val account = UUID.randomUUID().toString()
        val store = DictionaryNotebookStore(context, account)
        val original = DictionaryNotebook(
            notes = mapOf("word-122" to ResearchNote("Compare the central panel", "Catalogue source")),
            sets = mapOf("Fire signs" to listOf("word-122")),
        )
        store.save(original)
        val session = mutableStateOf(0)
        try {
            compose.setContent {
                MayaTranslateTheme {
                    key(session.value) { GlyphDictionaryHubScreen(accountId = account, notesOnly = true) }
                }
            }
            compose.waitUntil(20_000) {
                compose.onAllNodesWithText("Compare the central panel").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Search notes").performTextInput("unmatched")
            compose.onNodeWithText("No matching notes.").assertExists()
            compose.onNodeWithText("Search notes").performTextClearance()
            compose.onNodeWithText("Edit").performClick()
            compose.onNodeWithText("Your note").performTextReplacement("Updated fire note")
            compose.onNodeWithText("Save note").performClick()
            compose.onNodeWithText("Updated fire note").assertExists()
            assertEquals(original.sets, store.load().sets)

            compose.runOnIdle { session.value++ }
            compose.waitUntil(20_000) {
                compose.onAllNodesWithText("Updated fire note").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Catalogue source").assertExists()
        } finally {
            store.save(DictionaryNotebook())
        }
    }
}
