import importlib.util
from pathlib import Path
import tempfile
import unittest

spec=importlib.util.spec_from_file_location("block_preview_compare",
     Path(__file__).with_name("preview_detector.py"))
m=importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class ComparisonGeometryTests(unittest.TestCase):
    def test_research_gallery_can_score_more_than_48_boxes(self):
        """No artificial cap when comparing against dense, annotated panels."""
        boxes = []
        for i in range(75):
            x, y = (i % 15) * 30, (i // 15) * 30
            boxes.append((x,y,x+20,y+20,.95 - i*.0001))
        full = m.suppress(boxes,.45)
        capped = m.suppress(boxes,.45,limit=48)
        self.assertEqual(len(full),75)
        self.assertEqual(len(capped),48)
        self.assertEqual(m.count_matches(full,boxes), {"tp":75,"fp":0,"fn":0})
        self.assertEqual(m.count_matches(capped,boxes), {"tp":48,"fp":0,"fn":27})

    def test_rejects_unbounded_nms_limit(self):
        with self.assertRaisesRegex(ValueError,"maximum block"):
            m.suppress([],limit=301)

    def test_nms_keeps_distinct_blocks_and_best_duplicate(self):
        boxes=[
            (10,10,80,80,.80),
            (12,12,82,82,.90),
            (120,10,180,80,.60),
        ]
        selected=m.suppress(boxes,.45)
        self.assertEqual(len(selected),2)
        self.assertEqual(selected[0][4],.9)
        self.assertEqual(selected[1][0],120)

    def test_tile_windows_cover_full_width(self):
        starts=m.tile_starts(1408,512,.25)
        self.assertEqual(starts[0],0)
        self.assertEqual(starts[-1],896)
        self.assertTrue(all(b-a<=512 for a,b in zip(starts,starts[1:])))

    def test_rotation_mode_is_not_supported(self):
        class Image:
            shape = (100, 200, 3)
        with self.assertRaisesRegex(ValueError, "non-rotated"):
            m.candidate_boxes(None, Image(), "rotated", .35, "cpu")

    def test_count_tp_fp_fn_and_read_labels(self):
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)/"test.txt"
            path.write_text("0 .5 .5 .4 .4\n0 .15 .15 .2 .2\n",encoding="utf8")
            truth=m.read_yolo_boxes(path,100,100)
            guesses=[(30,30,70,70,.9),(31,31,69,69,.8),(1,75,20,95,.7)]
            self.assertEqual(m.count_matches(guesses,truth),
                             {"tp":1,"fp":2,"fn":1})

    def test_wrong_label_type_is_not_silently_accepted(self):
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)/"test.txt"
            path.write_text("0 .1 .2 .4 .6 .8 .9\n")
            with self.assertRaisesRegex(ValueError,"five-number"):
                m.read_yolo_boxes(path,200,100)


if __name__=="__main__":
    unittest.main()
