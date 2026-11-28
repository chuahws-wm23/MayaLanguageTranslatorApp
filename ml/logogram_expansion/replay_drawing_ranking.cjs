#!/usr/bin/env node
// Catalogue development replay. This measures ranking on drawings, not photo accuracy.
const { readFileSync } = require("node:fs");
const { resolve } = require("node:path");
const { createHash } = require("node:crypto");
const assets = resolve(__dirname, "../../app/src/main/assets");
const read = (name) => readFileSync(resolve(assets, name));
const manifest = JSON.parse(read("maya_reference_embeddings.json"));
const gallery = manifest.galleries.logogram;
function checked(name, expected) {
    const data = read(name);
    if (createHash("sha256").update(data).digest("hex") !== expected) {
        throw new Error(`Unexpected gallery asset: ${name}`);
    }
    return data;
}
const rows = JSON.parse(checked("maya_twkm_logogram_index.json", gallery.index_sha256)).entries;
const bitplanes = checked("maya_twkm_logogram_bitmap.bin", gallery.artwork_sha256);
const embeddings = checked("maya_logogram_embeddings.bin", gallery.vectors_sha256);
const dim = manifest.dimension;
if (rows.length !== gallery.count || bitplanes.length !== rows.length * 512 ||
    embeddings.length !== rows.length * dim * 4) throw new Error("Gallery sizes disagree");
const masks = rows.map((_, i) => bitplanes.subarray(i * 512, (i + 1) * 512).toString("hex"));
const vectors = rows.map((_, i) => Float32Array.from({length: dim},
    (_, d) => embeddings.readFloatLE((i * dim + d) * 4)));
const weights = [0, .1, .2, .35, .5];
const totals = { development: 0, holdout: 0 };
const groups = { development: new Set(), holdout: new Set() };
const counts = weights.map(weight => ({ weight, development: { top1: 0, top3: 0, gained: 0, lost: 0 },
    holdout: { top1: 0, top3: 0, gained: 0, lost: 0 } }));
const inkCounts = weights.map(weight => ({ weight, development: { top1: 0, top3: 0 },
    holdout: { top1: 0, top3: 0 } }));
const inkProfiles = rows.map((_, row) => {
    const cells = new Float32Array(64);
    for (let y = 0; y < 64; y++) {
        for (let x = 0; x < 64; x++) {
            if (bitplanes[row * 512 + y * 8 + (x >> 3)] & (128 >> (x & 7))) {
                cells[(y >> 3) * 8 + (x >> 3)]++;
            }
        }
    }
    const norm = Math.hypot(...cells);
    if (norm === 0) throw new Error("Empty drawing");
    return cells.map(count => count / norm);
});

for (const [i, query] of rows.entries()) {
    // Exclude every byte-identical drawing, including duplicates with another catalogue ID.
    if (!rows.some((other, j) => other.sign === query.sign && masks[j] !== masks[i])) continue;
    const slice = ((Math.imul(query.sign, 2654435761) >>> 0) % 5 === 0) ? "holdout" : "development";
    totals[slice]++;
    groups[slice].add(query.sign);
    const scoresBySign = new Map();
    const inkScores = weights.map(() => new Map());
    for (const [j, candidate] of rows.entries()) {
        if (masks[i] === masks[j]) continue;
        let cosine = 0;
        for (let d = 0; d < dim; d++) cosine += vectors[i][d] * vectors[j][d];
        let inkSimilarity = 0;
        for (let cell = 0; cell < 64; cell++) inkSimilarity += inkProfiles[i][cell] * inkProfiles[j][cell];
        weights.forEach((weight, k) => {
            const score = (1 - weight) * cosine + weight * inkSimilarity;
            const scores = inkScores[k];
            scores.set(candidate.sign, Math.max(scores.get(candidate.sign) ?? -Infinity, score));
        });
        const scores = scoresBySign.get(candidate.sign) ?? [];
        scores.push(cosine);
        scoresBySign.set(candidate.sign, scores);
    }
    const bestTwo = [...scoresBySign].map(([sign, scores]) => {
        scores.sort((a, b) => b - a);
        const [best, second = best] = scores;
        return { sign, best, gap: best - second };
    });
    let baselineCorrect = false;
    for (const result of counts) {
        const ranked = bestTwo.map(({sign, best, gap}) => ({ sign, score: best - result.weight * gap }))
            .sort((a, b) => b.score - a.score || a.sign - b.sign);
        const correct = ranked[0].sign === query.sign;
        if (result.weight === 0) baselineCorrect = correct;
        const measures = result[slice];
        measures.top1 += Number(correct);
        measures.top3 += Number(ranked.slice(0, 3).some(({sign}) => sign === query.sign));
        measures.gained += Number(correct && !baselineCorrect);
        measures.lost += Number(!correct && baselineCorrect);
    }
    for (const [k, scores] of inkScores.entries()) {
        const ranked = [...scores].sort((a, b) => b[1] - a[1] || a[0] - b[0]).slice(0, 3)
            .map(([sign]) => sign);
        inkCounts[k][slice].top1 += Number(ranked[0] === query.sign);
        inkCounts[k][slice].top3 += Number(ranked.includes(query.sign));
    }
}

const chosen = counts.reduce((best, next) => next.development.top1 > best.development.top1 ? next : best);
console.log(JSON.stringify({ study: "Catalogue drawing replay with sign-disjoint tuning and evaluation",
    scope: "Embeddings only. Android preprocessing, display safeguards and camera photos are outside this replay.",
    index_sha256: gallery.index_sha256, totals,
    sign_counts: { development: groups.development.size, holdout: groups.holdout.size },
    development: counts.map(({weight, development}) => ({weight, ...development})),
    holdout_baseline: counts[0].holdout, holdout_selected: {weight: chosen.weight, ...chosen.holdout},
    ink_distribution_development: inkCounts.map(({weight, development}) => ({weight, ...development})),
}, null, 2));
