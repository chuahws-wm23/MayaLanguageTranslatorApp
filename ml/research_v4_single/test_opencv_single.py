#!/usr/bin/env python3
"""Fast CPU-only regression tests for the Python glyph image pipeline."""
import io
import sys
import unittest
from pathlib import Path
import numpy as np
from PIL import Image

sys.path.insert(0,str(Path("ml/research_v4_single").resolve()))
from opencv_single_glyph import (
    VARIANTS, UnreadableGlyph, canonical_glyph, decode_image, model_tensor,
    prepare_variant, top_candidates
)

class OpenCvPipelineTests(unittest.TestCase):
    def setUp(self):
        self.img=np.full((140,130),245,dtype=np.uint8)
        self.img[25:115,28:33]=8
        self.img[28:33,28:100]=8
        self.img[50:56,75:105]=8
        self.img[100:107,28:108]=8

    def test_all_modes_preserve_tensor_shape(self):
        for variant in VARIANTS:
            with self.subTest(variant=variant):
                result=canonical_glyph(prepare_variant(self.img,variant))
                self.assertEqual(result.shape,(64,64))
                v=model_tensor(result)
                self.assertEqual(v.shape,(3,160,160))
                self.assertTrue(np.isfinite(v).all())

    def test_dark_background_handling(self):
        self.assertEqual(canonical_glyph(self.img).shape,
                         canonical_glyph(255-self.img).shape)
        out=canonical_glyph(255-self.img)
        self.assertGreater(int(np.max(out)),150)

    def test_blank_and_tiny_images_are_rejected(self):
        with self.assertRaises(UnreadableGlyph):
            canonical_glyph(np.full((140,130),255,dtype=np.uint8))
        with self.assertRaises(UnreadableGlyph):
            prepare_variant(np.zeros((10,10),dtype=np.uint8),"clahe")

    def test_alpha_composited_to_white(self):
        img=Image.new("RGBA",(130,140),(0,0,0,0))
        for y in range(25,110):
            for x in range(28,34):img.putpixel((x,y),(5,5,5,255))
        buf=io.BytesIO();img.save(buf,format="PNG")
        gray=decode_image(buf.getvalue())
        self.assertEqual(gray.shape,(140,130))
        self.assertEqual(int(gray[0,0]),255)
        self.assertLess(int(gray[50,30]),15)

    def test_top_candidates_are_model_scores_not_guessed_labels(self):
        logits=np.zeros(10,dtype=np.float32);logits[4]=8
        ranked=top_candidates(logits)
        self.assertEqual(3,len(ranked))
        self.assertEqual(44,ranked[0]["twkm_sign"])
        self.assertGreater(ranked[0]["relative_score_percent"],0)

if __name__=="__main__":
    unittest.main()
