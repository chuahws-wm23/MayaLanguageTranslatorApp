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
    def test_parser_recognizes_polygons_separately_from_boxes(self):
        kind, box = v18.parse_annotation("0 .1 .2 .8 .2 .8 .9 .1 .9")
        self.assertEqual(kind, "polygon_to_box")
        self.assertEqual(len(box.split()), 5)
        self.assertEqual(v18.parse_annotation("0 0.5 0.5 0.3 0.4")[0], "valid_box")
        self.assertEqual(v18.parse_annotation("1 0.5 0.5 0.3 0.4")[0], "unexpected_class")

    def test_diagnosis_describes_problem_without_training(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); self.make_dataset(root)
            p = root / "train" / "labels" / "train-source.rf.train123.txt"
            p.write_text("0 .1 .2 .8 .2 .8 .9 .1 .9\n", encoding="utf8")
            report = v18.preflight(root, diagnose=True)
            self.assertEqual(report["split"]["train"]["invalid_or_non_box_rows"], 1)
            self.assertEqual(report["split"]["train"]["label_formats_and_reasons"]["polygon_to_box"], 1)

    def test_creates_new_boxes_without_modifying_original(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "raw"; self.make_dataset(root)
            p = root / "train" / "labels" / "train-source.rf.train123.txt"
            p.write_text("0 .1 .2 .8 .2 .8 .9 .1 .9\n", encoding="utf8")
            target = Path(tmp) / "converted"
            v18.prepare_boxes(root, target)
            self.assertEqual(len((target / "train" / "labels" / p.name).read_text().split()), 5)
            self.assertEqual(len(p.read_text().split()), 9)
            self.assertEqual(v18.preflight(target)["split"]["train"]["invalid_or_non_box_rows"], 0)

    def test_rejects_unrepairable_unknown_class(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "raw"; self.make_dataset(root)
            p = root / "train" / "labels" / "train-source.rf.train123.txt"
            p.write_text("1 .5 .5 .2 .3\n", encoding="utf8")
            with self.assertRaisesRegex(ValueError, "Cannot safely convert"):
                v18.prepare_boxes(root, Path(tmp) / "converted")


    def make_dataset(self, root):
        root.mkdir(parents=True, exist_ok=True)
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
            self.assertEqual(audit["split"]["test"]["valid_detection_boxes"], 1)
    def test_rejects_wrong_class(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); self.make_dataset(root)
            (root / "test" / "labels" / "test-source.rf.test123.txt").write_text(
                "1 .5 .5 .2 .3\n", encoding="utf8")
            with self.assertRaisesRegex(ValueError, "unexpected_class"):
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
