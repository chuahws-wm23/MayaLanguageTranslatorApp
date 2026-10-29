# English Lookup Aliases

Machine-readable dictionary glosses are often more descriptive than the short
English word a user types.

Examples:

- `hand, arm`
- `soil, earth`
- `back (of the body)`

Exact-only English lookup would make user inputs such as `hand`, `earth` or
`back` fail even though the dictionary contains the relevant entry.

The dataset converter now creates **conservative English aliases**:

- short comma-separated alternatives are normalised as aliases;
- a trailing parenthetical qualifier can be removed for lookup;
- explanatory comma clauses beginning with wording such as `as ...` are not
  treated as separate meanings.

The canonical dictionary gloss is preserved unchanged for display and source
traceability. Aliases are only additional search keys.

Firestore stores them in:

`acceptedEnglishInputs: [ ... ]`

The app first tries the canonical `englishNormalized` field. If that returns
no result, it performs an `array-contains` alias lookup.

After updating an already-imported Firestore dictionary, re-run the importer
with the rebuilt CSV. It uses merge writes, so the alias field is added without
deleting unrelated document fields.
