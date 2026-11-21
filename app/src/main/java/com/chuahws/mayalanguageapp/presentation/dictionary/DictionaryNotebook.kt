package com.chuahws.mayalanguageapp.presentation.dictionary

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class ResearchNote(val text: String = "", val source: String = "")

internal data class DictionaryNotebook(
    val notes: Map<String, ResearchNote> = emptyMap(),
    val sets: Map<String, List<String>> = emptyMap(),
    val searches: List<String> = emptyList(),
)

internal class DictionaryNotebookStore(context: Context, private val accountId: String) {
    private val preferences = context.getSharedPreferences("glyph_dictionary_notebook", Context.MODE_PRIVATE)

    fun load(): DictionaryNotebook = runCatching {
        val root = JSONObject(preferences.getString(accountId, "{}") ?: "{}")
        val notes = root.optJSONObject("notes") ?: JSONObject()
        val sets = root.optJSONObject("sets") ?: JSONObject()
        DictionaryNotebook(
            notes = notes.keys().asSequence().associateWith { key ->
                val item = notes.getJSONObject(key)
                ResearchNote(item.optString("text"), item.optString("source"))
            },
            sets = sets.keys().asSequence().associateWith { sets.getJSONArray(it).strings() },
            searches = root.optJSONArray("searches")?.strings().orEmpty(),
        )
    }.getOrDefault(DictionaryNotebook())

    fun save(notebook: DictionaryNotebook) {
        val notes = JSONObject()
        notebook.notes.forEach { (key, note) ->
            notes.put(key, JSONObject().put("text", note.text).put("source", note.source))
        }
        val sets = JSONObject()
        notebook.sets.forEach { (name, entries) -> sets.put(name, JSONArray(entries)) }
        val root = JSONObject().put("notes", notes).put("sets", sets)
            .put("searches", JSONArray(notebook.searches))
        preferences.edit().putString(accountId, root.toString()).apply()
    }

    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
}
