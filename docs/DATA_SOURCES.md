# Yucatec Maya Data Sources

The translation design is dictionary-based. Do not add invented Yucatec Maya words to the application.

## Primary machine-readable source

Kaikki / English Wiktionary — Yucatec Maya machine-readable dictionary:

https://kaikki.org/dictionary/Yucatec%20Maya/index.html

The development build contains only a small set of source-checked entries so the application can be demonstrated before Firebase is configured.

For the full dictionary:

1. Download the Yucatec Maya JSONL data from the Kaikki page.
2. Run dataset/scripts/convert_kaikki.py.
3. Run dataset/scripts/validate_dictionary.py.
4. Review the evaluation subset manually.
5. Import the processed CSV with dataset/scripts/import_firestore.py.

Automatically imported entries remain marked verified=false until they are checked.

## Academic attribution

For research use, Kaikki asks users to cite:

Tatu Ylonen. Wiktextract: Wiktionary as Machine-Readable Structured Data. Proceedings of the 13th Conference on Language Resources and Evaluation (LREC 2022), pages 1317–1325.

Retain the source page and applicable Wiktionary licensing information with any distributed dataset.
