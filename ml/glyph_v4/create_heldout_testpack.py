"""Package ten ORIGINAL TWKM drawings excluded from v4 training.
This is an exact replay of the published v4 held-out test, not a new benchmark.
"""
import csv,hashlib,io,json,zipfile
from pathlib import Path
from datasets import load_dataset,Image
from PIL import Image as PILImage

ROOT=Path('ml/glyph_v4')
OUT=ROOT/'heldout_tests'
OUT.mkdir(parents=True,exist_ok=True)
split=json.loads((ROOT/'outputs/split_manifest.json').read_text(encoding='utf-8'))
evaluation=json.loads((ROOT/'outputs/evaluation.json').read_text(encoding='utf-8'))
assert len(split['test'])==10 and len(set(int(r['sign']) for r in split['test']))==10
by_graph={r['graph']:r for r in split['test']}
outcomes={r['graph']:r for r in evaluation['test_predictions']}
sign_index={int(v):i for i,v in enumerate(split['supported_sign_numbers'])}
assert set(by_graph)==set(outcomes)

rows=load_dataset(split['source'],'graphs',split='train',
    revision=split['revision']).cast_column('image',Image(decode=False))
found={}
for row in rows:
    graph=str(row['graph_no'])
    if graph in by_graph: found[graph]=row
assert set(found)==set(by_graph),f'Missing graphs {set(by_graph)-set(found)}'
catalog=[]
for i,expected in enumerate(split['test'],1):
    graph=expected['graph']
    source=found[graph]
    assert int(source['sign_no'])==int(expected['sign'])
    img=source['image']
    assert img
    raw=img.get('bytes')
    if raw is None and img.get('path'):
        raw=Path(img['path']).read_bytes()
    assert raw
    assert hashlib.sha256(raw).hexdigest()==expected['sha256'],f'Source image differs for {graph}'
    with PILImage.open(io.BytesIO(raw)) as image:
        png=image.convert('RGB')
        name=f'{i:02d}_sign_{expected["sign"]}_graph_{graph}.png'
        png.save(OUT/name,format='PNG')
    item=outcomes[graph]
    catalog.append({
        'file':name,'expected_sign_number':expected['sign'],
        'catalog_reading':split['labels'][sign_index[int(expected['sign'])]],
        'original_graph_id':graph,
        'python_model_predicted_sign_number':item['predicted_sign'],
        'python_model_top1_correct':str(bool(item['correct'])).lower(),
        'twkm_reference_url':source.get('source_image_url') or '',
        'drawing_creator':source.get('drawing_creator') or 'Christian Prager',
        'drawing_license':source.get('drawing_license') or 'CC BY 4.0',
        'used_in_model_training':'NO',
        'used_in_model_selection_or_threshold_calibration':'NO',
        'role':'Previously evaluated v4 original-image held-out test',
    })

with (OUT/'EXPECTED_RESULTS.csv').open('w',encoding='utf-8',newline='') as file:
    writer=csv.DictWriter(file,fieldnames=list(catalog[0]))
    writer.writeheader()
    writer.writerows(catalog)
instructions="""10-SIGN RESEARCH V4: HELD-OUT TWKM DRAWINGS

1. Pull the fyp-development branch once the v4 integration is merged.
2. Open Photo translation > Maya glyph recognition.
3. Choose Research v4 (10 signs) (NOT v1, v2 or v3).
4. Gallery > select each numbered PNG.
5. Crop the complete glyph with a small margin, then Recognise glyph.
6. Compare the displayed TWKM sign no. with EXPECTED_RESULTS.csv.

IMPORTANT:
- These 10 originals were NOT used in v4 model training, validation, or rejection calibration.
- They WERE ALREADY USED in the v4 research report's final test (7/10 correct).
  Therefore repeating them on Android is a deployment regression/smoke test,
  NOT a new independent estimate of accuracy.
- Research v4 ALWAYS outputs one of ten categories for every accepted image,
  including unsupported drawings; its rejected-sign filter failed on known
  test drawings and was intentionally NOT enabled.
- The app does not translate a full inscription.
- The catalogue sign readings are provisional and context dependent.
- The source gallery is from TWKM (University of Bonn), standardized
  glyph drawings Christian Prager, CC BY 4.0.
- Real phone-camera test accuracy remains unknown.
"""
(OUT/'HOW_TO_TEST.txt').write_text(instructions,encoding='utf-8')
(OUT/'SOURCES.json').write_text(json.dumps(catalog,indent=2),encoding='utf-8')
file=OUT/'MayaGlyphV4_Heldout_TestPictures.zip'
with zipfile.ZipFile(file,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
    for child in sorted(OUT.iterdir()):
        if child.is_file() and child!=file: z.write(child,arcname=child.name)
with zipfile.ZipFile(file) as z: assert z.testzip() is None
print('Exported',len(catalog),'source-verified original test drawings,',file.stat().st_size,'ZIP bytes',flush=True)
