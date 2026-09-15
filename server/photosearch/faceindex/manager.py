"""LoadedGeneration — ดัชนีหนึ่งรุ่นที่โหลดเข้า RAM แล้วพร้อมค้นหา

ดัชนีที่พร้อมใน RAM ถูกใช้ซ้ำระหว่างคำค้น โหลดใหม่เฉพาะตอนเริ่ม instance
เปิดกิจกรรมที่ยังไม่โหลด หรือพบ generation ใหม่ (ดู services/registry.py)
"""
from __future__ import annotations

import json
from dataclasses import dataclass

import numpy as np

from ..schemas import FaceEntry, Manifest, PhotoRecord
from ..storage import Storage
from .generation import deserialize_index, verify_generation


@dataclass
class SearchHit:
    photo_id: str
    score: float


class LoadedGeneration:
    def __init__(
        self,
        manifest: Manifest,
        photos: dict[str, PhotoRecord],
        faces: list[FaceEntry],
        index_kind: str,
        index_obj,
    ):
        self.manifest = manifest
        self.photos = photos
        self.faces = faces
        self.index_kind = index_kind
        self.index_obj = index_obj  # faiss index หรือ np.ndarray (แถว = เวกเตอร์)
        # แผนที่แถว -> photoId (สำหรับ map ผลค้นหากลับเป็นรูป)
        self.row_to_photo = [f.photoId for f in sorted(faces, key=lambda x: x.vectorRow)]

    @property
    def generation_id(self) -> str:
        return self.manifest.generationId

    @property
    def dim(self) -> int:
        return self.manifest.vectorDimension

    def search(self, query_vectors: np.ndarray, threshold: float, max_results: int) -> list[SearchHit]:
        """ค้นหาด้วยเวกเตอร์เซลฟี (อาจมีหลายใบหน้า) แล้วรวมผลเป็นรายรูป

        - หนึ่งรูปอาจมีหลายใบหน้า → เก็บคะแนนสูงสุดต่อ photoId
        - เรียงจากคะแนนมากไปน้อยแบบคงที่ (tie-break ด้วย photoId)
        """
        if query_vectors.size == 0 or self.manifest.faceCount == 0:
            return []
        q = query_vectors.astype(np.float32)
        scores, idxs = self._raw_search(q, min(max_results * 4, max(1, self.manifest.faceCount)))
        best: dict[str, float] = {}
        for qi in range(scores.shape[0]):
            for rank in range(scores.shape[1]):
                row = int(idxs[qi, rank])
                if row < 0:
                    continue
                sc = float(scores[qi, rank])
                if sc < threshold:
                    continue
                photo_id = self.row_to_photo[row]
                rec = self.photos.get(photo_id)
                # กันรูปที่ถูกถอน/ไม่ ok หลุดเข้าผล (ตรวจซ้ำอีกชั้นตอนส่งรูป)
                if rec is None or rec.processingStatus != "ok":
                    continue
                if photo_id not in best or sc > best[photo_id]:
                    best[photo_id] = sc
        hits = [SearchHit(pid, sc) for pid, sc in best.items()]
        hits.sort(key=lambda h: (-h.score, h.photo_id))
        return hits[:max_results]

    def _raw_search(self, q: np.ndarray, topk: int):
        if self.index_kind == "faiss":
            return self.index_obj.search(q, topk)
        # numpy brute force (IndexFlatIP เทียบเท่า): scores = Q · V^T
        vectors: np.ndarray = self.index_obj
        if vectors.size == 0:
            n = q.shape[0]
            return np.zeros((n, 1), dtype=np.float32) - 1, np.zeros((n, 1), dtype=np.int64) - 1
        sims = q @ vectors.T
        topk = min(topk, vectors.shape[0])
        idx = np.argpartition(-sims, topk - 1, axis=1)[:, :topk]
        # เรียงภายใน topk
        rows = np.arange(sims.shape[0])[:, None]
        order = np.argsort(-sims[rows, idx], axis=1)
        idx_sorted = idx[rows, order]
        sc_sorted = sims[rows, idx_sorted]
        return sc_sorted.astype(np.float32), idx_sorted.astype(np.int64)


def load_generation(storage: Storage, event_id: str, generation_id: str) -> LoadedGeneration:
    base = f"events/{event_id}/generations/{generation_id}"
    manifest = Manifest(**json.loads(storage.read_path(f"{base}/manifest.json").decode("utf-8")))
    index_bytes = storage.read_path(f"{base}/index.faiss")
    photos_bytes = storage.read_path(f"{base}/photos.json")
    faces_bytes = storage.read_path(f"{base}/faces-map.json")
    # ตรวจ checksum/ขนาดก่อนโหลดเข้า RAM
    verify_generation(manifest, index_bytes, photos_bytes, faces_bytes)
    photos_list = [PhotoRecord(**p) for p in json.loads(photos_bytes.decode("utf-8"))]
    faces = [FaceEntry(**f) for f in json.loads(faces_bytes.decode("utf-8"))]
    kind, index_obj = deserialize_index(index_bytes)
    photos = {p.photoId: p for p in photos_list}
    return LoadedGeneration(manifest, photos, faces, kind, index_obj)
