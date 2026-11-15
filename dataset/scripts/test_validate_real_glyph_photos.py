#!/usr/bin/env python3
"""Synthetic fixtures validate splitting/provenance, NOT model accuracy."""
import csv,sys,tempfile,unittest
from pathlib import Path
from PIL import Image,ImageDraw

sys.path.insert(0,str(Path("dataset/scripts").resolve()))
from validate_real_glyph_photos import audit,COLUMNS

class RealPhotoManifestTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)
        for i in range(3):
            im=Image.new("L",(128,128),255)
            d=ImageDraw.Draw(im);d.rectangle((18+i,22,90,108),outline=0,width=6)
            im.save(self.root/f"{i}.png")
    def manifest(self,rows):
        path=self.root/"manifest.csv"
        with path.open("w",newline="",encoding="utf-8") as f:
            w=csv.DictWriter(f,fieldnames=COLUMNS)
            w.writeheader();w.writerows(rows)
        return path
    def rec(self,i,sign="44",group=None,split="train"):
        return {"image_path":f"{i}.png","twkm_sign":sign,
            "source_group":group or f"artifact-{i}",
            "source_url":"https://example.org/source","license":"CC BY 4.0",
            "verified_by":"Human expert check","split":split}
    def test_valid_manifest_still_not_enough_for_reliability(self):
        report=audit(self.manifest([self.rec(0),self.rec(1,"187")]),self.root)
        self.assertEqual([],report["errors"])
        self.assertFalse(report["ready_for_independent_evaluation"])
    def test_source_group_cross_split_is_fatal(self):
        report=audit(self.manifest([self.rec(0,group="same-artifact"),
            self.rec(1,group="same-artifact",split="test")]),self.root)
        self.assertTrue(any("source_group" in s for s in report["errors"]))
    def test_identical_pixel_files_in_different_splits_fail(self):
        report=audit(self.manifest([self.rec(0),
            self.rec(0,group="another",split="test")]),self.root)
        self.assertTrue(any("identical image SHA" in s for s in report["errors"]))
    def test_path_escape_and_invalid_sign_fail(self):
        manifest=self.manifest([self.rec(0,sign="999"),self.rec(1)])
        report=audit(manifest,self.root)
        self.assertTrue(any("unsupported or unverified" in s for s in report["errors"]))
        vals=[self.rec(0)];vals[0]["image_path"]="../private.png"
        report=audit(self.manifest(vals),self.root)
        self.assertTrue(any("escapes" in s for s in report["errors"]))
    def test_missing_verification_metadata_fails(self):
        item=self.rec(0);item["verified_by"]=""
        report=audit(self.manifest([item]),self.root)
        self.assertTrue(any("verified_by" in s for s in report["errors"]))

if __name__=="__main__":unittest.main()
