# Exactly which 25 TWKM sign IDs are in the expanded FYP prototype?

**Data:** GitHub Actions research audit using `tadad/twkm-maya-glyphs`,
pinned revision `73fe9da1d975a5f1e588c7d42c952dfda53cefd0`.
The 25-class training subset contains **101 distinct source-image hashes**
from catalogue drawings and **two separate held-out graph images per class**
(validation and historical test). Source-image hashes do **not** ensure
independent physical artifacts.

The original Android Research v4 has 10 classes; the new optional
`TWKM 25 (research)` model uses these exact **25 IDs**:

| # | TWKM sign ID | Train catalogue graphs | Group |
|---:|---:|---:|---|
| 1 | 32 | 7 | Research v4 class |
| 2 | 184 | 6 | Research v4 class |
| 3 | 291 | 6 | Research v4 class |
| 4 | 4 | 5 | Research v4 class |
| 5 | 44 | 4 | Research v4 class |
| 6 | 507 | 4 | Research v4 class |
| 7 | 548 | 4 | Research v4 class |
| 8 | 122 | 3 | Research v4 class |
| 9 | 187 | 3 | Research v4 class |
| 10 | 1030 | 3 | Research v4 class |
| 11 | 74 | 5 | New experimental class |
| 12 | 178 | 5 | New experimental class |
| 13 | 1 | 4 | New experimental class |
| 14 | 21 | 4 | New experimental class |
| 15 | 28 | 4 | New experimental class |
| 16 | 102 | 4 | New experimental class |
| 17 | 325 | 4 | New experimental class |
| 18 | 595 | 4 | New experimental class |
| 19 | 630 | 4 | New experimental class |
| 20 | 126 | 3 | New experimental class |
| 21 | 177 | 3 | New experimental class |
| 22 | 204 | 3 | New experimental class |
| 23 | 569 | 3 | New experimental class |
| 24 | 586 | 3 | New experimental class |
| 25 | 604 | 3 | New experimental class |

**These are TWKM sign IDs, not Roboflow glyph codes, English words or
phonetic readings.** Some catalogue entries lack verified readings, so the
Android research mode shows sign IDs only and never invents a translation.

## How to contribute new photos

Choose one sign from this table, collect genuine independent examples of
that sign, and obtain a trustworthy sign-ID verification. Save **one
isolated sign per image**. The exact sign ID goes in `twkm_sign` in the
photo manifest. Use the literal `UNKNOWN` only for a verified sign outside
these 25 IDs; a random unlabelled image is not a verified negative example.

Prioritize identities with only 3–4 train catalogue graphs, and include
photos with different carving styles and lighting. Add a source URL,
licence, verifier and source-group ID. Every photograph or screenshot of
one physical artifact belongs in the same source group and must stay in
**train OR validation OR test**, never more than one.

See [Android installation and retraining instructions](TWKM25_ANDROID_INSTALL_AND_NEW_DATA.md).

## Research-only results

The pretrained MobileNetV3-Small + RBF SVM reached 17/25 correct top-1
on the previously exposed catalogue test cohort, 12/25 correctly
accepted at its research screening threshold, and rejected 38/40 other
catalogue sign identities.

That is **not** phone-photo accuracy, whole-block reading or translation
accuracy. Additional independent real-photo test samples are necessary
before any release claim.

Source: Christian Prager / TWKM, University of Bonn, standardized graph
drawings marked CC BY 4.0. Check individual rights before redistribution.
