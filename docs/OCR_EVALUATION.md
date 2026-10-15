# OCR Evaluation Guide

The OCR benchmark should measure **OCR quality separately from translation
quality**.

## Fixed conditions

Start with at least 8 labelled images:

1. clean digital text
2. printed text
3. small font
4. angled image
5. low light
6. blurred image
7. mixed Yucatec Maya + English
8. multi-line Yucatec Maya

For stronger final results, use 3 images per condition (24 total).

## Recording results

Copy:

`dataset/evaluation/ocr_evaluation_template.csv`

and fill:

- `image_file`
- `ground_truth_text`
- `ocr_output` — exactly what ML Kit returned before manual correction
- `corrected_text` — what the user corrected in the editable field
- `final_translation`
- `ocr_rating`
- `translation_rating`
- `notes`

Do not correct `ocr_output` before scoring.

## Automatic OCR metrics

Run:

```bash
python dataset/scripts/evaluate_ocr.py \
  dataset/evaluation/ocr_evaluation.csv \
  --report dataset/evaluation/ocr_report.json
```

The report gives:

- exact-match rate
- normalised exact-match rate
- character error rate (CER)
- per-image CER

A lower CER is better.

## FYP interpretation

Report at least two different results:

1. **raw OCR performance** — how closely ML Kit reproduced the labelled text
2. **end-to-end task performance** — whether the final translation was correct
   after the app allowed the user to review/edit OCR text

This distinction is important because a correct translation after user
correction should not be presented as perfect raw OCR.
