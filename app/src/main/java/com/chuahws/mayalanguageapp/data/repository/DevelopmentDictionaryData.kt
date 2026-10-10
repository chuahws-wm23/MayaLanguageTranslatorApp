package com.chuahws.mayalanguageapp.data.repository

import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.service.TextNormalizer

/**
 * Small source-checked dictionary used only before the full Firestore
 * dictionary is imported. Entries were checked against the Yucatec Maya
 * pages published by Kaikki/Wiktionary.
 */
object DevelopmentDictionaryData {
    fun entries(): List<DictionaryEntry> = listOf(
        entry("dev-ja", "jaʼ", "water", "noun", "[ˈhaʔ]"),
        entry("dev-naaj", "naaj", "house", "noun"),
        entry("dev-che", "cheʼ", "tree", "noun"),
        entry("dev-uj", "uj", "moon", "noun"),
        entry("dev-yaax", "yaʼax", "green", "adjective"),
        entry("dev-aak", "áak", "turtle", "noun"),
        entry("dev-puut", "puut", "papaya", "noun"),
        entry("dev-maak", "máak", "person", "noun"),
        entry("dev-muuyal", "múuyal", "cloud", "noun"),
        entry("dev-tuunich", "tuunich", "stone", "noun"),
        entry("dev-kaax", "kʼáax", "forest", "noun"),
        entry("dev-juun", "juʼun", "book", "noun"),
        entry("dev-beet", "beet", "make", "verb"),
        entry("dev-naat", "naʼat", "understand", "verb"),
        entry("dev-yeetel", "yéetel", "with", "conjunction"),
    )

    private fun entry(
        id: String,
        maya: String,
        english: String,
        partOfSpeech: String,
        pronunciation: String? = null,
    ) = DictionaryEntry(
        entryId = id,
        mayaText = maya,
        mayaNormalized = TextNormalizer.normalize(maya),
        englishText = english,
        englishNormalized = TextNormalizer.normalize(english),
        partOfSpeech = partOfSpeech,
        pronunciation = pronunciation,
        source = "Kaikki / English Wiktionary",
        verified = true,
        active = true,
    )
}
