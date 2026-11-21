# Research wiki and glyph workspace

Open the navigation drawer → **Research wiki** or **Glyph workspace**. These extend the existing scanner, dictionary, History and Activity; they do not reinstate the old Learn Maya quizzes.

The offline wiki has ten searchable, expandable articles: writing systems, block order, syllables, phonetic complements, variants, source attribution, the ka-ka-wa spelling example, transliteration/transcription, numbers/calendar context and an inscription research checklist. Further-reading links lead to Kettunen and Helmke's *Introduction to Maya Hieroglyphs* at Mesoweb and the TWKM website. The articles are introductory editorial summaries, not quoted source text or expert-reviewed decipherments. External sources require internet access.

The workspace displays the existing TWKM CC BY 4.0 syllable drawings and source-proposed syllable values. Search a syllable and drag a drawing into one of six numbered cells, or select a cell and tap a drawing. A floating drawing follows the drag, and a hovered cell highlights. Dropping replaces that cell and selects the next one. Remove selected clears one cell; Clear block clears all six. The board remains visible while browsing the palette. The current sign sequence appears in Latin letters, separated by hyphens, in explicit cell order 1–6. Empty cells are skipped.

A study block and wiki search survive navigation between sections and configuration recreation within the current signed-in shell. They are not a cloud-saved research notebook or an exported inscription, and are cleared when the signed-in shell ends.

This first workspace assembles **syllable reference drawings**, not arbitrary fragments extracted from photographs. It provides a sign-by-sign transliteration, not a linguistic transcription, automatic pronunciation, semantic translation or validation that a composition is attested Maya. A grid does not establish historical reading order, identify complements or resolve spelling conventions. The documented ka-ka-wa example helps explain why written values cannot simply be concatenated into spoken words. Word-sign/phonetic-complement interpretation requires further linguistic review.

Validation: `testDebugUnitTest assembleDebug assembleDebugAndroidTest` passes locally (180 JVM tests). ResearchWorkflowTest adds Android tests for article search/expansion, an actual drag into cell 1, a tap into the next cell, the combined ka-wa sequence and clearing. Check Android checks for the actual runtime outcome; compiling instrumentation tests alone is not a runtime pass.
