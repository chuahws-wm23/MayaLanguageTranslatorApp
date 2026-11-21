#!/usr/bin/env python3
"""Build reference features using the EXISTING pinned pretrained ONNX backbone.

No classifier training, synthetic labels, or unverified sign meanings. The 64x64
bitplane -> float bilinear -> ImageNet NCHW contract matches GlyphEmbeddingMath.
Run from repository root: python ml/logogram_expansion/build_reference_embeddings.py
Requires numpy and onnxruntime. Regenerate after ANY gallery/model change.
"""
import hashlib
import json
from pathlib import Path
import numpy as np
import onnxruntime as ort

ASSETS = Path(__file__).resolve().parents[2] / 'app/src/main/assets'
MODEL_SHA = 'e2887dae439a9cc39d0035d31bf55c7deeee7c1002b81d59ecc7f1e6171fc6de'

def sha(data):
    return hashlib.sha256(data).hexdigest()

def tensor(bitplanes):
    # Preserve float pixels; Pillow's integer bilinear resize rounds differently.
    coords = np.clip((np.arange(224, dtype=np.float64) + .5)*64/224 - .5, 0, 63)
    low = np.floor(coords).astype(int)
    high = np.minimum(low + 1, 63)
    frac = (coords - low).astype(np.float32)
    pixels = 1 - bitplanes.astype(np.float32)
    horizontal = pixels[:, :, low]*(1-frac) + pixels[:, :, high]*frac
    channel = horizontal[:, low, :]*(1-frac[None, :, None]) + horizontal[:, high, :]*frac[None, :, None]
    rgb = np.repeat(channel[:, None], 3, axis=1)
    return ((rgb - np.array([.485, .456, .406], np.float32)[None, :, None, None]) /
            np.array([.229, .224, .225], np.float32)[None, :, None, None])

def run():
    model = ASSETS / 'maya_mobilenet_v2.onnx'
    assert sha(model.read_bytes()) == MODEL_SHA
    options = ort.SessionOptions()
    options.intra_op_num_threads = 2
    session = ort.InferenceSession(str(model), options, providers=['CPUExecutionProvider'])
    meta = {'format_version': 1, 'dimension': 576, 'model_sha256': MODEL_SHA,
            'export_versions': {'numpy': np.__version__, 'onnxruntime': ort.__version__},
            'preprocessing': 'binary64_float_bilinear224_imagenet_nchw_v1',
            'score': 'L2-normalized feature cosine, not confidence', 'galleries': {}}
    for kind in ['logogram', 'phonogram']:
        index = (ASSETS / f'maya_twkm_{kind}_index.json').read_bytes()
        artwork = (ASSETS / f'maya_twkm_{kind}_bitmap.bin').read_bytes()
        count = json.loads(index)['count']
        assert len(artwork) == count*512
        masks = np.unpackbits(np.frombuffer(artwork, dtype=np.uint8)).reshape(count, 64, 64)
        features = []
        for mask in masks:
            value = session.run(None, {'image': tensor(mask[None])})[0][0]
            assert value.shape == (576,) and np.isfinite(value).all()
            norm = np.linalg.norm(value)
            assert norm > 1e-8
            features.append(value / norm)
        vectors = np.asarray(features, dtype='<f4').tobytes()
        (ASSETS / f'maya_{kind}_embeddings.bin').write_bytes(vectors)
        meta['galleries'][kind] = {'count': count, 'index_sha256': sha(index),
            'artwork_sha256': sha(artwork), 'vectors_sha256': sha(vectors)}
        print(kind, count, 'drawings indexed')
    (ASSETS / 'maya_reference_embeddings.json').write_text(json.dumps(meta, indent=2)+'\n')

if __name__ == '__main__':
    run()
