# Yucatec Maya Dataset Pipeline

This folder contains the reproducible data-preparation workflow for the FYP.

## Source

Primary machine-readable source:

- Kaikki / English Wiktionary — Yucatec Maya
- https://kaikki.org/dictionary/Yucatec%20Maya/index.html
- ISO 639-3: `yua`

Kaikki states that its Wiktionary-derived data is made available under the same
licenses as Wiktionary (CC BY-SA and GFDL). Retain attribution and licensing
information if distributing derived data.

The source page also asks academic users to cite Tatu Ylonen's 2022 LREC paper
on Wiktextract.

## Build

From the repository root:

```bash
python dataset/scripts/download_kaikki.py
python dataset/scripts/convert_kaikki.py \
  dataset/raw/kaikki-yucatec-maya.jsonl \
  dataset/processed/maya_english_dictionary.csv
python dataset/scripts/validate_dictionary.py \
  dataset/processed/maya_english_dictionary.csv \
  --report dataset/processed/validation_report.json
```

The converter uses deterministic IDs derived from the normalised Maya text,
English gloss and part of speech. Re-downloading a newer source dump therefore
does not randomly renumber unchanged entries.

## Verification policy

`verified=false` means only that the row was machine-extracted and has not yet
been manually approved for the FYP evaluation set. It does **not** mean the
source is necessarily wrong.

Do not change rows to `verified=true` merely because they exist in the source
file. Manual checking should use an acceptable linguistic reference and record
the reviewer/source in the evaluation sheet.

## Evaluation

Generate a deterministic candidate subset:

```bash
python dataset/scripts/prepare_evaluation_sample.py \
  dataset/processed/maya_english_dictionary.csv \
  dataset/evaluation/manual_evaluation.csv \
  --size 200
```

Review each candidate. Set `verified=true` only for approved cases. Then run:

```bash
python dataset/scripts/evaluate_dictionary.py \
  dataset/processed/maya_english_dictionary.csv \
  dataset/evaluation/manual_evaluation.csv \
  --report dataset/evaluation/evaluation_report.json
```

Reported metrics:

- exact-match accuracy on manually verified cases
- dictionary coverage
- number of no-match cases
- mismatch details for review

## Firestore import

Install the Firebase Admin dependency:

```bash
pip install -r dataset/requirements.txt
```

Keep the service-account JSON outside the repository and set:

```bash
# PowerShell
$env:GOOGLE_APPLICATION_CREDENTIALS="C:\path\to\service-account.json"
```

Dry-run first:

```bash
python dataset/scripts/import_firestore.py \
  dataset/processed/maya_english_dictionary.csv \
  --dry-run
```

Then import:

```bash
python dataset/scripts/import_firestore.py \
  dataset/processed/maya_english_dictionary.csv
```

The app reads from Firestore collection `dictionary`. Automatically imported
rows stay marked `verified=false`; source URL and licensing metadata are kept
with each document.
