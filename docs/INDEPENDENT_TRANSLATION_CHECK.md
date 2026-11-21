# Independent Translation Reference Candidates

The file
`dataset/evaluation/independent_reference_candidates.csv`
contains 30 candidate test cases taken from a published Yucatec Maya vocabulary
table that is independent of the app's Kaikki/English-Wiktionary dictionary
pipeline.

Source used for this first research pass:

- *Inteligencia Artificial* 28(76), 2025, Table 3, which reports a filtered
  English–Yucatec Maya noun list.

The candidates are intentionally marked:

`source_cross_checked_pending_human`

They are **not yet final gold-standard cases**. Before reporting final FYP
accuracy, a human reviewer should:

1. check the spelling/orthography used by the app;
2. check whether the expected translation is still appropriate for the exact
   sense being tested;
3. document an acceptable independent source;
4. approve or reject the test case.

Why this matters: several Yucatec Maya words have multiple senses and several
English concepts have multiple valid Yucatec Maya forms. The app now displays
multiple valid dictionary matches instead of silently selecting one.


## Automated coverage diagnostic

The dataset workflow also runs:

```bash
python dataset/scripts/score_reference_candidates.py \
  dataset/processed/maya_english_dictionary.csv \
  dataset/evaluation/independent_reference_candidates.csv \
  --report dataset/evaluation/independent_reference_coverage.json
```

This accepts the canonical English gloss **or a conservative English lookup
alias** derived from the gloss. It reports whether each independently sourced
candidate is currently supported by the app dictionary.

Do not label this number as "translation accuracy". It is a coverage diagnostic
until the candidate cases have been manually approved and sense ambiguity has
been reviewed.
