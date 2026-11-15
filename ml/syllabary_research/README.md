# Syllabary recognition: measured descriptor benchmark

Source images: four charts supplied by the user; 86 nonempty syllable cells
and 66 additional provisional drawings, **152** images total. Their labels
come from the chart cells, not a specialist independent verification.
Art rights to redistribute in a public Android APK are not established.

The reproducible [benchmark script](compare_visual_descriptors.py) accepts
the **local** `expanded_draft_reference_index.npz` from the user's research
ZIP. Glyph artwork and image index are deliberately not committed to GitHub.

Run from the project root:

```powershell
python -m pip install numpy opencv-python-headless scikit-image
python ml/syllabary_research/compare_visual_descriptors.py `
  --index "Maya_Syllabary_Expanded_Study/study/expanded_draft_reference_index.npz" `
  --output "ml/syllabary_research/local_results.json"
```

## Actual 45 chart-variant holdout results

Each query is the first extra crop for a chart syllable and is excluded
from the gallery. Other variants and all originals remain indexed.

| Descriptor | Top-1 | Top-3 |
|---|---:|---:|
| Pixel cosine | 5/45 | 9/45 |
| HOG 12px | 6/45 | 11/45 |
| HOG 8px | 6/45 | **13/45** |
| HOG 16px | 6/45 | 9/45 |
| Distance transform | 5/45 | 10/45 |
| Gaussian blur | 4/45 | 7/45 |
| Gradient | 4/45 | 10/45 |

These seven descriptor variants were inspected on the SAME exploratory
holdout. Choosing the best here is **test-set selection** and must not
be reported as unbiased accuracy. The existing expanded mixed HOG/pixel
matcher had 5/45 top-1 and 13/45 top-3. HOG 8px has not meaningfully
improved top-3 and top-1 remains far from 60–70%.

**Conclusion:** additional classical shape descriptors do not solve the
missing independently labelled examples problem. Do not ship an automatic
syllable reader or claim 60–70% on this evidence.

## Next engineering milestones

1. Manually verify that every retained crop shows ONE complete syllabogram
   and belongs to the correct syllable; do not count fragments as examples.
2. Collect independent verified sign variants for a smaller subset of
   10–20 syllables and preserve physical-source grouping.
3. Compare pretrained image embeddings on a separate source-group validation
   set, then evaluate once on untouched real photographs.
4. In Android, retain v18 block detection. Develop individual sign
   segmentation before applying single-sign recognition.
5. Implement a permission-cleared optional *Find Similar Glyphs* UI with
   top-three reference pictures, provenance and an Unknown choice; avoid
   automatically presenting guesses as translated text.

No Android files or model weights were modified by this benchmark.

## Next candidate: pretrained MobileNetV3 embeddings

A separate [learned visual-feature evaluator](evaluate_pretrained_embeddings.py)
has been added for the SAME local 152-reference index. This is a **new
candidate script, not yet a measured improvement**. The first run may
download pretrained ImageNet weights; no chart images are uploaded.

```powershell
python -m pip install torch torchvision Pillow
python ml/syllabary_research/evaluate_pretrained_embeddings.py `
  --index "Maya_Syllabary_Expanded_Study/study/expanded_draft_reference_index.npz" `
  --output "ml/syllabary_research/pretrained_local_results.json"
```

Compare the measured top-1/top-3 outcomes against the 5/45 and 13/45
expanded-gallery baseline, but do not select on these chart holdouts and
then report them as unbiased test accuracy. We still need independently
verified glyph images from distinct original sources for a credible
60–70% target.
