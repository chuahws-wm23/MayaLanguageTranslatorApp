"""Export fixed ten sign-disjoint unseen-sign TWKM pictures for Android rejection smoke tests.

Unknown sign IDs are taken from the predeclared evaluation held-out identity split;
no cherry-picking based on whether the model rejected them.
"""
import csv,hashlib,html,io,json,shutil,zipfile
from pathlib import Path
from datasets import Image,load_dataset
from PIL import Image as PILImage

HERE=Path("ml/glyph_v3")
RESULT=json.loads((HERE/"outputs/evaluation.json").read_text())
NEGATIVE_SIGNS=RESULT["negative_signs_used_only_for_testing"][:10]
assert len(NEGATIVE_SIGNS)==10
DATASET=RESULT["source"]
REV=RESULT["revision"]
OUT=HERE/"test_pictures"
OUT.mkdir(parents=True,exist_ok=True)
graphs=load_dataset(DATASET,"graphs",revision=REV,split="train").cast_column("image",Image(decode=False))
selected={}
for r in graphs:
    sign=r.get("sign_no")
    if sign not in NEGATIVE_SIGNS or not r.get("image"):continue
    if sign not in selected or str(r["graph_no"])<str(selected[sign]["graph_no"]):
        selected[sign]=r
assert len(selected)==10,selected.keys()
manifest=[]
for index,s in enumerate(NEGATIVE_SIGNS,1):
    row=selected[s]
    img=row["image"]
    raw=img.get("bytes")
    if raw is None and img.get("path"):raw=Path(img["path"]).read_bytes()
    assert raw
    im=PILImage.open(io.BytesIO(raw)).convert("RGB")
    name=f"unknown_{index:02d}_sign_{s}_graph_{row['graph_no']}.png"
    im.save(OUT/name)
    manifest.append({
       "filename":name,"group":"UNSUPPORTED_SIGN_TEST","twkm_sign_no":s,
       "twkm_graph_no":row["graph_no"],"expected":"No reliable supported match",
       "source_image_url":row.get("source_image_url") or "",
       "note":"Sign identity was excluded from BOTH supported model training and threshold calibration",
    })
source=Path("ml/glyph_v2/test_pictures")
known=[
    (32,"K'UH","01_32mt_KUH.png"),
    (184,"K'INICH","02_184vb_KINICH.png"),
    (291,"SIIP","03_291va_SIIP.png"),
    (4,"NAAH","04_4va_NAAH.png"),
    (44,"TOK","05_44bb_TOK.png"),
]
for sign,reading,filename in known:
    name=source/filename
    assert name.is_file(), name
    dest=OUT/name.name
    shutil.copyfile(name,dest)
    manifest.append({
       "filename":name.name,"group":"KNOWN_SIGN_SMOKE_ONLY",
       "twkm_sign_no":sign,"twkm_graph_no":name.name.split("_")[1],
       "expected":reading,
       "source_image_url":"",
       "note":"The original was in model training; NOT an independent positive accuracy test",
    })
with (OUT/"EXPECTED_RESULTS.csv").open("w",newline="",encoding="utf-8") as f:
    w=csv.DictWriter(f,fieldnames=list(manifest[0].keys()))
    w.writeheader()
    w.writerows(manifest)
instructions="""RESEARCH V3 — OPEN-SET PHONE TEST PACK

No change to Android code is needed after pulling the v3 integration.

Select Research v3 (screened) from the app's Photo translation screen.
Pick each PNG from Gallery, crop just the glyph, and tap Recognise glyph.

- The FIVE 'known' images are reused source drawings from the model's training
  corpus; these are ONLY a software smoke test and cannot demonstrate accuracy
  on genuinely new handwriting or camera photographs.
- The TEN 'unknown' images use TWKM sign identities completely outside the five
  supported model classes and also excluded from rejection-threshold calibration.
  Expect 'No reliable supported match'. If a result is suggested, record it as
  an UNKNOWN FALSE ACCEPT rather than an English translation.
- Original drawings, without text overlay, by Christian Prager, TWKM, University
  of Bonn. Licensed CC BY 4.0; relevant catalogue URLs/IDs are in the CSV.
- These are all catalogue-style drawings, NOT a separately sourced dataset.
- Never interpret the 92.1% old five-class cross-validation or the 63.2% v3
  known acceptance as actual phone-camera accuracy.

Record predicted sign / acceptance / failures separately and upload
a completed copy of EXPECTED_RESULTS.csv if you want those tests analysed.
"""
(OUT/"HOW_TO_TEST.txt").write_text(instructions)
cards=[]
for item in manifest:
    cards.append(f"<article><img src='{html.escape(item['filename'])}' alt='Maya test glyph'>"
        f"<b>{html.escape(str(item['twkm_sign_no']))}</b> — {html.escape(item['group'])}<br>"
        f"<span>{html.escape(item['expected'])}</span></article>")
(OUT/"GALLERY.html").write_text("<!doctype html><html><meta charset='utf-8'><title>Research v3 test drawings</title>"
"<style>body{font:16px system-ui;margin:2rem auto;max-width:1000px;padding:16px}"
"main{display:grid;grid-template-columns:repeat(auto-fill,minmax(175px,1fr));gap:12px}"
"article{border:1px solid #bbb;padding:12px;border-radius:12px}"
"img{width:100%;height:145px;object-fit:contain}</style><h2>Supported and unknown glyph test cases</h2>"
"<p>Training originals for supported signs; sign-disjoint TWKM originals for unknowns. "
"No independent photo accuracy claims.</p><main>"+''.join(cards)+"</main></html>")
dest=OUT/"MayaGlyphV3_PhoneTestPack.zip"
with zipfile.ZipFile(dest,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
    for f in OUT.iterdir():
        if f.is_file() and f!=dest:z.write(f,f.name)
with zipfile.ZipFile(dest) as z:assert z.testzip() is None
print("CREATED",dest,"bytes",dest.stat().st_size,"samples",len(manifest),flush=True)
