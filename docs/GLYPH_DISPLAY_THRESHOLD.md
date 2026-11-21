# User-selected visual similarity filter

The interface shows one nearest reference per glyph region only when its raw cosine similarity is strictly greater than 0.85. Exactly 0.85, lower, nonfinite and out-of-range scores are not accepted. The UI says **No match found** for a readable query whose best candidate fails the filter, and does not show its reference image or proposed reading/meaning. Unreadable input retains photo guidance.

Displayed values are raw visual similarity multiplied by 100, formatted to two decimal places and labelled **Visual similarity**. They are not identification confidence or a measured probability of correctness. Ordinary rounding may display a value extremely close to the cutoff as 85.00%; the decision uses the raw float score, not the rounded label.

The same display policy applies to single/multiple scanner results and History/Favourites details. Historical candidate data are retained, but low-score candidates are filtered out of the visible list, summary and search in History/Favourites. Home uses eligible candidates only. Existing reference notes remain optional. Each multi-glyph region is filtered independently.

The 85% cutoff is a user-requested presentation rule, not a cutoff derived from expert-labelled validation data. Different reference identities can score about 95%; a displayed suggestion is still unverified. Model weights, embeddings and scientific top-k retrieval evaluation are unchanged. Raising the cutoff can hide more incorrect candidates, but can also hide correct ones. No calibrated unknown-rejection accuracy is claimed.

Tests cover exactly 85%, below/above boundary, invalid values, one eligible result per region, percentage formatting, candidate image removal after a high-to-low score change, and existing saved/history workflows using clearly marked high-score fixtures.
