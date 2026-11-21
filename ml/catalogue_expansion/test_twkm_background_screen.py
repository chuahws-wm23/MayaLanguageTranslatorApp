"""Synthetic unit tests for TWKM non-target sign screening logic.

These are geometry and split-safety checks, not archaeological accuracy tests.
"""
import unittest
import numpy as np

from twkm_background_screen import (
    background_sign_drawings, choose_cutoff, score
)


class WiderTwkmBackgroundTests(unittest.TestCase):
    def test_all_other_signs_exclude_known_and_calibration_and_test(self):
        bysign = {s: [{"sign": s, "sha": f"{s:05d}-x"}] for s in range(1, 90)}
        got = background_sign_drawings(
            bysign, chosen=[1, 2, 3],
            unknown_cal=[{"sign": 9}], unknown_test=[{"sign": 10}])
        self.assertEqual(84, len(got))
        self.assertTrue({1, 2, 3, 9, 10}.isdisjoint({r["sign"] for r in got}))

    def test_reference_screen_uses_both_similarity_and_margin(self):
        rule = {"similarity_min": .75, "margin_min": .04}
        guesses = [
            {"sign": 32, "similarity": .92, "margin": .08},
            {"sign": 44, "similarity": .73, "margin": .30},
            {"sign": 32, "similarity": .93, "margin": .01},
        ]
        known = score(guesses, [32, 44, 44], rule)
        self.assertEqual(1, known["correct_and_accepted"])
        self.assertEqual(2, known["known_abstained"])

    def test_binary_proposal_protects_correct_knowns_and_reduces_unknown(self):
        rule = {"similarity_min": .75, "margin_min": .03}
        val = [
            {"sign": 32, "similarity": .93, "margin": .10},
            {"sign": 44, "similarity": .90, "margin": .08},
            {"sign": 291, "similarity": .92, "margin": .07},
        ]
        unknown = [
            {"sign": 44, "similarity": .87, "margin": .06},
            {"sign": 32, "similarity": .85, "margin": .06},
        ]
        rule_choice = choose_cutoff(
            val, [32, 44, 291], unknown, rule,
            np.asarray([.8, .9, .7]), np.asarray([-.8, -.9]))
        self.assertIsNotNone(rule_choice)
        self.assertEqual(3, rule_choice["known_validation"]["correct_and_accepted"])
        self.assertEqual(0, rule_choice["unknown_calibration"]["unknown_wrongly_accepted"])

    def test_candidate_requires_reducing_wrong_unknown(self):
        rule = {"similarity_min": .8, "margin_min": .03}
        val = [{"sign": 32, "similarity": .9, "margin": .1}]
        unknown = [{"sign": 44, "similarity": .7, "margin": .1}]
        proposal = choose_cutoff(
            val, [32], unknown, rule,
            np.asarray([.6]), np.asarray([-.6]))
        self.assertIsNone(proposal)


if __name__ == "__main__":
    unittest.main()
