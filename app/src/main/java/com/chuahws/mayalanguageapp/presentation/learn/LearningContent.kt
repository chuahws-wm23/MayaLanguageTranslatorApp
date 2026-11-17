package com.chuahws.mayalanguageapp.presentation.learn

/** Introductory content, separate from unverified scanner predictions. */
data class LearnLesson(val id: String, val title: String, val introduction: String, val sections: List<Pair<String, String>>)
data class LearnQuestion(val prompt: String, val choices: List<String>, val answer: Int, val explanation: String)

object LearningContent {
    val lessons = listOf(
        LearnLesson("signs", "Words and syllables", "Discover the two building blocks of Classic Maya writing.", listOf(
            "A writing system for language" to "Classic Maya inscriptions record language, including names, titles, events and dates. A sign is not simply a picture with one universal meaning.",
            "Word signs" to "Logograms represent words or word roots. Scholarly transcriptions conventionally write logograms in CAPITALS. One word can have different visual sign forms.",
            "Syllabic signs" to "Syllabic signs usually represent a consonant followed by a vowel, such as ka or ma. Scholars conventionally write syllabic readings in lowercase. These examples are sound values, not identifications of an uploaded image.",
            "Working together" to "A word sign can appear with syllabic signs that help indicate its reading. These phonetic complements are clues; they do not always add a separate word. Interpretation needs context."
        )),
        LearnLesson("blocks", "Inside a glyph block", "Learn why one block can contain several signs.", listOf(
            "Signs and blocks" to "A glyph block is a visual grouping that may contain a main sign and smaller attached signs. A scanner's region box does not prove that every sign inside it has been identified.",
            "Position matters" to "Smaller signs can appear above, below, beside or inside another sign. Their placement and the language help establish how to read the block. There is no single rule that covers every arrangement.",
            "Recognise variation" to "The same reading may have multiple graphic forms, called allographs. Carved, painted and handwritten forms can differ considerably.",
            "Compare carefully" to "Compare internal details and attached signs, not just the outer silhouette. A nearest visual reference is a candidate, not a confirmed translation."
        )),
        LearnLesson("order", "Following an inscription", "Explore the usual reading order and its limits.", listOf(
            "Paired columns" to "Many monumental inscriptions use paired columns. The usual sequence is across the first pair of blocks, then down to the next row: A1, B1, A2, B2.",
            "Continue with the next pair" to "After completing columns A and B, continue with C and D. A single row, circular layout or unusual monument may follow a different arrangement.",
            "Read in context" to "Dates, names and grammatical patterns help researchers interpret sequences. Recognising isolated signs is only one step toward reading an inscription."
        )),
        LearnLesson("numbers", "Dots, bars and zero", "Build numbers and discover base twenty.", listOf(
            "Three basic elements" to "A dot represents one, a bar represents five, and a shell-like sign is a common form for zero. Number signs also have other historical forms.",
            "A place for each value" to "In a simple base-twenty numeral, the bottom level counts ones, the level above counts twenties, and the next counts four hundreds. Read the total by adding each level's contribution.",
            "A calendar exception" to "Long Count calendar notation uses a modified place-value system: after days and groups of twenty days, the next unit is 360 days (18 × 20), not 400. The playground here demonstrates ordinary base twenty, not a Long Count date."
        ))
    )
    val questions = listOf(
        LearnQuestion("What does a syllabic sign usually represent?", listOf("A consonant–vowel syllable", "An entire inscription", "Only a number"), 0, "Many syllabic signs represent a consonant followed by a vowel. They can combine with other signs to spell words."),
        LearnQuestion("What is a phonetic complement useful for?", listOf("Decorating a border", "Helping indicate a word sign's reading", "Guaranteeing a scanner match"), 1, "A phonetic complement supplies a sound clue to the reading of a word sign."),
        LearnQuestion("Can one glyph block contain several signs?", listOf("Yes", "No, always exactly one", "Only in modern writing"), 0, "A block can group a main sign with smaller attached signs."),
        LearnQuestion("What is a common paired-column reading sequence?", listOf("A1, A2, A3, B1", "B2, B1, A2, A1", "A1, B1, A2, B2"), 2, "Many inscriptions read across a column pair, then down to its next row. Other layouts can differ."),
        LearnQuestion("Two bars and three dots represent…", listOf("8", "13", "23"), 1, "Each bar is five: 5 + 5 + 3 = 13."),
        LearnQuestion("In ordinary base twenty, one dot above a zero level represents…", listOf("10", "100", "20"), 2, "The upper dot contributes one twenty; the lower zero contributes no ones."),
        LearnQuestion("Does a nearest visual match confirm a translation?", listOf("Always", "No; it needs verification and context", "Only if the outline is similar"), 1, "Visual resemblance alone does not establish a sign's identity or the meaning of an inscription."),
        LearnQuestion("Which system uses a 360-day unit after groups of twenty days?", listOf("The Long Count calendar", "Every base-twenty number", "The Latin alphabet"), 0, "Long Count calendar notation has a modified place-value sequence. Ordinary base twenty uses 400 at that level.")
    )
}

data class MayaNumberLevel(val digit: Int, val place: Int) {
    val dots get() = digit % 5
    val bars get() = digit / 5
}
fun mayaNumberLevels(value: Int): List<MayaNumberLevel> {
    require(value in 0..399) { "The playground supports 0–399" }
    return if (value < 20) listOf(MayaNumberLevel(value, 1))
        else listOf(MayaNumberLevel(value / 20, 20), MayaNumberLevel(value % 20, 1))
}
