# Two-syllable workspace

The Research wiki has been removed, including its drawer entry and bundled articles. Open the menu → **Glyph workspace**.

1. Find a syllable in the catalogue. Hold a card, then drag it onto the empty canvas. Tapping a card also adds it at a default position.
2. Add one more syllable. The canvas accepts exactly two cards; the palette disables additions when both are present.
3. Hold and drag either placed card to arrange them freely. An out-of-canvas drop does not change the composition. Clear canvas removes both cards.
4. Tap **Next**, enabled only when both cards are placed. Only then does a separate result screen show the combined sign sequence in Latin letters, with the two reference drawings.
5. **Edit combination** returns to the existing canvas; **Start again** clears it. Android Back from the result also returns to the canvas.

Order is explicitly left to right (with vertical position, then insertion index resolving ties). The canvas is a study arrangement, not an automatic reconstruction of historical block order. Source-proposed TWKM syllable values are joined with a hyphen. The result is a transliteration of the chosen signs, not a verified Maya word, pronunciation or translation. Drawings remain the existing TWKM CC BY 4.0 references; parts are not extracted from uploaded photographs.

The canvas and search survive navigation within the signed-in shell. They are not a cloud-saved notebook. Closing the keyboard restores the canvas; dragging or tapping a palette card also dismisses keyboard focus.

The Android regression suite covers a real drag onto the canvas, adding a second card, moving a placed card, hiding the combined reading before Next, checking the left-to-right result, editing, starting again, clearing and empty search results. Compilation alone is not reported as a runtime pass; see the completed Android checks run.

## Verified result

Application commit `5ffdb3016c611b578e3d302becd93cb3587c6a9b` passed the build, 180 JVM tests and all 20 Android runtime tests (zero failures/errors/skips). [Android checks run 44889020836](https://github.com/chuahws-wm23/MayaLanguageTranslatorApp/actions/runs/44889020836) includes the new drag/move/Next/result/edit/restart and clear/search checks alongside existing app workflows. Canvas and result screenshots were inspected. This is emulator evidence at the default phone size, not validation of every physical device or arbitrary syllable combinations as valid Maya.
