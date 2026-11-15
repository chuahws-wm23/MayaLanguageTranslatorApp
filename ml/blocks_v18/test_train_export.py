#!/usr/bin/env python3
"""Tests for the v18 dataset preflight without requiring real uploaded photos."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("v18_training", Path(__file__).with_name("train_export.py"))
v18 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v18)


class PreflightTests(unittest.TestCase):
    def make_dataset(self, root):
        (root / "data.yaml").write_text("names: ['glyph-blocks']\nnc: 1\n", encoding="utf8")
        for split in ("train", "valid", "test"):
            (root / split / "images").mkdir(parents=True)
            (root / split / "labels").mkdir()
            stem = f"{split}-source.rf.{split}123"
            (root / split / "images" / (stem + ".jpg")).write_bytes(split.encode())
            (root / split / "labels" / (stem + ".txt")).write_text(
                "0 0.5 0.5 0.2 0.3\n", encoding="utf8"
            )
    def test_good_source_disjoint_yolo_dataset(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); self.make_dataset(root)
            audit = v18.preflight(root)
            self.assertEqual(audit["split"]["train"]["images"], 1)
            self.assertEqual(audit["split"]["test"]["boxes"], 1)
    def test_rejects_wrong_class(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); self.make_dataset(root)
            (root / "test" / "labels" / "test-source.rf.test123.txt").write_text(
                "1 .5 .5 .2 .3\n", encoding="utf8")
            with self.assertRaisesRegex(ValueError, "invalid YOLO rows"):
                v18.preflight(root)
    def test_rejects_same_source_cross_split(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); self.make_dataset(root)
            (root / "test" / "images" / "test-source.rf.test123.jpg").rename(
                root / "test" / "images" / "train-source.rf.extra.jpg")
            (root / "test" / "labels" / "test-source.rf.test123.txt").rename(
                root / "test" / "labels" / "train-source.rf.extra.txt")
            with self.assertRaisesRegex(ValueError, "Data leakage"):
                v18.preflight(root)


if __name__ == "__main__":
    unittest.main()
