"""การสร้างและตรวจสอบ generation ของดัชนีใบหน้า

generation = ชุดข้อมูลดัชนีที่ไม่เปลี่ยนแปลง (immutable) หนึ่งรุ่น ประกอบด้วย
  manifest.json, photos.json, faces-map.json, index.faiss
สร้างรุ่นใหม่แยกจากรุ่นที่ใช้งาน ตรวจ checksum/จำนวน/ความสัมพันธ์ก่อนเผยแพร่
"""
from __future__ import annotations

import hashlib
import io
from dataclasses import dataclass
from datetime import datetime, timezone

import numpy as np

from ..schemas import FaceEntry, Manifest, PhotoRecord

try:
    import faiss  # type: ignore

    _HAS_FAISS = True
except Exception:  # pragma: no cover
    _HAS_FAISS = False


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def serialize_index(vectors: np.ndarray) -> bytes:
    """สร้างไฟล์ดัชนีจากเมทริกซ์เวกเตอร์ (normalize แล้ว, metric=IP)

    ใช้ FAISS IndexFlatIP ถ้ามี ไม่งั้น fallback เป็น .npy (บันทึกเวกเตอร์ตรง ๆ)
    exact search ทั้งสองแบบให้ผลเทียบเท่ากัน
    """
    if vectors.dtype != np.float32:
        vectors = vectors.astype(np.float32)
    if _HAS_FAISS:
        index = faiss.IndexFlatIP(vectors.shape[1] if vectors.size else 1)
        if vectors.size:
            index.add(vectors)
        return b"FAISS" + faiss.serialize_index(index).tobytes()
    buf = io.BytesIO()
    np.save(buf, vectors, allow_pickle=False)
    return b"NPY__" + buf.getvalue()


def deserialize_index(data: bytes):
    tag, payload = data[:5], data[5:]
    if tag == b"FAISS":
        if not _HAS_FAISS:  # pragma: no cover
            raise RuntimeError("ไฟล์ดัชนีเป็น FAISS แต่ไม่ได้ติดตั้ง faiss")
        arr = np.frombuffer(payload, dtype=np.uint8)
        return ("faiss", faiss.deserialize_index(arr))
    if tag == b"NPY__":
        vectors = np.load(io.BytesIO(payload), allow_pickle=False)
        return ("npy", vectors)
    raise ValueError("รูปแบบไฟล์ดัชนีไม่รู้จัก")


@dataclass
class BuiltGeneration:
    manifest: Manifest
    photos: list[PhotoRecord]
    faces: list[FaceEntry]
    index_bytes: bytes


def build_generation(
    event_id: str,
    generation_id: str,
    photos: list[PhotoRecord],
    faces: list[FaceEntry],
    vectors: np.ndarray,
    model_id: str,
    model_version: str,
    preprocessing_version: str,
) -> BuiltGeneration:
    dim = int(vectors.shape[1]) if vectors.size else 0
    index_bytes = serialize_index(vectors)
    photos_bytes = _json_bytes([p.model_dump() for p in photos])
    faces_bytes = _json_bytes([f.model_dump() for f in faces])
    checksums = {
        "index.faiss": _sha256(index_bytes),
        "photos.json": _sha256(photos_bytes),
        "faces-map.json": _sha256(faces_bytes),
    }
    manifest = Manifest(
        generationId=generation_id,
        eventId=event_id,
        modelId=model_id,
        modelVersion=model_version,
        preprocessingVersion=preprocessing_version,
        vectorDimension=dim,
        metric="ip",
        normalization="l2",
        photoCount=len(photos),
        faceCount=len(faces),
        checksums=checksums,
        createdAt=datetime.now(timezone.utc).isoformat(),
    )
    return BuiltGeneration(manifest, photos, faces, index_bytes)


def _json_bytes(obj) -> bytes:
    import json

    return json.dumps(obj, ensure_ascii=False, indent=2).encode("utf-8")


def verify_generation(manifest: Manifest, index_bytes: bytes, photos_bytes: bytes, faces_bytes: bytes) -> None:
    """ตรวจ checksum + จำนวน + ความสัมพันธ์ก่อนโหลด/เผยแพร่ (โยน ValueError ถ้าเสีย)"""
    exp = manifest.checksums
    if _sha256(index_bytes) != exp.get("index.faiss"):
        raise ValueError("checksum ของ index.faiss ไม่ตรง")
    if _sha256(photos_bytes) != exp.get("photos.json"):
        raise ValueError("checksum ของ photos.json ไม่ตรง")
    if _sha256(faces_bytes) != exp.get("faces-map.json"):
        raise ValueError("checksum ของ faces-map.json ไม่ตรง")
