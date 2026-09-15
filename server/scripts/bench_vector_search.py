"""วัดเวลาการเทียบเวกเตอร์เมื่อดัชนีพร้อมใน RAM (เป้าหมาย: p95 <= 300 ms)

*** ขอบเขตที่วัด ***
สคริปต์นี้วัดเฉพาะ "ขั้นตอนค้นหาเวกเตอร์ใน RAM" (embed เซลฟีแล้ว + ดัชนีโหลดแล้ว)
ไม่รวมเวลา: ถอดรหัสเซลฟี, รันโมเดล, เครือข่าย, ดึงรูปจาก Drive
ตัวเลขขึ้นกับ CPU ที่รัน ณ ขณะนั้น — เป็นข้อมูลประกอบ ไม่ใช่คำรับประกันของทั้งระบบ

ใช้เวกเตอร์สุ่ม (มิติ = 512 เท่าโมเดลจริง) เพื่อประมาณ throughput/latency ของ FAISS/numpy
"""
from __future__ import annotations

import argparse
import time

import numpy as np

from photosearch.faceindex.generation import _HAS_FAISS, deserialize_index, serialize_index
from photosearch.faceindex.manager import LoadedGeneration
from photosearch.schemas import FaceEntry, Manifest, PhotoRecord


def build_loaded(n_faces: int, dim: int) -> LoadedGeneration:
    rng = np.random.default_rng(42)
    v = rng.standard_normal((n_faces, dim)).astype(np.float32)
    v /= np.linalg.norm(v, axis=1, keepdims=True)
    index_bytes = serialize_index(v)
    kind, obj = deserialize_index(index_bytes)
    faces = [FaceEntry(faceId=f"f{i}", photoId=f"p{i//3}", vectorRow=i) for i in range(n_faces)]
    photos = {f"p{i//3}": PhotoRecord(eventId="b", photoId=f"p{i//3}", originalFileId=f"o{i}", contentVersion="1") for i in range(n_faces)}
    manifest = Manifest(
        generationId="g", eventId="b", modelId="bench", modelVersion="1",
        preprocessingVersion="1", vectorDimension=dim, photoCount=len(photos), faceCount=n_faces,
    )
    return LoadedGeneration(manifest, photos, faces, kind, obj)


def percentile(xs, p):
    return float(np.percentile(np.array(xs), p))


def run(n_photos: int, faces_per: float, dim: int, iters: int) -> dict:
    n_faces = int(n_photos * faces_per)
    loaded = build_loaded(n_faces, dim)
    rng = np.random.default_rng(7)
    # warm-up
    for _ in range(20):
        q = rng.standard_normal((1, dim)).astype(np.float32)
        q /= np.linalg.norm(q)
        loaded.search(q, threshold=0.2, max_results=500)
    lat = []
    for _ in range(iters):
        q = rng.standard_normal((1, dim)).astype(np.float32)
        q /= np.linalg.norm(q)
        t0 = time.perf_counter()
        loaded.search(q, threshold=0.2, max_results=500)
        lat.append((time.perf_counter() - t0) * 1000.0)
    return {
        "photos": n_photos,
        "faces": n_faces,
        "backend": "faiss" if _HAS_FAISS else "numpy",
        "p50_ms": round(percentile(lat, 50), 3),
        "p95_ms": round(percentile(lat, 95), 3),
        "p99_ms": round(percentile(lat, 99), 3),
        "iters": iters,
    }


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dim", type=int, default=512)
    ap.add_argument("--faces-per-photo", type=float, default=3.0)
    ap.add_argument("--iters", type=int, default=500)
    args = ap.parse_args()

    print(f"# วัดเวลาค้นหาเวกเตอร์ใน RAM (dim={args.dim}, faces/photo={args.faces_per_photo}, backend={'faiss' if _HAS_FAISS else 'numpy'})")
    print(f"{'photos':>8} {'faces':>8} {'p50(ms)':>9} {'p95(ms)':>9} {'p99(ms)':>9}")
    for n in (1800, 5000, 10000):
        r = run(n, args.faces_per_photo, args.dim, args.iters)
        print(f"{r['photos']:>8} {r['faces']:>8} {r['p50_ms']:>9} {r['p95_ms']:>9} {r['p99_ms']:>9}")


if __name__ == "__main__":
    main()
