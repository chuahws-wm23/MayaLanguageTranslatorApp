package com.chuahws.mayalanguageapp.data.repository

import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.service.TextNormalizer

/**
 * Small source-checked dictionary used while the full Firestore dictionary
 * is being prepared. Core greetings remain available after Firebase is
 * connected through CompositeDictionaryRepository.
 */
object DevelopmentDictionaryData {

    fun entries(): List<DictionaryEntry> = buildList {
        addWithAliases(
            id = "core-hello",
            maya = "Baʼax ka waʼalik?",
            english = "hello",
            partOfSpeech = "greeting",
            pronunciation = "ba-ash ka wa-a-lik",
            englishAliases = listOf("hi", "hey", "what's up", "whats up"),
        )
        addEntry("core-welcome", "Kíimak óolal", "welcome", "greeting")
        addEntry("core-how-are-you", "Bix a beel?", "how are you", "greeting")
        addEntry("core-thank-you", "Dios boʼotik", "thank you", "expression")
        addEntry("core-please", "Meentʼ uts", "please", "expression")
        addEntry("core-goodbye", "Ka kaʼat", "goodbye", "greeting")
        addWithAliases(
            id = "core-yes",
            maya = "Jéʼel",
            english = "yes",
            partOfSpeech = "response",
            englishAliases = listOf("yeah", "yep"),
        )
        addEntry("core-no", "Maʼ", "no", "response")

        addEntry("dev-ja", "jaʼ", "water", "noun", "[ˈhaʔ]")
        addEntry("dev-naaj", "naaj", "house", "noun")
        addEntry("dev-che", "cheʼ", "tree", "noun")
        addEntry("dev-uj", "uj", "moon", "noun")
        addEntry("dev-yaax", "yaʼax", "green", "adjective")
        addEntry("dev-aak", "áak", "turtle", "noun")
        addEntry("dev-puut", "puut", "papaya", "noun")
        addEntry("dev-maak", "máak", "person", "noun")
        addEntry("dev-muuyal", "múuyal", "cloud", "noun")
        addEntry("dev-tuunich", "tuunich", "stone", "noun")
        addEntry("dev-kaax", "kʼáax", "forest", "noun")
        addEntry("dev-juun", "juʼun", "book", "noun")
        addEntry("dev-beet", "beet", "make", "verb")
        addEntry("dev-naat", "naʼat", "understand", "verb")
        addEntry("dev-yeetel", "yéetel", "with", "conjunction")
    }

    private fun MutableList<DictionaryEntry>.addEntry(
        id: String,
        maya: String,
        english: String,
        partOfSpeech: String,
        pronunciation: String? = null,
    ) {
        add(
            createEntry(
                id = id,
                maya = maya,
                english = english,
                englishSearchText = english,
                partOfSpeech = partOfSpeech,
                pronunciation = pronunciation,
            )
        )
    }

    private fun MutableList<DictionaryEntry>.addWithAliases(
        id: String,
        maya: String,
        english: String,
        partOfSpeech: String,
        pronunciation: String? = null,
        englishAliases: List<String>,
    ) {
        addEntry(id, maya, english, partOfSpeech, pronunciation)

        englishAliases.forEach { alias ->
            add(
                createEntry(
                    id = id,
                    maya = maya,
                    english = english,
                    englishSearchText = alias,
                    partOfSpeech = partOfSpeech,
                    pronunciation = pronunciation,
                )
            )
        }
    }

    private fun createEntry(
        id: String,
        maya: String,
        english: String,
        englishSearchText: String,
        partOfSpeech: String,
        pronunciation: String?,
    ) = DictionaryEntry(
        entryId = id,
        mayaText = maya,
        mayaNormalized = TextNormalizer.normalize(maya),
        englishText = english,
        englishNormalized = TextNormalizer.normalize(englishSearchText),
        partOfSpeech = partOfSpeech,
        pronunciation = pronunciation,
        source = "Na'atik Language & Culture Institute; Kaikki/Wiktionary",
        verified = true,
        active = true,
    )
}
