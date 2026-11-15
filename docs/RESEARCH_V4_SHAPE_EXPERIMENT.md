# Source-grouped HOG shape experiment

The user-reported TOK (#44) misclassification is reproducible using a
clean TWKM drawing. This experiment tests whether manually grounded
stroke/gradient shape features plus a regularized SVM complement the
existing MobileNetV3 classifier.

The script trains only on the 45 source-hash-separated TWKM training
originals, with image augmentation applied AFTER assigning each source
original to one of three training-only CV folds. The ten validation
originals and ten previously exposed test originals are not included in
cross-validation or fitting.

It compares 64/128px shape features, HOG alone versus HOG combined with
coarse pixel layout, and linear/RBF kernels. Hyperparameters are chosen
from training-only grouped CV rather than the previously exposed TOK test
image. It exports a confusion matrix, per-class precision, recall and F1,
known validation correctness, historical exposed test diagnostics, and
unknown-sign forced-acceptance evidence.

This is not a newly calibrated open-set detector and not a real-photo
accuracy measurement. It must not be deployed automatically or advertised
as reliable based on a handful of catalogue drawings.
