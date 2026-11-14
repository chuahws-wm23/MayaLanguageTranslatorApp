import json, collections, os
from datasets import load_dataset, Image
REPO='tadad/twkm-maya-glyphs'
REV='73fe9da1d975a5f1e588c7d42c952dfda53cefd0'
kwargs=dict(split='train',revision=REV,cache_dir=os.environ.get('HF_DATASETS_CACHE','/tmp/maya-hf-cache'))
print('Loading TWKM pinned snapshot',flush=True)
graphs=load_dataset(REPO,'graphs',**kwargs).cast_column('image',Image(decode=False))
signs=load_dataset(REPO,'signs',**kwargs)
decipherments=load_dataset(REPO,'decipherments',**kwargs)
print('graphs=',len(graphs),'signs=',len(signs),'decipherments=',len(decipherments),flush=True)
print('graph-fields:',graphs.column_names,flush=True)
print('sign-fields:',signs.column_names,flush=True)
print('decipherment-fields:',decipherments.column_names,flush=True)
by_sign=collections.defaultdict(dict)
translations=collections.defaultdict(collections.Counter)
for r in graphs:
 im=r.get('image')
 if not im:continue
 n=str(r.get('sign_no'))
 sha=str(r.get('image_sha256') or r.get('source_image_id') or r.get('graph_no'))
 by_sign[n][sha]=r
 if r.get('translation'):translations[n][str(r['translation'])]+=1
indexed={str(x.get('sign_no')):x for x in signs}
readings=collections.defaultdict(list)
for x in decipherments:
 n=str(x.get('sign_no'))
 readings[n].append({k:x.get(k) for k in ['reading','reading_value','value','type','confidence_level','sign_reference_resolved'] if k in x})
top=[]
for n,records in sorted(by_sign.items(),key=lambda x:(-len(x[1]),x[0])):
 sr=indexed.get(n,{})
 top.append({'sign_no':n,'distinct_graph_images':len(records), 'examples':[z.get('graph_no') for z in list(records.values())[:12]],'graph_translations':translations[n].most_common(3),'sign_translation':sr.get('translation'), 'readings':readings[n][:5]})
summary={'repo':REPO,'revision':REV,'graph_count':len(graphs),'sign_count':len(signs), 'signs_with_images':len(by_sign),'counts':{str(k):sum(len(x)>=k for x in by_sign.values()) for k in [2,3,4,5,6,8,10]}, 'top_signs':top[:160]}
out='ml/glyph_v2/audit.json';os.makedirs(os.path.dirname(out),exist_ok=True)
with open(out,'w') as f:json.dump(summary,f,indent=2)
print('Top 25:',json.dumps(top[:25],indent=2)[:18000],flush=True)
