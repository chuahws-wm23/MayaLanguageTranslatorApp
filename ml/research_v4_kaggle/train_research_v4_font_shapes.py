#!/usr/bin/env python3
"""Research v4 auxiliary font-shape audit + benchmark (NOT Maya translation).

No external downloads. Uses the user-supplied Kaggle ZIP and trains an 80-key
font classifier with pixel-deduplicated splits. An additional fill->outline
experiment measures a source-style shift; neither test measures phone-camera
accuracy or unseen Maya glyph shapes.

Usage:
    python train_research_v4_font_shapes.py --zip archive.zip --out results --epochs 16

Dependency: torch, numpy, Pillow, sklearn. Do not publish derived weights until
font/dataset reuse terms are cleared. The images are never re-uploaded.
"""
from __future__ import annotations
import argparse, hashlib, io, json, random, time, zipfile
from collections import Counter, defaultdict
from pathlib import Path
import numpy as np
from PIL import Image, ImageFilter
import torch
from torch import nn
import torch.nn.functional as F
from sklearn.model_selection import train_test_split
from sklearn.metrics import confusion_matrix

class FontNet(nn.Module):
    def __init__(self, n_classes=80):
        super().__init__()
        def block(a,b):
            return nn.Sequential(nn.Conv2d(a,b,3,padding=1),nn.BatchNorm2d(b),nn.SiLU(),nn.MaxPool2d(2))
        self.network=nn.Sequential(
            block(1,24),block(24,48),block(48,96),block(96,128),
            nn.AdaptiveAvgPool2d((2,2)),nn.Flatten(),
            nn.Linear(512,192),nn.SiLU(),nn.Dropout(.20),nn.Linear(192,n_classes))
    def forward(self,x):return self.network(x)


def set_seed(seed):
    random.seed(seed);np.random.seed(seed);torch.manual_seed(seed)
    torch.set_num_threads(min(4, torch.get_num_threads()))


def extract(zip_name):
    """Keep exactly one image per pixel SHA; drop blanks, collisions, single-style classes."""
    raw=[]
    with zipfile.ZipFile(zip_name) as archive:
        for name in archive.namelist():
            if not name.endswith('.png'):continue
            parts=name.split('/')
            if len(parts)<5: continue
            style, split, key=parts[-4:-1]
            if style not in ('mayanglyphsfill_regular','mayanglyphsoutline_regular'):continue
            with Image.open(io.BytesIO(archive.read(name))) as picture:
                picture=picture.convert('L')
                if picture.size != (64,64):raise RuntimeError(f'Unexpected image size {name}')
                arr=np.asarray(picture,dtype=np.uint8).copy()
            if int(arr.max()) == int(arr.min()):continue
            digest=hashlib.sha256(arr.tobytes()).hexdigest()
            raw.append((style,key,digest,arr,name))
    counts=defaultdict(set)
    for style,key,*_ in raw:counts[style].add(key)
    shared=sorted(set.intersection(*counts.values()))
    valid=set(shared)
    by_hash=defaultdict(list)
    for sample in raw:
        if sample[1] in valid:by_hash[sample[2]].append(sample)
    clashes={h:{r[1] for r in rows} for h,rows in by_hash.items() if len({r[1] for r in rows})>1}
    safe=[s for h,rows in by_hash.items() if h not in clashes for s in [rows[0]]]
    print(f'Raw nonblank: {len(raw)}; shared keys: {len(shared)}; unique pixels in shared: {len(safe)}; collisions: {len(clashes)}',flush=True)
    assert len(shared)==80,'Unexpected shared-class count; re-audit the input ZIP'
    if clashes:print('Excluded hash collisions:',len(clashes),flush=True)
    return safe,shared,{"raw_nonblank":len(raw),"shared_keys":len(shared),"unique_images":len(safe),"label_collision_hashes":len(clashes)}


def partitions(samples,labels,seed):
    # One example per SHA already. Stratify unique images by source font key + style.
    indices=np.arange(len(samples))
    strata=np.array([f'{s[0]}_{s[1]}' for s in samples])
    trainval,test=train_test_split(indices,test_size=.15,random_state=seed,stratify=strata)
    train,val=train_test_split(trainval,test_size=.15/.85,random_state=seed+1,stratify=strata[trainval])
    return train,val,test


def as_tensors(samples,labels):
    classes={s:i for i,s in enumerate(labels)}
    x=np.stack([s[3] for s in samples]).astype(np.float32)[:,None,:,:] / 255
    y=np.asarray([classes[s[1]] for s in samples],dtype=np.int64)
    styles=np.asarray([0 if s[0]=='mayanglyphsfill_regular' else 1 for s in samples])
    return torch.from_numpy(x),torch.from_numpy(y),styles


def augment(x):
    # Mild, online training-only variations; do not augment test images.
    b=x.size(0)
    # Shared rotation/shear/translation per input via grid sampling (no PIL dependency here).
    deg=(torch.rand(b)-.5)*.28
    ct=torch.cos(deg);st=torch.sin(deg)
    scale=.92+torch.rand(b)*.16
    theta=torch.zeros(b,2,3)
    theta[:,0,0]=ct*scale;theta[:,0,1]=-st
    theta[:,1,0]=st;theta[:,1,1]=ct*scale
    theta[:,:,2]=(torch.rand(b,2)-.5)*.18
    grid=F.affine_grid(theta,x.shape,align_corners=False)
    x=F.grid_sample(x,grid,padding_mode='border',align_corners=False)
    if random.random() < .5:
        x=(x*(.85+torch.rand(b,1,1,1)*.30)+(.04*torch.randn(b,1,1,1))).clamp(0,1)
    return x


def train_model(x,y,train_ids,val_ids,n_classes,epochs=16,seed=26,lr=.002):
    set_seed(seed)
    net=FontNet(n_classes)
    optimizer=torch.optim.AdamW(net.parameters(),lr=lr,weight_decay=.001)
    scheduler=torch.optim.lr_scheduler.CosineAnnealingLR(optimizer,T_max=epochs,eta_min=0.0001)
    tr=torch.as_tensor(np.asarray(train_ids),dtype=torch.long)
    val=torch.as_tensor(np.asarray(val_ids),dtype=torch.long)
    history=[];best=None;best_acc=-1
    for epoch in range(epochs):
        net.train();order=tr[torch.randperm(len(tr))]
        losses=[]
        for batch in order.split(128):
            pred=net(augment(x[batch]))
            loss=F.cross_entropy(pred,y[batch],label_smoothing=.025)
            optimizer.zero_grad(set_to_none=True);loss.backward();optimizer.step()
            losses.append(float(loss.item()))
        scheduler.step()
        net.eval()
        with torch.no_grad():
            logits=torch.cat([net(x[b]) for b in val.split(256)])
            correct=float((logits.argmax(1)==y[val]).float().mean())
        history.append(dict(epoch=epoch+1,train_loss=round(float(np.mean(losses)),4),val_top1=round(correct,4)))
        if correct > best_acc:
            best_acc=correct;best={k:v.detach().clone() for k,v in net.state_dict().items()}
        print(f'epoch={epoch+1:02d} loss={np.mean(losses):.4f} val={correct*100:.1f}%',flush=True)
    net.load_state_dict(best);net.eval()
    return net,history,best_acc


def predict(net,x,ids):
    if not len(ids):return np.zeros((0,80),dtype=np.float32)
    ii=torch.as_tensor(np.asarray(ids),dtype=torch.long)
    with torch.no_grad():
        return torch.cat([net(x[i]) for i in ii.split(256)]).numpy()


def summarize(logits,labels,styles,indices,keys):
    gold=labels[indices].numpy()
    ranks=(-logits).argsort(axis=1)
    pred=ranks[:,0]
    res=dict(count=len(gold),top1=round(float(np.mean(pred==gold)),6),top5=round(float(np.mean(np.any(ranks[:,:5]==gold[:,None],axis=1))),6))
    res['by_style']={style:dict(count=int(np.sum(styles[indices]==num)),top1=round(float(np.mean(pred[styles[indices]==num]==gold[styles[indices]==num])),6)) for num,style in [(0,'fill'),(1,'outline')] if np.any(styles[indices]==num)}
    wrong=np.flatnonzero(pred!=gold)
    res['mistakes']=[{'image_index':int(indices[t]),'actual':keys[int(gold[t])],'predicted':keys[int(pred[t])]} for t in wrong[:30]]
    return res


def photo_simulation(x,seed=33):
    # Deliberately generated photographic artifacts. NOT real phone photos.
    rng=np.random.default_rng(seed)
    src=(x[:,0].numpy()*255).astype(np.uint8)
    result=[]
    yy,xx=np.mgrid[:64,:64]
    for i,arr in enumerate(src):
        im=Image.fromarray(arr,'L')
        if i%2:im=im.filter(ImageFilter.GaussianBlur(radius=0.8))
        arr=np.asarray(im,dtype=np.float32)
        base=(rng.uniform(-27,27)*xx/63)+(rng.uniform(-27,27)*yy/63)
        noisy=(arr+base+rng.normal(0,8,size=arr.shape)).clip(0,255).astype(np.uint8)
        result.append(noisy)
    return torch.from_numpy(np.stack(result).astype(np.float32)[:,None]/255)


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--zip',required=True)
    parser.add_argument('--out',default='research_v4_font_experiment')
    parser.add_argument('--epochs',type=int,default=16)
    parser.add_argument('--style-epochs',type=int,default=10)
    parser.add_argument('--seed',type=int,default=2026)
    args=parser.parse_args();start=time.monotonic()
    out=Path(args.out);out.mkdir(parents=True,exist_ok=True)
    set_seed(args.seed)
    samples,keys,audit=extract(args.zip)
    x,y,styles=as_tensors(samples,keys)
    train,val,test=partitions(samples,keys,args.seed)
    hashes={partition:{samples[i][2] for i in ids} for partition,ids in [('train',train),('validation',val),('test',test)]}
    assert not hashes['train']&hashes['validation'] and not hashes['train']&hashes['test'] and not hashes['validation']&hashes['test']
    print('split sizes:',len(train),len(val),len(test),flush=True)
    net,history,best=train_model(x,y,train,val,len(keys),args.epochs,args.seed)
    test_logits=predict(net,x,test)
    test_result=summarize(test_logits,y,styles,test,keys)
    pseudo_photo=photo_simulation(x[test],args.seed+1)
    augmented_logits=predict(net,pseudo_photo,np.arange(len(test)))
    pseudo_result=summarize(augmented_logits,y,styles,test,keys)
    # Additional out-of-domain: train *only* filled font, evaluate outline font.
    # Need within-train validation from fill; never use outline for training or tuning.
    fill=np.flatnonzero(styles==0)
    outline=np.flatnonzero(styles==1)
    fill_train,fill_val=train_test_split(fill,test_size=.18,random_state=args.seed,stratify=y[fill].numpy())
    fill_net,fill_history,_=train_model(x,y,fill_train,fill_val,len(keys),args.style_epochs,args.seed+42)
    outline_result=summarize(predict(fill_net,x,outline),y,styles,outline,keys)
    checkpoint=out/'research_v4_aux_font_shapes.pt'
    torch.save({'model':net.state_dict(),'font_keys':keys,'model_type':'auxiliary_font_key_visual_classifier','seed':args.seed},checkpoint)
    # TorchScript can be loaded outside Python for testing, but this is NOT the Android ONNX model.
    torch.jit.trace(net,torch.rand(1,1,64,64)).save(str(out/'research_v4_aux_font_shapes.torchscript.pt'))
    (out/'font_keys.json').write_text(json.dumps(keys,indent=2))
    (out/'experiment_history.json').write_text(json.dumps({'both_styles':history,'fill_only':fill_history},indent=2))
    splits={k:[{'pixel_sha256':samples[i][2],'style':samples[i][0],'font_key':samples[i][1]} for i in ids] for k,ids in [('train',train),('validation',val),('test',test)]}
    (out/'split_manifest.json').write_text(json.dumps(splits,indent=2))
    summary={
        'purpose':'Synthetic-font key recognition only. NOT a Maya reading or translation model.',
        'dataset_url':'https://www.kaggle.com/datasets/sayantanroy10121999/mayan-glyphs-dataset',
        'audit':audit, 'classes':len(keys),
        'split':{'train':len(train),'validation':len(val),'test':len(test),'no_exact_pixel_overlap':True},
        'mixed_style_unique_synthetic_test':test_result,
        'same_test_with_generated_photo_artifacts':pseudo_result,
        'filled_style_training_outline_style_test':outline_result,
        'cautions':[
            'All images are synthetic font renderings from one source; no independent glyph identities.',
            'No independently labelled real camera photos were tested.',
            'U_XXXX keys are font codes, not deciphered signs; do not attach Maya readings.',
            'The original research v4 ten-class model weights and Android ONNX integration are unchanged.',
            'Verify reuse rights before redistribution of the derived model.',
        ],
        'duration_seconds':round(time.monotonic()-start,1)
    }
    (out/'evaluation.json').write_text(json.dumps(summary,indent=2))
    print('\n*** FINAL METRICS ***',json.dumps({k:v for k,v in summary.items() if k.endswith('test') or k.endswith('artifacts')},indent=2),flush=True)
    print('SAVED to',out.resolve(),'duration',summary['duration_seconds'],'seconds',flush=True)

if __name__=='__main__':main()
