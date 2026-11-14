package com.chuahws.mayalanguageapp.data.repository

import com.chuahws.mayalanguageapp.domain.model.DictionaryEntry
import com.chuahws.mayalanguageapp.domain.service.TextNormalizer

object DevelopmentDictionaryData {

    fun entries(): List<DictionaryEntry> = buildList {
        addPhraseWithAliases(
            id = "core-hello",
            maya = "Baʼax ka waʼalik?",
            english = "hello",
            partOfSpeech = "greeting",
            usageLabel = "General greeting",
            pronunciation = "Approx. ba-ash ka wa-a-lik",
            englishAliases = listOf("hi", "hey"),
        )

        addPhrase(
            "core-welcome",
            "Kíimak óolal",
            "welcome",
            "greeting",
            "Welcome greeting",
        )
        addPhrase(
            "core-how-are-you",
            "Bix a bel?",
            "how are you",
            "greeting",
            "Asking how someone is",
        )
        addPhrase(
            "core-thank-you",
            "Dios boʼotik",
            "thank you",
            "expression",
            "Expression of thanks",
        )
        addPhrase(
            "core-please",
            "Meentʼ uts",
            "please",
            "expression",
            "Polite request",
        )
        addPhrase(
            "core-goodbye",
            "Ka kaʼat",
            "goodbye",
            "greeting",
            "Parting phrase",
        )

        addLexical("dev-ja", "jaʼ", "water", "noun", "[ˈhaʔ]")
        addLexical("dev-naaj", "naaj", "house", "noun")
        addLexical("dev-che", "cheʼ", "tree", "noun")
        addLexical("dev-uj", "uj", "moon", "noun")
        addLexical("dev-yaax", "yaʼax", "green", "adjective")
        addLexical("dev-aak", "áak", "turtle", "noun")
        addLexical("dev-puut", "puut", "papaya", "noun")
        addLexical("dev-maak", "máak", "person", "noun")
        addLexical("dev-muuyal", "múuyal", "cloud", "noun")
        addLexical("dev-tuunich", "tuunich", "stone", "noun")
        addLexical("dev-kaax", "kʼáax", "forest", "noun")
        addLexical("dev-juun", "juʼun", "book", "noun")
        addLexical("dev-beet", "beet", "make", "verb")
        addLexical("dev-naat", "naʼat", "understand", "verb")
        addLexical("dev-yeetel", "yéetel", "with", "conjunction")
    }

    private fun MutableList<DictionaryEntry>.addPhrase(
        id: String,
        maya: String,
        english: String,
        partOfSpeech: String,
        usageLabel: String,
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
                source = "Na'atik Language & Culture Institute",
                usageLabel = usageLabel,
                acceptedEnglishInputs = listOf(english),
            )
        )
    }

    private fun MutableList<DictionaryEntry>.addPhraseWithAliases(
        id: String,
        maya: String,
        english: String,
        partOfSpeech: String,
        usageLabel: String,
        pronunciation: String? = null,
        englishAliases: List<String>,
    ) {
        val acceptedInputs = listOf(english) + englishAliases

        acceptedInputs.forEach { input ->
            add(
                createEntry(
                    id = id,
                    maya = maya,
                    english = english,
                    englishSearchText = input,
                    partOfSpeech = partOfSpeech,
                    pronunciation = pronunciation,
                    source = "Na'atik Language & Culture Institute",
                    usageLabel = usageLabel,
                    acceptedEnglishInputs = acceptedInputs,
                )
            )
        }
    }

    private fun MutableList<DictionaryEntry>.addLexical(
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
                source = "Kaikki / English Wiktionary",
                usageLabel = null,
                acceptedEnglishInputs = emptyList(),
            )
        )
    }

    private fun createEntry(
        id: String,
        maya: String,
        english: String,
        englishSearchText: String,
        partOfSpeech: String,
        pronunciation: String?,
        source: String,
        usageLabel: String?,
        acceptedEnglishInputs: List<String>,
    ) = DictionaryEntry(
        entryId = id,
        mayaText = maya,
        mayaNormalized = TextNormalizer.normalize(maya),
        englishText = english,
        englishNormalized = TextNormalizer.normalize(englishSearchText),
        partOfSpeech = partOfSpeech,
        pronunciation = pronunciation,
        source = source,
        usageLabel = usageLabel,
        acceptedEnglishInputs = acceptedEnglishInputs,
        verified = true,
        active = true,
    )
}
