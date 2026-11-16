package com.chuahws.mayalanguageapp.integration.glyph

/**
 * SHORT educational notes for the 10 source-identified v4 sign categories.
 *
 * Source meaning: existing TWKM-linked Research v4 sign mapping.
 * Context: ClassicMayan TWKM sign catalog and Smithsonian Living Maya Time;
 * K'awiil context: Helmke & Vepretskii (2022), Mesoweb.
 *
 * These lessons describe meaning candidates, NOT verified photo
 * identifications, sentence translation or new neural model labels.
 */
internal data class GlyphLearningNote(
    val twkmSign: Int,
    val reading: String,
    val english: String,
    val category: String,
    val explanation: String,
    val historicalContext: String,
    val remember: String,
    val sourceUrl: String,
)

internal object GlyphLearningNotes {
    private const val TWKM = "https://classicmayan.org/portal/signcatalog"
    private val notes = listOf(
        GlyphLearningNote(
            32,"K'UH","God / deity","Religious word",
            "K'UH refers to a god, deity or divine being. It expresses a religious idea rather than the name of one particular god.",
            "In Classic Maya texts, divine language can appear with titles and names, including expressions about sacred rulership.",
            "A drawing that resembles K'UH is not enough to confirm its reading; check the surrounding signs.",
            TWKM,
        ),
        GlyphLearningNote(
            184,"K'INICH","Resplendent / sunny","Epithet or name element",
            "K'INICH conveys a radiant or sun-related quality. It can function as part of a title or personal name, not just a description of the weather.",
            "The element occurs in names and titles of some Classic Maya rulers, where it carries associations with solar brilliance.",
            "Do not translate an entire royal name into English by replacing only this one element.",
            "https://www.mesoweb.com/pari/publications/journal/2204/Helmke-Vepretskii.2022.pdf",
        ),
        GlyphLearningNote(
            291,"SIIP","Name of a deity","Divine name",
            "SIIP is a proper divine name in the supported catalogue label. It is not a common English noun.",
            "Names of supernatural beings can occur in inscriptions, art and compound names. A deity's name is normally retained when explaining a text.",
            "The English result is 'name of a deity', not a literal English translation of the name SIIP.",
            TWKM,
        ),
        GlyphLearningNote(
            4,"NAAH","House / first","Context-dependent word",
            "The existing TWKM-linked catalogue model lists house and first as possible English senses associated with NAAH.",
            "Signs must be interpreted within a larger written expression. The intended English sense can differ across linguistic contexts.",
            "Treat these as alternative proposed senses, not proof that the same image always means both.",
            TWKM,
        ),
        GlyphLearningNote(
            44,"TOK","Cloud / mist","Nature word",
            "The app's source-linked gloss for TOK is cloud or mist, terms referring to atmospheric conditions.",
            "Natural imagery and weather-related vocabulary can appear as elements in Maya writing, including compound expressions.",
            "A visual match does not prove the entire glyph block describes the weather.",
            TWKM,
        ),
        GlyphLearningNote(
            507,"TZIMAH","Gourd","Plant or object",
            "The app's TWKM-linked gloss is gourd, a fruit or vessel-forming plant. This is a word meaning rather than a syllable like ba or ku.",
            "Words in Classic Maya inscriptions can represent everyday objects and living things as well as names, titles and dates.",
            "The English meaning is a proposed lexical gloss, not proof of an object's function in a particular inscription.",
            TWKM,
        ),
        GlyphLearningNote(
            548,"HAAB","Year","Calendar or time",
            "HAAB means year. The Haab is also the name of a traditional 365-day Maya calendar cycle.",
            "The Haab calendar has eighteen 20-day months and one additional five-day period, together making 365 days.",
            "The appearance of HAAB can indicate time-related language. Other signs determine the complete date or phrase.",
            "https://maya.nmai.si.edu/calendar/calendar-system",
        ),
        GlyphLearningNote(
            122,"K'AK'","Fire","Natural element",
            "K'AK' is associated with fire. An English word gloss helps identify the vocabulary, but not necessarily an entire sentence.",
            "Fire-related words and imagery can occur in Maya names, ceremonies and historical accounts.",
            "Look for neighboring signs before interpreting a glyph block as a specific event or ritual.",
            TWKM,
        ),
        GlyphLearningNote(
            187,"K'ABA'","Name","Word about identity",
            "K'ABA' refers to a name. In a text, the word may be part of a larger statement that introduces or mentions someone's identity.",
            "Classic Maya inscriptions frequently record rulers and other named individuals, often together with titles.",
            "Do not confuse the word 'name' with the person's actual name, which requires other glyphs.",
            TWKM,
        ),
        GlyphLearningNote(
            1030,"K'AWIIL","Name of a deity","Divine name",
            "K'AWIIL names a major supernatural figure in Classic Maya belief, rather than an everyday English word.",
            "Scholarly studies connect K'awiil with lightning, storms and the symbolism of royal authority.",
            "Preserve K'AWIIL as a proper name. Associations with lightning do not mean every occurrence translates as 'lightning'.",
            "https://www.mesoweb.com/pari/publications/journal/2204/Helmke-Vepretskii.2022.pdf",
        ),
    )
    private val byId = notes.associateBy { it.twkmSign }

    fun forSign(twkmSign: Int): GlyphLearningNote? = byId[twkmSign]
    fun all(): List<GlyphLearningNote> = notes.toList()
}
