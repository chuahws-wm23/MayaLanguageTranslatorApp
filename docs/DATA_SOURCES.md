# Yucatec Maya Data Sources

The production translation path is dictionary-based. Do not add invented
Yucatec Maya words to the application.

## Primary machine-readable source

Kaikki / English Wiktionary — Yucatec Maya machine-readable dictionary:

https://kaikki.org/dictionary/Yucatec%20Maya/index.html

The source currently exposes several hundred distinct Yucatec Maya word forms
and machine-readable English glosses. The exact count changes as Wiktionary is
updated, so the FYP pipeline records the downloaded file hash rather than
hard-coding a permanent row count.

Kaikki says its Wiktionary-derived data is available under the same licenses as
Wiktionary: CC BY-SA and GFDL.

Academic citation requested by Kaikki:

Tatu Ylonen. "Wiktextract: Wiktionary as Machine-Readable Structured Data."
Proceedings of the 13th Conference on Language Resources and Evaluation
(LREC 2022), pages 1317–1325.

## Independent cross-check source

ASJP Yucatec Maya / Maya Yucatan wordlist:

https://asjp.clld.org/languages/MAYA_YUCATAN

The ASJP page identifies ISO 639-3 `yua`, lists published dictionary sources,
and states that the ASJP Database is licensed under CC BY 4.0.

Use ASJP as an independent cross-check/reference source, not as a replacement
for manual verification of the evaluation cases.

## Data-quality policy

The repository separates:

1. machine-extracted source rows
2. application dictionary rows
3. manually verified evaluation rows

Kaikki rows are imported with `verified=false`. The evaluation tool scores
only cases that a reviewer explicitly marks `verified=true`.

This prevents the project from using its own machine-extracted source as
unquestioned ground truth when reporting translation quality.

See `dataset/README.md` for the reproducible workflow.
