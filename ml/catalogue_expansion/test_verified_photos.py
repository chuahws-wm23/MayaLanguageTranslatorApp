"""Local verified-photo intake tests; synthetic shapes are NOT glyph accuracy data."""
import csv
import tempfile
import unittest
from pathlib import Path

from PIL import Image, ImageDraw

from verified_photos import load_verified_photos, split_for_training


class VerifiedPhotoIntakeTests(unittest.TestCase):
    def test_allows_25_sign_labels_and_separates_test_and_unknown(self):
        with tempfile.TemporaryDirectory() as location:
            root=Path(location)
            records=[]
            for i,(sign,split) in enumerate((("32","train"),("44","validation"),("UNKNOWN","test"))):
                picture=Image.new("L",(128,128),255)
                pen=ImageDraw.Draw(picture)
                pen.rectangle((15+i*7,20,90,105),outline=0,width=7)
                picture.save(root / f"{i}.png")
                records.append({
                    "image_path":f"{i}.png","twkm_sign":sign,
                    "source_group":f"source-artifact-{i}",
                    "source_url":"https://example.org/labelled-artifact",
                    "license":"CC BY 4.0","verified_by":"Verified human label",
                    "split":split,
                })
            manifest=root/"manifest.csv"
            with manifest.open("w",newline="",encoding="utf-8") as f:
                writer=csv.DictWriter(f,fieldnames=list(records[0]))
                writer.writeheader()
                writer.writerows(records)
            groups,audit=load_verified_photos(manifest,root,[32,44],set())
            train,ignored=split_for_training(groups)
            self.assertEqual(1,len(train))
            self.assertEqual(0,ignored)
            self.assertEqual(-1,groups["test"][0]["sign"])
            self.assertEqual(44,groups["validation"][0]["sign"])
            self.assertEqual(3,audit["num_photos"])

    def test_does_not_reuse_existing_twkm_source_bytes(self):
        import hashlib
        with tempfile.TemporaryDirectory() as location:
            root=Path(location)
            image=Image.new("L",(128,128),255)
            pen=ImageDraw.Draw(image)
            pen.line((12,12,108,110),fill=0,width=6)
            path=root/"image.png"
            image.save(path)
            manifest=root/"manifest.csv"
            manifest.write_text("image_path,twkm_sign,source_group,source_url,license,verified_by,split\n"+
                                "image.png,32,source-1,https://example.org/source,CC BY 4.0,Human verified,train\n",
                                encoding="utf-8")
            with self.assertRaisesRegex(ValueError,"exactly duplicates"):
                load_verified_photos(manifest,root,[32,44],
                                     {hashlib.sha256(path.read_bytes()).hexdigest()})


if __name__=="__main__":
    unittest.main()
