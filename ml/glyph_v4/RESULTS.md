# Model v4 — Training and evaluation results

## Completed

Trained **10 catalogue sign categories** from the TWKM data source, compared two algorithms using a dedicated validation set, and exported the selected fine-tuned MobileNetV3-Small to ONNX for offline Android use.

The original sign drawings were split **before training** and individual original-image SHA-256 hashes were not duplicated across partitions.

| Experiment | Result |
|---|---:|
| Frozen pretrained MobileNetV3 + RBF SVM validation | 7/10 (70%) |
| Fine-tuned MobileNetV3 validation | **8/10 (80%)** |
| Fine-tuned v4 held-out known sign top-1 | **7/10 (70%)** |
| Correctly recognised AND accepted by v4 rejection filter | **1/10 (10%)** |
| Unknown sign examples rejected | **75/80 (93.75%)** |
| Unknown examples accepted incorrectly | 5/80 (6.25%) |
| Independent phone photograph evaluation | **Not performed** |

Validation determined the model selection and initial screening threshold.
The 10-known-drawing test (one original per sign) is statistically very small.
Unknown examples were drawn from 40 **different sign identities** excluded from
calibration, and all images originate from the **same TWKM catalogue**.

**Decision: Do not deploy v4 to replace Research v3.** Despite exceeding the
predeclared 50% closed-set top-1 target, v4's threshold rejects nine out of
ten known test drawings. Its functional recognised-and-accepted rate is only
10%. This is a critical product usability failure, not evidence that recognition
works well. Do not weaken the threshold based on seeing these test predictions
and then report them as an untouched test.

In the test, examples 44bb, 507hh and 1030ex were given the wrong top-1 sign;
the rest had correct top-1 predictions but mostly failed screening.
One incorrectly classified example was ACCEPTED, so incorrect recognitions
still occur.

## Research files

- outputs/evaluation.json — exact per-original predictions and all metrics.
- outputs/split_manifest.json — original-image hashes and fixed train/val/test splits.
- outputs/metadata.json — chosen classifier and validation thresholds.
- outputs/mobilenet_v4_finetuned.pt — selected trained PyTorch weights.
- outputs/gallery_v4.bin — training-original feature gallery (no full glyph images).
- outputs/android/maya_glyph_v4.onnx — validated two-output ONNX model.
- outputs/android/metadata.json — model hashes, shapes and numerical parity.
- train_v4.py — reproducible training and post-training evaluation.
- export_onnx.py — ONNX export with numerical parity assertions.

## Next steps before final-model acceptance

1. Collect and source-verify additional **different drawings** of the target signs,
   including hand drawings and independent camera photographs.
2. Create a new and fully independent phone-photograph test split before more
   hyperparameter and rejection-threshold selection.
3. Train a better explicit out-of-scope detector and calibrate against that
   *new* validation data, leaving external test sources untouched.
4. Require a useful **correct-and-accepted rate**, not only closed-set top-1,
   alongside a low false-accept rate on unknown drawings.
5. Only after device/inference testing and a source-independent evaluation
   should a replacement model be integrated into the ordinary Android flow.

**Do not quote v4's 70% as general glyph recognition accuracy or translation
accuracy.** It is 7/10 on catalogue drawings from one data source.
