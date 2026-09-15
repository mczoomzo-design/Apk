"""InsightFace adapter — โมเดลจริง (buffalo_l) ผ่าน onnxruntime

*** สิทธิ์การใช้งาน ***
โค้ด InsightFace (MIT) กับ pretrained models มีเงื่อนไขต่างกัน โมเดล buffalo_l
เป็น non-commercial/research ผู้ใช้ยืนยันแล้วว่าใช้เชิงวิจัย/ไม่พาณิชย์
หากเปลี่ยนเป็นเชิงพาณิชย์ต้องเปลี่ยนโมเดลที่มีสิทธิ์เหมาะสม (ดู docs/models.md)

การติดตั้ง (ดู docs/install.md):
  pip install insightface onnxruntime
  โมเดลจะถูกดาวน์โหลด/วางไว้ที่ INSIGHTFACE_ROOT
"""
from __future__ import annotations

import io

import numpy as np

from .base import Face, l2_normalize


class InsightFaceEmbedder:
    dim = 512
    model_id = "insightface"
    preprocessing_version = "if-arcface-1"

    def __init__(self, model_name: str = "buffalo_l", root: str = "~/.insightface", min_face_px: int = 24):
        import os

        from insightface.app import FaceAnalysis  # type: ignore

        self.model_version = model_name
        self.min_face_px = min_face_px
        self.app = FaceAnalysis(name=model_name, root=os.path.expanduser(root))
        # ctx_id=-1 = CPU; det_size ปรับได้เพื่อรักษารายละเอียดใบหน้าเล็กในภาพกลุ่ม
        self.app.prepare(ctx_id=-1, det_size=(640, 640))

    def detect_and_embed(self, image_bytes: bytes) -> list[Face]:
        from PIL import Image

        img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        arr = np.asarray(img)[:, :, ::-1]  # RGB -> BGR ตามที่ insightface คาดหวัง
        detected = self.app.get(arr)
        faces: list[Face] = []
        for d in detected:
            x1, y1, x2, y2 = d.bbox.astype(float)
            if (x2 - x1) < self.min_face_px or (y2 - y1) < self.min_face_px:
                continue
            emb = l2_normalize(np.asarray(d.normed_embedding, dtype=np.float32))
            faces.append(
                Face(embedding=emb, bbox=[x1, y1, x2, y2], det_score=float(d.det_score))
            )
        return faces
