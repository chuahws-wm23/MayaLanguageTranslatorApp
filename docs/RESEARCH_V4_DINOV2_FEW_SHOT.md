# DINOv2 pretrained few-shot vision experiment

DINOv2 (Meta, ViT-Small, Apache 2.0) is a feature extractor pretrained
using self-supervised learning on a large unrelated image corpus. It can
sometimes produce useful visual embeddings with relatively few supervised
examples, but has not been shown to recognise Maya glyphs reliably.

This research trains a small SVM using only ten verified TWKM sign-ID
categories; hyperparameters and representation are selected using 3-fold
source-original-grouped cross-validation on the original 45 training
drawings. The 10 known validation drawings and 10 already-exposed historical
test drawings are evaluated only after selection. No unsupported-glyph
recognition or real-world camera-photo accuracy is established.

The model is *not* integrated into Android, the weights are not committed,
and Research v4 remains the only default deployed recognizer.

Pretrained model documentation: https://github.com/facebookresearch/dinov2
