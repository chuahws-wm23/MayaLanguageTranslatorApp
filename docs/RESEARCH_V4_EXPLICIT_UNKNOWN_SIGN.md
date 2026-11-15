# Explicit known versus unknown TWKM sign detector

This experiment trains a small detector using the existing v4 visual
features and true sign-ID labels. Fifty labelled negative drawings from
25 unsupported sign identities form the negative training set. Thirty
drawings from 15 *other* unsupported identities calibrate a threshold.
A final eighty drawings from forty *different* unknown sign identities
are reserved for testing, separate from all training and threshold
calibration sign categories.

It evaluates **correct-and-accepted known** versus **unknown rejected**.
A candidate is calibrated only if it correctly accepts at least 6 of 10
supported known validation glyphs and rejects at least 20/30 unknown
validation drawings. The already exposed historical known test and
the unseen 40-sign identity test are then reported, without tuning on them.

Models, thresholds and unsupported-sign findings remain research-only
unless they generalise to expert-verified independent real camera photos.
The ten existing v4 sign class weights remain unchanged. Classifying
a drawing as UNKNOWN does not prove its Maya reading is known.
