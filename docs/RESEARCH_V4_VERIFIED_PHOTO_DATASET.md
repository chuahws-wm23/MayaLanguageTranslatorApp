# Verified real-photograph dataset — Research v4

Current limitation: only **4 distinct TOK training originals, 3 K'ABA'
training originals and 4 HAAB training originals**. Synthetic effects,
more filters or higher model resolution cannot substitute for independently
labelled, genuinely different source glyphs.

## Local photo manifest

This directory contains a CSV TEMPLATE only. Do not invent labels or
commit private user camera photos. Put your locally sourced, licensed
single-glyph images under `dataset/local_verified_photos/` (gitignored).

For every source image, add ONE row to
`dataset/evaluation/real_single_glyph_photo_manifest_template.csv`:

- `image_path`: relative PNG/JPEG/WEBP path inside the photo directory
- `twkm_sign`: one of 32, 184, 291, 4, 44, 507, 548, 122, 187, 1030,
  or `UNKNOWN` for verified signs outside this set
- `source_group`: the **underlying original drawing/carving/object**
  identifier (every screenshot, rotated crop, re-photograph of the same
  drawing belongs in the SAME group)
- `source_url`: original artifact or photographer source
- `license`: actual image rights (or documented research permission)
- `verified_by`: who checked sign identity; do not assert expert
  verification if it did not occur
- `split`: `train`, `validation`, or `test`

**Do not reuse** the previously exposed TOK graph `44bb` as unseen test
data or mix photographs of that same graph into different splits.
If sign identity is not known, mark it UNKNOWN only when it is independently
verified to be outside the supported classes, not for arbitrary wrong guesses.

## Run audit on Windows (PowerShell)

```powershell
python -m pip install Pillow numpy
python dataset/scripts/validate_real_glyph_photos.py `
  --manifest dataset/evaluation/real_single_glyph_photo_manifest_template.csv `
  --root dataset/local_verified_photos `
  --out dataset/local_verified_photos/audit.json
```

Only after filling the manifest will it validate. Use `--strict` to enforce
provisional research sample targets.

For every sign, the initial *research target* is at least 8 independent
training, 3 validation and 5 test source-groups, alongside 20 unknown
validation and 50 unknown test examples. These are **minimum collection
targets**, NOT a guarantee of a good classifier. Future inference must
report confidence intervals, per-sign recall, correct-and-accepted known
rates, and wrong acceptance of unsupported glyphs.

The audit blocks exact-file leakage and source-group leakage, flags
perceptual near-duplicate candidates, checks file validity, and records
missing or unsupported sign labels. It does NOT automatically certify
archaeological readings or image licences. Keep actual images local or
share them separately only with explicit permission.

The best additional source is independent, annotated photographs with
genuinely different stylistic variants of target signs. Public museum
photos or glyph archives are not automatically ground-truth single-sign
classifications without specialist review.
