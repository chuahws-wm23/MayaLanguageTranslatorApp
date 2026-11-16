package com.chuahws.mayalanguageapp.integration.glyph

/**
 * Source-linked context for selected higher-evidence English logograms beyond
 * v4's original ten classes. These entries are for STUDY and visual reference
 * browsing, not evidence of a trained recognition result.
 */
internal object GlyphMoreLearningNotes {
    private const val TWKM="https://classicmayan.org/portal/signcatalog"
    private val entries=listOf(
        GlyphLearningNote(
            168,"AJAW","Lord / ruler","Title or person",
            "AJAW is a title or noun meaning lord or ruler. The English gloss describes a social position, not a person's own name.",
            "Classic Maya inscriptions record rulers and their titles. An emblem glyph may combine a divine or holy title with the word lord.",
            "A name or title is usually written with additional signs. Do not identify a specific ruler from AJAW alone.",
            "https://maya.nmai.si.edu/resources",
        ),
        GlyphLearningNote(
            16,"YAX","First / fresh / green","Quality or descriptive word",
            "YAX has several source-proposed senses including first, fresh and green. These are alternative English senses, not a complete phrase.",
            "Ancient Maya texts use both words with meaning and syllabic signs to build longer expressions. The exact sense depends on the rest of the inscription.",
            "Several possible glosses mean that context matters. Avoid automatically selecting the first English word.",
            TWKM,
        ),
        GlyphLearningNote(
            45,"HUL","To arrive here","Action or verb",
            "HUL represents an arrival-related action, shown in the source catalogue as to arrive here.",
            "Maya inscriptions can record events, journeys and actions. Verbal signs interact with other signs that identify people and places.",
            "A verb does not identify who arrived, where or when without the surrounding inscription.",
            TWKM,
        ),
        GlyphLearningNote(
            109,"CHAK","Red","Colour or descriptive word",
            "CHAK is recorded with the English colour gloss red.",
            "Descriptive words can contribute to names, titles or descriptions. Their role depends on the grammatical expression.",
            "CHAK (red) should not automatically be confused with similarly written Maya names or the rain deity Chaahk.",
            TWKM,
        ),
        GlyphLearningNote(
            115,"YOP","Leaf","Plant or nature word",
            "YOP refers to a leaf, a plant part. It is a logographic word meaning, unlike the syllabic sounds ba or ku.",
            "Maya writing includes signs for plants, animals and objects as well as names, numbers and dates.",
            "An image resembling a leaf does not necessarily mean the entire glyph block is literally the English word leaf.",
            TWKM,
        ),
        GlyphLearningNote(
            173,"MIH","No / nothing / zero","Quantity or negation",
            "MIH is associated in TWKM with no, nothing and zero. The exact use depends on context.",
            "Maya numerical and calendrical systems incorporated the concept of zero. The writing system also encoded ordinary language about quantity and absence.",
            "Not every appearance of a zero-like concept is a mathematical numeral; a phrase must be read as a whole.",
            "https://maya.nmai.si.edu/calendar/calendar-system",
        ),
        GlyphLearningNote(
            185,"NAAB","Body of water / pool / sea / waterlily","Nature or water",
            "NAAB has several source-linked English senses associated with water, including a body of water, pool or sea, and waterlily.",
            "Natural landscapes and aquatic imagery feature in ancient Maya vocabulary and iconography.",
            "Choose a particular English sense only after checking the inscription's other signs and archaeological context.",
            TWKM,
        ),
        GlyphLearningNote(
            87,"TE'","Forest / tree / wood / staff / stick","Plant or material",
            "TE' has source-proposed English meanings associated with trees, wood or wooden objects.",
            "Words for materials and natural features can occur in names and descriptions across Maya texts.",
            "A single source glossary lists several related senses. They are not all meant simultaneously in one phrase.",
            TWKM,
        ),
        GlyphLearningNote(
            86,"NAL","Ear of corn / cultivated place","Plant or place-related word",
            "NAL is listed with senses that include an ear of maize and a place associated with cultivated crops.",
            "Maize has long been central to Maya farming, foodways and cultural traditions, including the calendar of agricultural activities.",
            "A crop-related sign may occur in a place name or other expression. It does not always describe farming literally.",
            "https://maya.nmai.si.edu/corn-and-maya-time",
        ),
        GlyphLearningNote(
            193,"EEB","Ladder / stair / step","Object or architecture",
            "EEB has proposed English meanings related to steps, stairs or ladders.",
            "Maya monuments and texts refer to constructed places and material objects, but the precise reading depends on other signs.",
            "A word for a step does not prove the photographed object is a specific monument or staircase.",
            TWKM,
        ),
    )
    private val bySign=entries.associateBy { it.twkmSign }
    fun forSign(signNumber:Int):GlyphLearningNote?=bySign[signNumber]
    fun all():List<GlyphLearningNote> = entries.toList()
}
