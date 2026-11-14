"""Train a compact HOG image classifier on separate original TWKM drawings.
Fixed five sign classes were selected using the sign-variant audit BEFORE testing.
Not an estimate of phone-photo recognition or all Maya glyph accuracy.
"""
import collections,hashlib,io,json,os,random,time
from pathlib import Path
import cv2,joblib,numpy as np
from datasets import Image,load_dataset
from PIL import Image as PILImage
from sklearn.metrics import accuracy_score,confusion_matrix,classification_report
from sklearn.svm import SVC
from sklearn.neighbors import KNeighborsClassifier
from skimage.feature import hog

REPO='tadad/twkm-maya-glyphs'
REV='73fe9da1d975a5f1e588c7d42c952dfda53cefd0'
SIGNS=[32,184,291,4,44]
LABELS=["K'UH | god / deity","K'INICH | resplendent","SIIP | deity name","NAAH | first / house","TOK | cloud / mist"]
SEED=20261114
SIZE=64
BASE=Path('ml/glyph_v2/outputs')

def process(image):
    """Shape normalization: line drawings become bright strokes on dark, 64x64."""
    raw=image.get('bytes')
    if raw is None and image.get('path'):raw=Path(image['path']).read_bytes()
    with PILImage.open(io.BytesIO(raw)) as pil:
        rgb=np.array(pil.convert('RGB'))
    gray=cv2.cvtColor(rgb,cv2.COLOR_RGB2GRAY)
    if min(gray.shape)<16:raise ValueError('Too small')
    th,_=cv2.threshold(gray,0,255,cv2.THRESH_BINARY+cv2.THRESH_OTSU)
    border=np.r_[gray[0,:],gray[-1,:],gray[:,0],gray[:,-1]]
    if np.median(border)<110:
        gray=255-gray
        th,_=cv2.threshold(gray,0,255,cv2.THRESH_BINARY+cv2.THRESH_OTSU)
    fg=np.where(gray<th,255,0).astype('uint8')
    if np.count_nonzero(fg)<30:raise ValueError('Empty glyph')
    ys,xs=np.nonzero(fg)
    x0,x1,y0,y1=xs.min(),xs.max()+1,ys.min(),ys.max()+1
    pad=max(2,int(max(x1-x0,y1-y0)*.035))
    x0,x1=max(0,x0-pad),min(gray.shape[1],x1+pad)
    y0,y1=max(0,y0-pad),min(gray.shape[0],y1+pad)
    crop=fg[y0:y1,x0:x1]
    s=(SIZE-8)/max(crop.shape)
    nw,nh=max(1,round(crop.shape[1]*s)),max(1,round(crop.shape[0]*s))
    small=cv2.resize(crop,(nw,nh),interpolation=cv2.INTER_AREA)
    out=np.zeros((SIZE,SIZE),dtype='uint8')
    oy,ox=(SIZE-nh)//2,(SIZE-nw)//2
    out[oy:oy+nh,ox:ox+nw]=small
    return out

def augment(im,rng):
    M=cv2.getRotationMatrix2D((SIZE/2,SIZE/2),float(rng.uniform(-13,13)),float(rng.uniform(.9,1.1)))
    M[0,2]+=float(rng.uniform(-4,4));M[1,2]+=float(rng.uniform(-4,4))
    out=cv2.warpAffine(im,M,(SIZE,SIZE),borderMode=cv2.BORDER_CONSTANT,borderValue=0)
    morph=int(rng.integers(0,5))
    if morph==1:out=cv2.dilate(out,np.ones((2,2),np.uint8),iterations=1)
    if morph==2:out=cv2.erode(out,np.ones((2,2),np.uint8),iterations=1)
    return out

def features(images):
    return np.asarray([hog(im.astype('float32')/255,orientations=9,
        pixels_per_cell=(8,8),cells_per_block=(2,2),block_norm='L2-Hys',feature_vector=True)
        for im in images],dtype=np.float32)

def get_rows():
    print('Loading pinned TWKM catalogue',flush=True)
    graphs=load_dataset(REPO,'graphs',split='train',revision=REV,
        cache_dir=os.environ.get('HF_DATASETS_CACHE','/tmp/maya-train-cache')).cast_column('image',Image(decode=False))
    by_sha=collections.defaultdict(list)
    for r in graphs:
        if not r.get('image'):continue
        raw=r['image'].get('bytes')
        if raw is None and r['image'].get('path'):raw=Path(r['image']['path']).read_bytes()
        if not raw:continue
        sha=hashlib.sha256(raw).hexdigest()
        by_sha[sha].append(r)
    by_sign=collections.defaultdict(list);conflicts=0
    for sha,group in by_sha.items():
        if len({str(x.get('sign_no')) for x in group})>1:
            conflicts+=1;continue
        r=group[0]
        if int(r['sign_no']) not in SIGNS:continue
        try:img=process(r['image'])
        except Exception as e:
            print('Skipped',r['graph_no'],str(e)[:80]);continue
        by_sign[int(r['sign_no'])].append({'sign':int(r['sign_no']),
          'graph':str(r['graph_no']),'sha256':sha,'im':img})
    for sign in SIGNS:
        assert len(by_sign[sign])>=5, 'Insufficient distinct images: '+str(sign)
    return by_sign,conflicts

def split_data(by_sign):
    rng=random.Random(SEED)
    splits={'train':[],'val':[],'test':[]}
    for sign in SIGNS:
        group=sorted(by_sign[sign],key=lambda x:x['sha256'])
        rng.shuffle(group)
        ntest=max(1,round(len(group)*.22));nval=1
        assert len(group)-nval-ntest>=3
        splits['test'].extend(group[:ntest])
        splits['val'].append(group[ntest])
        splits['train'].extend(group[ntest+nval:])
    return splits

def main():
    start=time.time()
    np.random.seed(SEED)
    by_sign,conflicts=get_rows()
    splits=split_data(by_sign)
    BASE.mkdir(parents=True,exist_ok=True)
    counts={str(s):{name:sum(r['sign']==s for r in group) for name,group in splits.items()} for s in SIGNS}
    print('Original splits:',json.dumps(counts),flush=True)
    manifest={'source':REPO,'revision':REV,'seed':SEED,
      'grouping':'exact unique source original image SHA256',
      'labels':dict(zip(map(str,SIGNS),LABELS)), 'counts':counts,
      'cross_sign_duplicate_groups_excluded':conflicts,
      'splits':{n:[{'sign':r['sign'],'graph':r['graph'],'sha256':r['sha256']} for r in group]
                for n,group in splits.items()}}
    with open(BASE/'split_manifest.json','w') as f:json.dump(manifest,f,indent=2)
    index={n:i for i,n in enumerate(SIGNS)}
    rng=np.random.default_rng(SEED)
    trains=[];trainy=[]
    for r in splits['train']:
        trains.append(r['im']);trainy.append(index[r['sign']])
        for _ in range(19):
            trains.append(augment(r['im'],rng));trainy.append(index[r['sign']])
    X=features(trains);y=np.asarray(trainy,dtype='int64')
    valX=features([r['im'] for r in splits['val']])
    valy=np.asarray([index[r['sign']] for r in splits['val']])
    testX=features([r['im'] for r in splits['test']])
    testy=np.asarray([index[r['sign']] for r in splits['test']])
    print('Training matrix',X.shape,'validation',valX.shape,'held-out',testX.shape,flush=True)
    methods=[('linear_0.1',SVC(kernel='linear',C=.1)),
             ('linear_1',SVC(kernel='linear',C=1)),
             ('rbf_1',SVC(kernel='rbf',C=1,gamma='scale')),
             ('rbf_10',SVC(kernel='rbf',C=10,gamma='scale')),
             ('knn_1',KNeighborsClassifier(n_neighbors=1,weights='distance'))]
    leaderboard=[];models={}
    for name,model in methods:
        model.fit(X,y)
        pred=model.predict(valX)
        acc=accuracy_score(valy,pred)
        leaderboard.append({'method':name,'validation_accuracy':round(float(acc),4),
          'correct':int((pred==valy).sum()),'total':len(valy)})
        models[name]=model
        print(name,'validation',round(acc,3),flush=True)
    order=[n for n,_ in methods]
    chosen=sorted(leaderboard,key=lambda x:(-x['validation_accuracy'],order.index(x['method'])))[0]['method']
    model=models[chosen]
    # Held-out test predictions evaluated exactly once after validation-only selection.
    pred=model.predict(testX)
    acc=accuracy_score(testy,pred)
    matrix=confusion_matrix(testy,pred,labels=list(range(len(SIGNS)))).tolist()
    report=classification_report(testy,pred,labels=list(range(len(SIGNS))),
      target_names=LABELS,output_dict=True,zero_division=0)
    result={'experiment':'TWKM five-class original-drawing held-out v2','dataset':REPO,
      'revision':REV,'classes':[{'sign_no':s,'label':LABELS[i]} for i,s in enumerate(SIGNS)],
      'selection':'Validation-only comparison on five original drawings, stable method-order tie break',
      'candidate_methods':leaderboard,'selected_model':chosen,
      'heldout_test_original_count':len(testy),'heldout_test_accuracy':round(float(acc),4),
      'heldout_correct':int((testy==pred).sum()),'heldout_confusion_matrix':matrix,
      'heldout_by_class':{k:{z:v[z] for z in ('precision','recall','f1-score','support')}
         for k,v in report.items() if k in LABELS},
      'predictions':[{'graph':r['graph'],'true_sign':r['sign'],
          'predicted_sign':SIGNS[int(guess)],'correct':bool(index[r['sign']]==guess)}
           for r,guess in zip(splits['test'],pred)],
      'limitations':['Only five predefined sign classes','Standardized drawings, not phone photographs',
        'Small test set, wide statistical uncertainty','Unknown/unsupported glyph rejection not evaluated',
        'Augmentations exclusively from training originals'],
      'runtime_seconds':round(time.time()-start,1)}
    with open(BASE/'evaluation.json','w') as f:json.dump(result,f,indent=2)
    joblib.dump({'model':model,'signs':SIGNS,'labels':LABELS,'size':SIZE,
       'feature_contract':'64x64 bright foreground on black HOG 9 orientations, 8px cells, 2x2 blocks'},
       BASE/'classifier_v2.joblib',compress=3)
    print('FINAL HELDOUT',result['heldout_correct'],'/',len(testy),'accuracy',result['heldout_test_accuracy'],flush=True)
    print('MODEL SIZE', (BASE/'classifier_v2.joblib').stat().st_size, 'bytes',flush=True)
if __name__=='__main__':main()
