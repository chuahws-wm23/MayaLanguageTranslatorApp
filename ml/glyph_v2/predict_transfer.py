"""Run the saved experimental five-sign glyph classifier on one cropped image.
This is closed-set recognition with NO unsupported-sign rejection yet.
"""
import argparse,json
from pathlib import Path
import joblib,torch
from torchvision import models
from torch import nn
import train
from train_transfer import embed

OUT=Path('ml/glyph_v2/outputs/transfer')

def predict(path):
    backbone=OUT/'mobilenet_v3_small_backbone.pt'
    head=OUT/'mobilenet_v3_small_head.joblib'
    assert backbone.exists() and head.exists(), 'Missing research model assets under '+str(OUT)
    model=models.mobilenet_v3_small(weights=None)
    model.classifier=nn.Identity()
    model.load_state_dict(torch.load(backbone,map_location='cpu',weights_only=True))
    model.eval()
    data=joblib.load(head)
    with open(path,'rb') as f:
        im=train.process({'bytes':f.read()})
    vec=embed(model,[im])
    code=int(data['classifier'].predict(vec)[0])
    return {'status':'UNVERIFIED_CLOSED_SET_SUGGESTION',
      'sign_no':data['classes'][code],
      'catalogue_label':data['labels'][code],
      'warning':'This model always chooses one of five signs even for an unknown glyph. Do not present the suggestion as a verified English translation.',
      'model':'Frozen pretrained MobileNetV3-Small image features and trained RBF SVM'}

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('image',help='Path to one cropped glyph JPG or PNG')
    args=p.parse_args()
    torch.set_num_threads(2)
    print(json.dumps(predict(args.image),ensure_ascii=False,indent=2))
