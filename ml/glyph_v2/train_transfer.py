"""Pretrained image-feature comparison using group-held-out TWKM graph originals.

No TWKM drawing (or an augmentation of it) occurs in both training and test
for any fold. A five-fold original-image cross-validation estimates how these
models classify catalog drawings; it is NOT a held-out phone-camera benchmark.
"""
import json,os,time
from pathlib import Path

import cv2,joblib,numpy as np,torch
from sklearn.metrics import accuracy_score,f1_score,confusion_matrix
from sklearn.model_selection import StratifiedKFold
from sklearn.neighbors import KNeighborsClassifier
from sklearn.svm import SVC
from torchvision import models
from torch import nn
import train

SEED=train.SEED
BASE=Path('ml/glyph_v2/outputs/transfer')
SAMPLES=4

def tensorize(batch):
    ims=[]
    for a in batch:
        gray=255-a
        up=cv2.resize(gray,(224,224),interpolation=cv2.INTER_LINEAR)
        x=torch.from_numpy(up.copy()).float().div_(255)
        rgb=x.repeat(3,1,1)
        rgb.sub_(torch.tensor([.485,.456,.406])[:,None,None]).div_(torch.tensor([.229,.224,.225])[:,None,None])
        ims.append(rgb)
    return torch.stack(ims)

def embed(net,imgs):
    out=[]
    net.eval()
    with torch.inference_mode():
        for pos in range(0,len(imgs),12):
            x=tensorize(imgs[pos:pos+12])
            z=net(x).reshape(x.shape[0],-1)
            z=torch.nn.functional.normalize(z,dim=1)
            out.append(z.numpy())
    return np.concatenate(out,axis=0)

def experiment(name,make_net,rows,views,labels):
    print('Preparing pretrained backbone:',name,flush=True)
    net=make_net()
    feats=embed(net,views)
    print(name,'features',feats.shape,flush=True)
    vals=feats.reshape(len(rows),SAMPLES,-1)
    candidates=[('svm_linear',SVC(kernel='linear',C=1)),
        ('svm_rbf',SVC(kernel='rbf',C=10,gamma='scale')),
        ('knn_cosine',KNeighborsClassifier(n_neighbors=1,metric='cosine'))]
    split=StratifiedKFold(n_splits=5,shuffle=True,random_state=SEED)
    results=[]
    for method,estimator in candidates:
        predictions=np.full(len(rows),-1,dtype='int64')
        for train_ids,test_ids in split.split(np.zeros(len(rows)),labels):
            xx=vals[train_ids].reshape(-1,vals.shape[-1])
            yy=np.repeat(labels[train_ids],SAMPLES)
            estimator.fit(xx,yy)
            predictions[test_ids]=estimator.predict(vals[test_ids,0,:])
        assert np.all(predictions>=0)
        acc=float(accuracy_score(labels,predictions))
        macro=float(f1_score(labels,predictions,average='macro',zero_division=0))
        results.append({'backbone':name,'method':method,
            'out_of_fold_accuracy':round(acc,4),
            'out_of_fold_correct':int((predictions==labels).sum()),
            'out_of_fold_original_count':len(rows),
            'out_of_fold_macro_f1':round(macro,4),
            'confusion':confusion_matrix(labels,predictions,labels=list(range(len(train.SIGNS)))).tolist(),
            'predictions':[{'graph':row['graph'],'sign':row['sign'],
                'pred_sign':train.SIGNS[int(pred)]} for row,pred in zip(rows,predictions)]})
        print(name,method,'CV',round(acc,4),'macro_f1',round(macro,4),flush=True)
    np.savez_compressed(BASE/(name+'_features.npz'),features=vals[:,0,:],
        labels=labels,graph_ids=np.array([r['graph'] for r in rows]))
    return results,net,vals

def prepare_mobilenet():
    net=models.mobilenet_v3_small(weights=models.MobileNet_V3_Small_Weights.DEFAULT)
    net.classifier=nn.Identity()
    return net

def prepare_resnet():
    net=models.resnet18(weights=models.ResNet18_Weights.DEFAULT)
    net.fc=nn.Identity()
    return net

def main():
    os.environ.setdefault('OMP_NUM_THREADS','2')
    torch.set_num_threads(2)
    torch.manual_seed(SEED);np.random.seed(SEED)
    BASE.mkdir(parents=True,exist_ok=True)
    by_sign,conflicts=train.get_rows()
    rows=[r for s in train.SIGNS for r in sorted(by_sign[s],key=lambda z:z['sha256'])]
    labels=np.asarray([train.SIGNS.index(r['sign']) for r in rows],dtype=np.int64)
    rng=np.random.default_rng(SEED)
    views=[]
    for r in rows:
        views.append(r['im'])
        views.extend([train.augment(r['im'],rng) for _ in range(SAMPLES-1)])
    net_defs=[('mobilenet_v3_small',prepare_mobilenet),
        ('resnet18',prepare_resnet)]
    results=[];nets={};matrices={}
    started=time.time()
    for name,definition in net_defs:
        res,net,vals=experiment(name,definition,rows,views,labels)
        results.extend(res);nets[name]=net;matrices[name]=vals
    report={'purpose':'Compare frozen ImageNet vision features for five TWKM sign classes',
      'source':train.REPO,'revision':train.REV,'seed':SEED,'original_count':len(rows),
      'classes':[{'sign_no':s,'label':train.LABELS[i]} for i,s in enumerate(train.SIGNS)],
      'evaluation':'Five-fold stratified CV; each distinct original is tested in exactly one fold; no augmentation leaks',
      'models':results,
      'limitations':['CV estimates on TWKM standardized drawings only, not phone camera images',
        'Comparing models using CV and selecting the best creates model-selection optimism',
        'Sign classification is not English sentence translation',
        'Unknown/unsupported glyph rejection not evaluated',
        'ResNet18 backbone is too large for lightweight Android'],
      'seconds':round(time.time()-started,1)}
    with open(BASE/'evaluation.json','w') as f:json.dump(report,f,indent=2)
    name='mobilenet_v3_small'
    sub=sorted([r for r in results if r['backbone']==name],
        key=lambda r:(-r['out_of_fold_accuracy'],r['method']))[0]
    method=sub['method']
    estimator={'svm_linear':SVC(kernel='linear',C=1),
       'svm_rbf':SVC(kernel='rbf',C=10,gamma='scale'),
       'knn_cosine':KNeighborsClassifier(n_neighbors=1,metric='cosine')}[method]
    estimator.fit(matrices[name].reshape(-1,matrices[name].shape[-1]),np.repeat(labels,SAMPLES))
    joblib.dump({'classifier':estimator,'classes':train.SIGNS,'labels':train.LABELS,
        'backbone':name,'pretrained_weights':'MobileNet_V3_Small_Weights.DEFAULT'},
        BASE/'mobilenet_v3_small_head.joblib',compress=3)
    torch.save(nets[name].state_dict(),BASE/'mobilenet_v3_small_backbone.pt')
    report['exported_mobilenet_method']=method
    with open(BASE/'evaluation.json','w') as f:json.dump(report,f,indent=2)
    print('Exported pretrained backbone', (BASE/'mobilenet_v3_small_backbone.pt').stat().st_size,'bytes',flush=True)
    print('Completed transfer comparison',round(time.time()-started,1),'seconds',flush=True)

if __name__=='__main__':main()
