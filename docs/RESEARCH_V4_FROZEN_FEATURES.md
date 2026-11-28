# TWKM few-shot ResNet18/EfficientNet frozen feature experiment

Comparative experiment for the ongoing TOK (#44) vs K'ABA' (#187)
misclassification problem. Uses ImageNet-supervised pretrained feature
extractors without any self-assigned sign labels or data leakage.

Both 224px grayscale and binarized shapes are presented as normalized RGB
to a **frozen** ResNet18 or EfficientNet-B0 encoder. An SVM is trained on
the same 45 verified TWKM training originals as Research v4. Hyperparameters
are chosen through **training-original-grouped stratified cross-validation**
rather than the previously exposed test image. It then evaluates all ten
classes on the reserved validation drawing and reports known historical
test diagnostics only after selection.

No changes are made to the Android ONNX model. These visual models are
not open-set detectors and have not been evaluated on independent real
camera photographs. Research artifacts are retained for comparison.
