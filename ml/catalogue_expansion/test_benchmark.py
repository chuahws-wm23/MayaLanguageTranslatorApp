"""Unit checks for the TWKM sign-expansion pilot (no network needed)."""
import io
import unittest

import numpy as np
from PIL import Image, ImageDraw

from ml.catalogue_expansion import benchmark as b


class ExpansionTests(unittest.TestCase):
    def test_normalize_isolated_dark_strokes(self):
        image = Image.new("RGB", (120, 80), "white")
        pen = ImageDraw.Draw(image)
        pen.line([(24, 20), (100, 20), (70, 60)], fill="black", width=5)
        buffer = io.BytesIO()
        image.save(buffer, format="PNG")
        canonical = b.canonical(buffer.getvalue())
        self.assertEqual(canonical.shape, (64, 64))
        self.assertGreater(np.count_nonzero(canonical), 30)

    def test_bad_blank_sign_is_rejected(self):
        image = Image.new("RGB", (80, 80), "white")
        buffer = io.BytesIO()
        image.save(buffer, format="PNG")
        with self.assertRaises(ValueError):
            b.canonical(buffer.getvalue())

    def test_nearest_uses_distinct_signs(self):
        ty = np.array([32, 32, 44], dtype=np.int64)
        train_p = np.asarray([[1., 0.], [1., 0.], [0., 1.]], np.float32)
        train_h = train_p.copy()
        q = np.asarray([[0., 1.]], np.float32)
        got = b.nearest(ty, train_p, train_h, q, q, "hybrid")
        self.assertEqual(got[0]["sign"], 44)
        self.assertGreater(got[0]["margin"], .5)

    def test_experiment_signs_and_unknown_ids_do_not_overlap(self):
        bysign = {
            s: [{"sign": s, "graph": str(s) + chr(97 + i),
                 "sha": "%05d-%d" % (s, i), "image": np.ones((64, 64), np.uint8),
                 "translation": ""} for i in range(5)]
            for s in range(1, 91)
        }
        chosen, tr, va, te, unknown_cal, unknown_test = b.split(bysign, 5, 20)
        self.assertEqual(len(chosen), 20)
        self.assertEqual(len(va), 20)
        self.assertEqual(len(te), 20)
        self.assertEqual(len(unknown_cal), 40)
        self.assertEqual(len(unknown_test), 30)
        self.assertTrue(set(chosen).isdisjoint({r["sign"] for r in unknown_cal + unknown_test}))
        all_images = tr + va + te + unknown_cal + unknown_test
        self.assertEqual(len(all_images), len({r["sha"] for r in all_images}))


if __name__ == "__main__":
    unittest.main()
