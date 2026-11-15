import importlib.util
from pathlib import Path
import unittest

import cv2
import numpy as np

MODULE = Path(__file__).resolve().parent / "reference_matcher.py"
spec = importlib.util.spec_from_file_location("reference_matcher", MODULE)
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class MatcherTests(unittest.TestCase):
    def glyph(self, shape):
        image = np.full((100, 100), 255, dtype=np.uint8)
        if shape == "ring":
            cv2.circle(image, (50, 50), 35, 0, 7)
        if shape == "cross":
            cv2.line(image, (15, 50), (85, 50), 0, 7)
            cv2.line(image, (50, 15), (50, 85), 0, 7)
        return m.canonical(image)

    def test_exact_reference_returns_class_code_only(self):
        ring, cross = self.glyph("ring"), self.glyph("cross")
        bank = m.Matcher(np.array(["CODE1", "CODE2"]), np.stack([ring, cross]))
        result = bank.query(ring, minimum_similarity=.97, minimum_margin=.05)
        self.assertEqual(result["matched_glyph_code"], "CODE1")
        self.assertNotIn("twkm_sign", result)
        self.assertGreaterEqual(result["nearest_similarity_not_accuracy"], .99)

    def test_very_high_threshold_abstains_on_different_glyph(self):
        ring, cross = self.glyph("ring"), self.glyph("cross")
        bank = m.Matcher(np.array(["A", "B"]), np.stack([ring, cross]))
        image = np.full((100, 100), 255, dtype=np.uint8)
        cv2.rectangle(image, (20, 30), (70, 75), 0, 6)
        result = bank.query(m.canonical(image), minimum_similarity=.999,
                            minimum_margin=.1)
        self.assertEqual(result["status"], "no_reliable_match")
        self.assertIsNone(result["matched_glyph_code"])

    def test_blank_image_abstains(self):
        with self.assertRaises(m.GlyphError):
            m.canonical(np.full((80, 80), 255, dtype=np.uint8))


if __name__ == "__main__":
    unittest.main()
