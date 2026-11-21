# Experimental Maya glyph v2 on Android

## Status and history

The existing 29 KB **TinyGlyphNet v1** remains the default, supporting experimental reference labels: Deer (CHIJ), Hawk (MUWAAN), Jaguar (HIX), Star (EK'), Cacao (KAKAW), and Other.

This branch adds an **opt-in Research v2** mode using actual exported pretrained **MobileNetV3-Small + RBF SVM** inference **offline on Android** through ONNX Runtime.

## How to test from Android Studio

1. Use the `fyp-development` branch once this integration has been merged; otherwise use `glyph-v2-android-integration` for initial testing.
2. Pull the branch, sync Gradle, and Run on a real Android phone or emulator.
3. Open **Photo translation > Maya glyph recognition**.
4. Tap **Research v2** to switch the model. No training, Python or external server is required on the phone.
5. Use Camera or Gallery, crop **one glyph without surrounding text**, tap **Recognise glyph**.
6. Results are labelled **Research v2 — experimental suggestion**, with a prominent warning that the classifier **always picks one of five classes**, even for unknown glyphs.

## Scope and metrics

| Selected sign | TWKM sign no. | Proposed meaning | 5-fold catalogue cross-validation |
|---|---:|---|---:|
| K'UH | 32 | God / deity | 9/9 |
| K'INICH | 184 | Resplendent / sunny | 8/8 |
| SIIP | 291 | Deity name | 7/8 |
| NAAH | 4 | First / house | 7/7 |
| TOK | 44 | Cloud / mist | 4/6 |
| Total | | | **35/38 = 92.1%** |

**This is research cross-validation on a small selection of TWKM original drawings, not phone-camera accuracy, general Classic Maya reading accuracy, or verified English translation.** It is susceptible to selection optimism because variants of candidate models were compared using the same cross-validation data. Test independently on phone photos and hand drawings; implement proper out-of-distribution rejection before presenting results as identified signs.

The Research v2 model **DOES NOT include CHIJ (Deer) or KAKAW (Cacao)**. Continue to use Model v1 for demonstration of those experimental categories; its real-world accuracy also remains unverified.

**Device requirement:** Android 7.0 / API level 24 or later, required by ONNX Runtime Android 1.31.1. The previous app supported API 23; this integration intentionally raises the minimum without a manifest override.

## Assets and runtime

- `app/src/main/assets/maya_mobilenet_v2.onnx` — exported frozen visual backbone + normalized feature vector (~3.7 MB).
- `app/src/main/assets/maya_svm_v2.onnx` — trained SVM closed-set classifier (~320 KB).
- `app/src/main/assets/maya_glyph_v1.mgl` — retained lightweight v1 model (~29 KB).
- `app/src/main/java/com/chuahws/mayalanguageapp/integration/glyph/GlyphV2Preprocessor.kt` — shape normalization following the v2 research pipeline.
- `.../MayaGlyphRecognizerV2.kt` — offline dual-ONNX session with SHA-256 asset validation.
- The app includes ONNX Runtime Android; the APK size increase includes this runtime and supported ABIs. **No Python/PyTorch/scikit-learn package is installed on Android.**
- Both ONNX exports were tested against PyTorch/scikit-learn numerically in GitHub Actions. Android runtime behavior still needs physical-device testing.

## Safeguards

Predictions are not written to the Firebase verified text-translation dictionary, saved vocabulary, or photo history. A copy action includes an explicit unverified disclaimer. Legacy text-photo history replay is preserved. User-selected Gallery files are never deleted.

References: TWKM *Maya Glyphs* snapshot `73fe9da1d975a5f1e588c7d42c952dfda53cefd0`, University of Bonn standardised glyph drawings CC BY 4.0. Catalogue metadata rights may differ.

Research code, evaluation reports and original PyTorch/SVM training artifacts are retained separately in the `glyph-model-v2-experiment` branch under `ml/glyph_v2`.
