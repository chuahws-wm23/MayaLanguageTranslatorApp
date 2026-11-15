import sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path("ml/research_v4_single").resolve()))
from evaluate_heldout_photos import measure,wilson

class PhotoEvaluationGatesTest(unittest.TestCase):
    def test_small_test_does_not_imply_reliability(self):
        r=measure([44,187,"UNKNOWN"],[44,44,187],[True,True,False])
        self.assertEqual(1,r["known_correct_and_accepted"])
        self.assertEqual(1,r["known_wrongly_accepted"])
        self.assertEqual(1,r["unknown_rejected"])
        self.assertFalse(r["provisional_reliability_release_gate"])
    def test_unknown_false_accept_counted_even_with_high_model_score(self):
        r=measure(["UNKNOWN"]*3,[44,32,187],[True,True,True])
        self.assertEqual(3,r["unknown_wrongly_accepted"])
        self.assertEqual(0,r["unknown_rejected"])
        self.assertFalse(r["provisional_reliability_release_gate"])
    def test_rejection_counts_as_missing_known_identification(self):
        r=measure([44,44],[44,187],[False,True])
        self.assertEqual(0,r["known_correct_and_accepted"])
        self.assertEqual(1,r["known_wrongly_accepted"])
        self.assertEqual(1,r["known_rejected"])
    def test_wilson_interval_uncertainty(self):
        self.assertIsNone(wilson(0,0))
        lo,hi=wilson(0,100)
        self.assertEqual(0,lo)
        self.assertGreater(hi,.03)
        self.assertLess(hi,.05)
        lo,hi=wilson(90,100)
        self.assertLess(lo,.9)
        self.assertGreater(lo,.8)

if __name__=="__main__":unittest.main()
