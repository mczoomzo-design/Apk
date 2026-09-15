"""Mock embedder — deterministic ใช้ทดสอบเส้นทางทั้งระบบโดยไม่ต้องมีโมเดลจริง

*** คำเตือน ***
นี่ไม่ใช่ face recognition จริง ใช้เพื่อพิสูจน์ "เส้นทางข้อมูล" (pipeline) เท่านั้น
ห้ามนำผลความแม่นยำจาก mock ไปอ้างว่าโมเดลจับคู่ใบหน้าจริงแม่นยำ
การพิสูจน์ความแม่นยำต้องใช้ EMBEDDER=insightface กับภาพจริงที่ได้รับอนุญาต

หลักการจำลอง (สอดคล้องกับ fixture generator ใน scripts/make_fixtures.py):
  - "ใบหน้า" = สี่เหลี่ยมสีทึบจาก palette คงที่ วางบนพื้นหลังเทา
  - identity ของใบหน้า = ดัชนีสีใน palette
  - embedding = เวกเตอร์หนึ่งหน่วยที่ seed จาก identity + jitter เล็กน้อย
    ทำให้รูปของ "คนเดียวกัน" มี similarity สูงแต่ไม่เท่ากับ 1 พอดี
"""
from __future__ import annotations

import io

import numpy as np

from .base import Face, l2_normalize

# palette คงที่ — ต้องตรงกับ scripts/make_fixtures.py
PALETTE = [
    (220, 20, 60),   # 0 crimson
    (30, 144, 255),  # 1 dodgerblue
    (34, 139, 34),   # 2 forestgreen
    (255, 140, 0),   # 3 darkorange
    (148, 0, 211),   # 4 darkviolet
    (0, 139, 139),   # 5 teal
    (184, 134, 11),  # 6 darkgoldenrod
    (199, 21, 133),  # 7 mediumvioletred
    (70, 130, 180),  # 8 steelblue
    (139, 69, 19),   # 9 saddlebrown
]
BACKGROUND = (128, 128, 128)
DIM = 128


def _identity_vector(identity: int, dim: int = DIM) -> np.ndarray:
    rng = np.random.default_rng(seed=1000 + identity)
    v = rng.standard_normal(dim).astype(np.float32)
    return l2_normalize(v)


class MockEmbedder:
    dim = DIM
    model_id = "mock-colorface"
    model_version = "1"
    preprocessing_version = "mock-1"

    def __init__(self, min_face_px: int = 8, jitter: float = 0.06):
        self.min_face_px = min_face_px
        self.jitter = jitter

    def detect_and_embed(self, image_bytes: bytes) -> list[Face]:
        from PIL import Image

        img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        arr = np.asarray(img)
        h, w, _ = arr.shape
        bg = np.array(BACKGROUND)
        # mask ของ pixel ที่ไม่ใช่พื้นหลัง
        mask = np.abs(arr.astype(int) - bg).sum(axis=2) > 40
        faces: list[Face] = []
        visited = np.zeros((h, w), dtype=bool)
        # ค้นหา connected components แบบง่าย (flood fill ด้วย label ตามแถว)
        boxes = _bounding_boxes(mask, self.min_face_px)
        for (x1, y1, x2, y2) in boxes:
            patch = arr[y1:y2, x1:x2].reshape(-1, 3).mean(axis=0)
            identity = _nearest_palette(patch)
            if identity is None:
                continue
            base = _identity_vector(identity, self.dim)
            # jitter deterministic ตามตำแหน่ง เพื่อไม่ให้ทุกใบหน้าเหมือนเป๊ะ
            seed = (identity * 73856093) ^ (x1 * 19349663) ^ (y1 * 83492791)
            rng = np.random.default_rng(seed & 0x7FFFFFFF)
            noise = rng.standard_normal(self.dim).astype(np.float32) * self.jitter
            emb = l2_normalize(base + noise)
            faces.append(
                Face(
                    embedding=emb,
                    bbox=[float(x1), float(y1), float(x2), float(y2)],
                    det_score=1.0,
                )
            )
        return faces


def _nearest_palette(color: np.ndarray, tol: float = 60.0):
    best, best_d = None, 1e9
    for i, c in enumerate(PALETTE):
        d = float(np.linalg.norm(color - np.array(c)))
        if d < best_d:
            best_d, best = d, i
    return best if best_d <= tol else None


def _bounding_boxes(mask: np.ndarray, min_px: int) -> list[tuple[int, int, int, int]]:
    """label connected components (4-neighbour) และคืน bounding box ที่ใหญ่พอ"""
    h, w = mask.shape
    labels = np.zeros((h, w), dtype=np.int32)
    cur = 0
    boxes: dict[int, list[int]] = {}
    from collections import deque

    for i in range(h):
        for j in range(w):
            if mask[i, j] and labels[i, j] == 0:
                cur += 1
                q = deque([(i, j)])
                labels[i, j] = cur
                minx, miny, maxx, maxy = j, i, j, i
                while q:
                    y, x = q.popleft()
                    minx, maxx = min(minx, x), max(maxx, x)
                    miny, maxy = min(miny, y), max(maxy, y)
                    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        ny, nx = y + dy, x + dx
                        if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and labels[ny, nx] == 0:
                            labels[ny, nx] = cur
                            q.append((ny, nx))
                boxes[cur] = [minx, miny, maxx + 1, maxy + 1]
    out = []
    for _, (x1, y1, x2, y2) in boxes.items():
        if (x2 - x1) >= min_px and (y2 - y1) >= min_px:
            out.append((x1, y1, x2, y2))
    return sorted(out)
