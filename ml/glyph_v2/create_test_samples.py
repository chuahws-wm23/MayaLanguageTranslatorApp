"""Export five individual TWKM reference drawings for a basic Android smoke test.

These are originals from the catalogue used in model v2 development.
They are NOT independent, held-out evaluation examples for the final fitted model.
"""
import hashlib, html, io, json, os, zipfile
from pathlib import Path
from datasets import load_dataset, Image
from PIL import Image as PILImage

REV="73fe9da1d975a5f1e588c7d42c952dfda53cefd0"
DATASET="tadad/twkm-maya-glyphs"
SELECTION=[
    ("32mt",32,"K'UH","God or deity"),
    ("184vb",184,"K'INICH","Resplendent or sunny"),
    ("291va",291,"SIIP","Deity name"),
    ("4va",4,"NAAH","First or house"),
    ("44bb",44,"TOK","Cloud or mist"),
]
OUTPUT=Path("ml/glyph_v2/test_pictures")
OUTPUT.mkdir(exist_ok=True, parents=True)
print("Fetching pinned TWKM drawings", flush=True)
rows=load_dataset(DATASET,"graphs",split="train",revision=REV,
    cache_dir=os.environ.get("HF_DATASETS_CACHE","/tmp/twkm-test-cache"))
found={}
wanted={x[0] for x in SELECTION}
for row in rows:
    if str(row.get("graph_no")) in wanted:
        found[str(row["graph_no"])]=row
assert set(found)==wanted, f"Missing requested TWKM variants: {wanted - set(found)}"
manifest=[]
for idx,(graph,sign,reading,gloss) in enumerate(SELECTION,1):
    row=found[graph]
    assert int(row["sign_no"])==sign, f"Catalogue sign mismatch for {graph}"
    img=row["image"]
    assert img is not None
    if isinstance(img,PILImage.Image):
        im=img.copy()
    elif isinstance(img,dict):
        raw=img.get("bytes") or (Path(img["path"]).read_bytes() if img.get("path") else None)
        assert raw
        im=PILImage.open(io.BytesIO(raw)).copy()
    else: raise ValueError(f"Unexpected embedded image type {type(img)}")
    assert im.width>50 and im.height>50
    # PNG output retains source pixel content; no synthetic distortion, no captions on photos.
    name=f"{idx:02d}_{graph}_{reading.replace(chr(39),'')}.png"
    dest=OUTPUT/name
    im.save(dest,format="PNG")
    manifest.append({
        "file":name,"graph_no":graph,"sign_no":sign,
        "reading":reading,"proposed_english_gloss":gloss,
        "source_image_url":row.get("source_image_url"),
        "source_image_id":row.get("source_image_id"),
        "drawing_creator":row.get("drawing_creator") or "Christian Prager",
        "drawing_license":row.get("drawing_license") or "CC BY 4.0",
        "source_dataset":DATASET,"revision":REV,
        "sha256_of_exported_png":hashlib.sha256(dest.read_bytes()).hexdigest(),
        "image_size":[im.width,im.height],
        "role":"Training-catalogue reference; smoke-test only, NOT new unseen accuracy test"
    })
    print("Exported",name,im.size,flush=True)

(OUTPUT/"SOURCES.json").write_text(json.dumps(manifest,indent=2),encoding="utf-8")
instructions="""MAYA GLYPH MODEL v2 — FIVE EXAMPLE PHOTOS

Open Android Studio and run the fyp-development branch.

1. Open Photo translation > Maya glyph recognition.
2. Select Research v2. Do NOT use Model v1 for these five examples.
3. Select Choose an image > pick ONE PNG from this folder.
4. Keep the full single glyph in the crop (remove any extra border if needed).
5. Tap Recognise glyph.
6. Compare the displayed proposed reading to the filename and SOURCES.json.

01 -> K'UH (God / deity), sign 32
02 -> K'INICH (Resplendent / sunny), sign 184
03 -> SIIP (Deity name), sign 291
04 -> NAAH (First / house), sign 4
05 -> TOK (Cloud / mist), sign 44

These are standard TWKM catalogue reference drawings. The fitted model
has seen these original drawings during training; positive results
show that the software pipeline can run, not that the model generalizes.
The measured 35/38 = 92.1% was an exploratory original-image
five-fold cross-validation within this same catalogue, NOT phone-photo
accuracy or an independently held-out final score.

This model always chooses one of these five signs, EVEN for an
unknown image. A suggested English gloss is not a verified translation.
Deer (CHIJ) and Cacao (KAKAW) are NOT supported by v2.

SOURCE: University of Bonn / TWKM Text Database and Dictionary of Classic Mayan.
Drawings: Christian Prager. Drawing license: CC BY 4.0, with
per-file attribution and the originating image URL in SOURCES.json.
Dataset packaging: tadad/twkm-maya-glyphs, pinned snapshot 2026-09-04.
"""
(OUTPUT/"HOW_TO_TEST.txt").write_text(instructions,encoding="utf-8")

cards=[]
for row in manifest:
    cards.append(f'<article><img src="{html.escape(row["file"])}" alt="TWKM sign {row["sign_no"]}">'
                 f'<h3>{html.escape(row["reading"])}</h3>'
                 f'<p>{html.escape(row["proposed_english_gloss"])} · Graph {html.escape(row["graph_no"])}</p>'
                 f'<a href="{html.escape(row["file"])}" download>Save individual image</a></article>')
gallery=('''<!doctype html><html lang="en"><meta charset="utf-8"><title>Maya glyph v2 test pictures</title>
<style>body{font:16px/1.4 system-ui;max-width:1100px;margin:2rem auto;padding:1rem;background:#f7f7fa}
main{display:grid;grid-template-columns:repeat(auto-fit,minmax(210px,1fr));gap:16px}
article{background:white;border:1px solid #ddd;border-radius:12px;padding:16px}
img{width:100%;height:190px;object-fit:contain;background:white} h3{margin:.4rem 0}
p{color:#555}a{color:#314de4}</style><h1>Five real TWKM reference drawings</h1>
<p>Research v2 ONLY. These are training-catalogue reference images for a smoke test,
not independent examples that prove recognition accuracy.</p><main>'''+''.join(cards)+'</main>'
'<footer>Christian Prager, TWKM / University of Bonn, CC BY 4.0. See SOURCES.json.</footer></html>')
(OUTPUT/"GALLERY.html").write_text(gallery,encoding="utf-8")
bundle=OUTPUT/"MayaGlyphV2_Test_Pictures.zip"
with zipfile.ZipFile(bundle,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
    for item in OUTPUT.iterdir():
        if item!=bundle and item.is_file():z.write(item,item.name)
with zipfile.ZipFile(bundle) as z:assert z.testzip() is None
print("PACKAGE",bundle,bundle.stat().st_size,flush=True)
