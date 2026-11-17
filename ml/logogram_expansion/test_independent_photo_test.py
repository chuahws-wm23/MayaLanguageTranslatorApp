"""Synthetic workflow fixtures only; no glyph/photo-accuracy claims."""
import csv
import tempfile
import unittest
from pathlib import Path
from PIL import Image, ImageDraw
from prepare_independent_photo_test import prepare
from summarize_android_photo_test import summarize


class IndependentPhotoWorkflowTest(unittest.TestCase):
    def test_staging_never_copies_training_images_or_overwrites_previous_batch(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            rows = []
            for i, split in enumerate(['train', 'test']):
                image = Image.new('L', (128, 128), 255)
                ImageDraw.Draw(image).line((20, 20, 80 + i*10, 100), fill=0, width=8)
                image.save(root/f'{i}.png')
                rows.append(dict(image_path=f'{i}.png', twkm_sign=('44' if split == 'train' else '32'), source_group=f'original-{i}',
                    source_url='https://example.org/fixture', license='test fixture only',
                    verified_by='fixture', split=split))
            manifest = root/'manifest.csv'
            with manifest.open('w', newline='') as f:
                writer = csv.DictWriter(f, fieldnames=list(rows[0]))
                writer.writeheader(); writer.writerows(rows)
            dest = root/'staged'
            result = prepare(manifest, root, dest)
            self.assertEqual(1, len(result['samples']))
            self.assertEqual('32', result['samples'][0]['expected_sign'])
            self.assertEqual(1, len(list(dest.glob('*.png'))))
            self.assertFalse(result['audit']['ready_for_independent_evaluation'])
            with self.assertRaises(ValueError):
                prepare(manifest, root, dest)

    def test_empty_and_failed_known_queries_count_as_retrieval_failures(self):
        report = {'format_version': 1, 'source_manifest_sha256': 'fixture', 'predictions': [
            {'expected_sign': '32', 'source_group': 'a', 'confirmed_identification': False,
             'candidates': [{'sign': 44}, {'sign': 32}]},
            {'expected_sign': '32', 'source_group': 'b', 'confirmed_identification': False,
             'candidates': [], 'error': 'decode'},
            {'expected_sign': 'UNKNOWN', 'source_group': 'c', 'confirmed_identification': False,
             'candidates': [{'sign': 32}]}]}
        stats = summarize(report)
        self.assertEqual(0, stats['known_top1'])
        self.assertEqual(.5, stats['known_top5'])
        self.assertEqual(.25, stats['known_mrr_at_5'])
        self.assertEqual(1, stats['error_photos'])
        self.assertIsNone(stats['unknown_rejection_accuracy'])
        self.assertFalse(stats['release_approved'])

    def test_no_photos_cannot_be_reported_as_zero_or_perfect_accuracy(self):
        with self.assertRaises(ValueError):
            summarize({'format_version': 1, 'predictions': []})

if __name__ == '__main__':
    unittest.main()
