# Manual Regression Results

Date: 2026-10-15  
Branch: `fyp-development`

These results record user-observed behaviour on the Android application after
the Firestore dictionary was refreshed with lookup aliases.

## Dictionary lookup checks

| ID | Direction | Input | Expected behaviour | Result |
| --- | --- | --- | --- | --- |
| DICT-001 | English → Yucatec Maya | `earth` | Alias lookup resolves a dictionary entry | PASS |
| DICT-002 | English → Yucatec Maya | `hand` | Alias lookup resolves a dictionary entry | PASS |
| DICT-003 | English → Yucatec Maya | `back` | Alias lookup resolves a dictionary entry | PASS |
| DICT-004 | English → Yucatec Maya | `broom` | Multiple valid Maya forms are shown instead of one arbitrary result | PASS |
| DICT-005 | Yucatec Maya → English | `iit` | Multiple English senses are shown instead of one arbitrary result | PASS |

Observed for `iit`: the UI displayed several dictionary senses including
`base`, `bottom`, `butt, buttocks`, and `end`.

Observed for `broom`: the UI displayed more than one Yucatec Maya form and
allowed each meaning/form to be saved separately.

## Interpretation

This confirms:

- Firestore dictionary lookup is active;
- conservative English aliases are usable in the app;
- ambiguous dictionary entries no longer silently collapse to one result;
- the Possible translations UI is functioning on device/emulator.

These are regression checks, not a final linguistic-accuracy score.
