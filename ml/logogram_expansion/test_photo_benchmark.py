"""Synthetic evaluation-tool tests. These are not glyph-accuracy measurements."""
import copy
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from photo_benchmark import box_iou, detection_counts, evaluate_public, review_pack, score_regions


class PhotoBenchmarkTest(unittest.TestCase):
    def fixture(self):
        samples = []
        results = []
        for i, expected in enumerate([['AJAW'], ['AJAW'], []]):
            row = {'id': str(i), 'file': f'{i}.jpg', 'source_group': 'vessel' if i < 2 else 'stone',
                   'source_url': 'https://example.org/fixture', 'license': 'test fixture',
                   'sha256': hashlib.sha256(str(i).encode()).hexdigest(), 'expected_readings': expected,
                   'label_is_expert_verified_exact_sign': False}
            if i == 1:
                row['derived_from'] = '0.jpg'
            samples.append(row)
            results.append({**row, 'production_candidates': [{'sign': 1, 'reading': 'AJAW'}],
                'research_illumination_candidates': [{'sign': 2, 'reading': 'OTHER'}],
                'region_proposals': [[0, 0, 1, 1]], 'region_proposal_count': 1})
        return {'format_version': 1, 'results': results}, {'format_version': 1,
            'independent_expert_verified_test': False, 'samples': samples}

    def test_reading_clues_and_closeups_do_not_become_accuracy_or_independent_objects(self):
        report, manifest = self.fixture()
        result = evaluate_public(report, manifest)
        self.assertEqual(2, result['source_groups'])
        self.assertEqual(1, result['slices']['original_photo']['reading_described_inputs'])
        self.assertEqual(1, result['slices']['operator_closeup']['reading_described_inputs'])
        self.assertIsNone(result['cases'][2]['production_reading_in_top1'])
        self.assertIsNone(result['known_sign_top1_accuracy'])
        self.assertIsNone(result['unknown_rejection_accuracy'])
        self.assertIsNone(result['detection_precision'])
        self.assertFalse(result['release_approved'])

    def test_runtime_errors_cannot_count_as_hits(self):
        report, manifest = self.fixture()
        report['results'][0]['runtime_error'] = 'decode'
        result = evaluate_public(report, manifest)
        self.assertFalse(result['cases'][0]['production_reading_in_top5'])
        self.assertEqual(1, result['runtime_errors'])

    def test_missing_duplicate_and_wrong_provenance_fail_closed(self):
        report, manifest = self.fixture()
        for mutate in [lambda r: r['results'].pop(), lambda r: r['results'].append(r['results'][0]),
                       lambda r: r['results'][0].update(sha256='wrong'),
                       lambda r: r['results'][0].update(source_group='wrong')]:
            altered = copy.deepcopy(report); mutate(altered)
            with self.assertRaises(ValueError):
                evaluate_public(altered, manifest)

    def test_iou_duplicate_predictions_and_missing_vs_negative_ground_truth(self):
        box = [0, 0, 1, 1]
        self.assertEqual(1, box_iou(box, box))
        self.assertIsNone(detection_counts([box], None))
        self.assertEqual({'tp': 0, 'fp': 1, 'fn': 0, 'iou_threshold': .5}, detection_counts([box], []))
        self.assertEqual({'tp': 1, 'fp': 1, 'fn': 0, 'iou_threshold': .5}, detection_counts([box, box], [box]))
        self.assertEqual(1, detection_counts([], [box])['fn'])

    def test_matching_finds_two_true_positives_when_greedy_matching_would_find_one(self):
        result = detection_counts([[0, 0, .7, 1], [0, 0, .4, 1]], [[0, 0, .5, 1], [.25, 0, .75, 1]])
        self.assertEqual(2, result['tp'])

    def test_malformed_boxes_and_thresholds_are_rejected(self):
        for box in [[0, 0, 0, 1], [0, 0, 2, 1], [0, 0, float('nan'), 1], [True, 0, 1, 1]]:
            with self.assertRaises(ValueError):
                detection_counts([box], [])
        with self.assertRaises(ValueError):
            detection_counts([], [], 0)

    def test_region_scoring_requires_review_and_matching_image_bytes(self):
        report, _ = self.fixture()
        ann = {'format_version': 1, 'images': [{
            'id': '0', 'sha256': report['results'][0]['sha256'], 'source_group': 'vessel',
            'reviewed_by': 'synthetic fixture', 'source_citation': 'synthetic fixture',
            'exhaustive': True, 'boxes': [[0, 0, 1, 1]]}]}
        result = score_regions(report, ann)
        self.assertEqual(1, result['annotated_images'])
        self.assertEqual(2, result['unannotated_images'])
        self.assertEqual(1, result['metrics']['0.5']['recall'])
        ann['images'][0]['sha256'] = 'wrong'
        with self.assertRaises(ValueError): score_regions(report, ann)
        self.assertIsNone(score_regions(report, {'format_version': 1, 'images': []})['metrics'])

    def test_review_pack_assigns_no_labels_and_checks_hashes_before_output(self):
        _, manifest = self.fixture()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for i in range(3): (root / f'{i}.jpg').write_text(str(i))
            path = root / 'manifest.json'; path.write_text(json.dumps(manifest))
            result = review_pack(path, root, root / 'review')
            self.assertEqual(0, result['labels_assigned'])
            self.assertIn('unreviewed', (root / 'review/review.csv').read_text())
            (root / '0.jpg').write_text('changed')
            with self.assertRaises(ValueError): review_pack(path, root, root / 'bad')
            self.assertFalse((root / 'bad').exists())

if __name__ == '__main__': unittest.main()
