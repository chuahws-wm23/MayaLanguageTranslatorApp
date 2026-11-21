# Research 128×128 Maya glyph model — completed training (15 Nov 2026)

**Decision:** Two new 128px models were trained and exported. Neither improves on the existing Android Research v4. Leave v4 deployed.

## Actual training data

- Kaggle user-supplied Mayan Glyphs ZIP: **16,600 PNGs** total, many blank and exact duplicates; **80 font-key classes**, **3,200 deduplicated selected images** used for stage-one visual pretraining (20 filled + 20 outline samples per class).
- TWKM pinned graph drawings: **45 verified sign-labelled training originals**, **10 validation originals** and **10 previously exposed test originals** across ten supported sign numbers [32,184,291,4,44,507,548,122,187,1030].
- Kaggle `U_XXXX` font-key labels were **never** treated as Maya readings or TWKM sign numbers.
- The TWKM input retains **128×128 canonical glyph detail**; the previous v4 pipeline used 64px canonical glyphs enlarged for the CNN.
- Models are MobileNetV3-Small, trained using class-labelled images, rotations, translations and light degradation; the final head is ten TWKM classes.

## Reproducible results

| Model | Clean validation | Simulated camera variants | Previously exposed TWKM test |
|---|---:|---:|---:|
| Existing Android Research v4 | **8/10** | **24/30** | **7/10** |
| New TWKM-only 128px | 6/10 | 18/30 | 4/10 |
| New Kaggle-pretrained + TWKM 128px | 6/10 | 18/30 | 5/10 |

**Neither 128px model passed the predeclared validation improvement gate.**

The original TOK (#44, graph 44bb) is still misclassified by both new models as #548 (HAAB), not correctly recognized. Testing the user's `tok.png` produced #548 as the top result with approximately 51.1% **relative closed-set model score** for the Kaggle+TWKM candidate. A model score is not verified confidence.

The test images were seen in earlier v4 research. This is a regression check, **not** a new untouched independent evaluation. Simulated image perturbations are not real photographs. No independent real-world accuracy or unknown-sign detection has been established.

## Models and source

- Reproducible script: [train_128.py](../ml/research_128_candidate/train_128.py)
- [Training workflow and downloadable model artifact](https://github.com/chuahws-wm23/MayaLanguageTranslatorApp/actions/runs/43680463041)
- Produced **two 128×128 ONNX models** and PyTorch checkpoints, plus evaluation JSON and confusion matrices; ONNX versus PyTorch maximum absolute output error was approximately **0.0000011**, passing parity checks.
- Python image tester: `ml/scripts/test_128_maya_glyph.py`.

After downloading the GitHub Actions model artifact into `ml/research_128_candidate/out`, try:

```powershell
python -m pip install numpy opencv-python-headless pillow onnxruntime
python .\ml\scripts\test_128_maya_glyph.py --image ".\tok.png" --expected 44
```

Use `--model "ml/research_128_candidate/out/twkm_128_no_kaggle.onnx"` to test the control model instead.

## Follow-up scientifically needed

Training a larger 128px classifier with only 45 verified training originals did not generalise better here. The next high-value improvement is additional independently labelled TOK, K'ABA' and other sign variants, plus a truly independent real-photo test group. **Do not retrain on the previously exposed TOK held-out sample to inflate test results.** No Android deployment of the new candidate is justified yet.

### Rights
TWKM standardized glyph drawings by Christian Prager, University of Bonn, CC BY 4.0. The Kaggle publisher's accompanying article indicates non-commercial/no-derivatives conditions; review upstream terms before distributing derived weights publicly. The exported models are research-only candidate artifacts, not a released licensed model.
