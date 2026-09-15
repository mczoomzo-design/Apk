"""ทดสอบความแม่นยำจริงด้วย InsightFace บนชุดภาพใบหน้าจริงที่มี ground truth

รับชุดภาพ + ไฟล์คู่ (pairs CSV: file_x,file_y,Decision[Yes/No]) แล้ววัดด้วย **โค้ดจริง**
ของระบบ (photosearch.embedder.InsightFaceEmbedder + faceindex):

  A) Verification 1:1 — ใช้คู่ที่มี label เพื่อหา distribution ของ cosine similarity
     ระหว่าง "คนเดียวกัน" กับ "คนละคน" → เลือก threshold, รายงาน accuracy / FAR / FRR
  B) Search 1:N — จับกลุ่ม identity จากคู่ Yes (union-find) แล้วจำลองการค้นหาในอัลบั้มงาน
     (query 1 รูป, ที่เหลือ + คนอื่นเป็น gallery) วัด precision/recall ด้วย LoadedGeneration.search

การแยกชุด: เลือก threshold จาก A (calibration) แล้ววัด B ด้วย threshold นั้น (ไม่จูนซ้ำบน B)

ตัวอย่าง (ชุดทดสอบ DeepFace, MIT, ภาพบุคคลสาธารณะ สำหรับงานวิจัย):
  git clone --depth 1 https://github.com/serengil/deepface /tmp/deepface
  PYTHONPATH=. EMBEDDER=insightface python scripts/accuracy_faces.py \
    --dataset /tmp/deepface/tests/unit/dataset \
    --pairs /tmp/deepface/tests/unit/dataset/master.csv

หมายเหตุสิทธิ์: ใช้ภาพที่ได้รับอนุญาต (วิจัย/ไม่พาณิชย์) เท่านั้น ไม่ commit ภาพบุคคลเข้า repo
"""
from __future__ import annotations

import argparse
import csv
import os
import time

import numpy as np

from photosearch.config import get_settings
from photosearch.embedder.insightface import InsightFaceEmbedder
from photosearch.faceindex.manager import LoadedGeneration
from photosearch.schemas import FaceEntry, Manifest, PhotoRecord


class UnionFind:
    def __init__(self):
        self.p: dict[str, str] = {}

    def find(self, x: str) -> str:
        self.p.setdefault(x, x)
        while self.p[x] != x:
            self.p[x] = self.p[self.p[x]]
            x = self.p[x]
        return x

    def union(self, a: str, b: str) -> None:
        ra, rb = self.find(a), self.find(b)
        if ra != rb:
            self.p[ra] = rb


def largest_face(embedder: InsightFaceEmbedder, path: str):
    with open(path, "rb") as f:
        faces = embedder.detect_and_embed(f.read())
    if not faces:
        return None
    return max(faces, key=lambda f: (f.bbox[2] - f.bbox[0]) * (f.bbox[3] - f.bbox[1]))


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dataset", required=True, help="โฟลเดอร์ภาพ")
    ap.add_argument("--pairs", required=True, help="CSV: file_x,file_y,Decision(Yes/No)")
    args = ap.parse_args()

    # อ่านคู่
    pairs: list[tuple[str, str, bool]] = []
    with open(args.pairs, newline="") as f:
        for r in csv.DictReader(f):
            pairs.append((r["file_x"], r["file_y"], r["Decision"].strip().lower() == "yes"))

    files = sorted({p for a, b, _ in pairs for p in (a, b)})
    settings = get_settings()
    embedder = InsightFaceEmbedder(settings.insightface_model, settings.insightface_root, settings.min_face_px)
    print(f"โมเดล {embedder.model_id}/{embedder.model_version} dim={embedder.dim}; ภาพ {len(files)} ไฟล์, คู่ {len(pairs)}", flush=True)

    # embed ทุกไฟล์ (largest face)
    t0 = time.perf_counter()
    emb: dict[str, np.ndarray] = {}
    no_face: list[str] = []
    for name in files:
        path = os.path.join(args.dataset, name)
        if not os.path.isfile(path):
            continue
        f = largest_face(embedder, path)
        if f is None:
            no_face.append(name)
        else:
            emb[name] = f.embedding.astype(np.float32)
    dt = time.perf_counter() - t0
    print(f"embed เสร็จใน {dt:.1f}s ({dt/max(1,len(files))*1000:.0f} ms/รูป), ตรวจไม่พบใบหน้า {len(no_face)}", flush=True)

    # ---- A) Verification ----
    sims_pos, sims_neg = [], []
    for a, b, same in pairs:
        if a in emb and b in emb:
            s = float(emb[a] @ emb[b])  # normalized → cosine
            (sims_pos if same else sims_neg).append(s)
    sims_pos, sims_neg = np.array(sims_pos), np.array(sims_neg)
    print(f"\n# A) Verification 1:1  (คู่คนเดียวกัน {len(sims_pos)}, คนละคน {len(sims_neg)})")
    if len(sims_pos) and len(sims_neg):
        print(f"  cosine คนเดียวกัน: mean={sims_pos.mean():.3f} min={sims_pos.min():.3f}")
        print(f"  cosine คนละคน:   mean={sims_neg.mean():.3f} max={sims_neg.max():.3f}")
    print(f"  {'thr':>5} {'acc':>7} {'FAR':>7} {'FRR':>7}")
    best_thr, best_acc = 0.35, -1.0
    for thr in [round(x, 2) for x in np.arange(0.20, 0.66, 0.05)]:
        tp = (sims_pos >= thr).sum(); fn = (sims_pos < thr).sum()
        fp = (sims_neg >= thr).sum(); tn = (sims_neg < thr).sum()
        acc = (tp + tn) / (tp + tn + fp + fn)
        far = fp / max(1, fp + tn); frr = fn / max(1, tp + fn)
        mark = ""
        if acc > best_acc:
            best_acc, best_thr = acc, thr; mark = "  <= best acc"
        print(f"  {thr:>5} {acc:>7.3f} {far:>7.3f} {frr:>7.3f}{mark}")
    print(f"  → threshold ที่ดีสุดบนคู่ (calibration) = {best_thr} (acc={best_acc:.3f})")

    # ---- B) Search 1:N ----
    uf = UnionFind()
    for a, b, same in pairs:
        if same:
            uf.union(a, b)
    clusters: dict[str, list[str]] = {}
    for name in emb:
        clusters.setdefault(uf.find(name), []).append(name)

    # gallery = ทุกภาพที่ embed ได้; query = 1 ภาพต่อ identity ที่มี >=2 ภาพ
    photos: dict[str, PhotoRecord] = {}
    faces: list[FaceEntry] = []
    vecs: list[np.ndarray] = []
    row = 0
    name_to_pid: dict[str, str] = {}
    for name, v in emb.items():
        pid = name.replace(".", "_")
        name_to_pid[name] = pid
        photos[pid] = PhotoRecord(eventId="acc", photoId=pid, originalFileId=name, contentVersion="1", processingStatus="ok")
        faces.append(FaceEntry(faceId=f"{pid}#0", photoId=pid, vectorRow=row))
        vecs.append(v)
        row += 1
    mat = np.stack(vecs).astype(np.float32)
    manifest = Manifest(
        generationId="acc", eventId="acc", modelId=embedder.model_id, modelVersion=embedder.model_version,
        preprocessingVersion=embedder.preprocessing_version, vectorDimension=embedder.dim,
        photoCount=len(photos), faceCount=len(faces),
    )
    loaded = LoadedGeneration(manifest, photos, faces, "npy", mat)

    queries = []  # (query_name, cluster_id, relevant_pids)
    for cid, members in clusters.items():
        if len(members) >= 2:
            q = members[0]
            rel = {name_to_pid[m] for m in members if m != q}
            queries.append((q, rel))

    print(f"\n# B) Search 1:N  (identity ที่ทดสอบได้ {len(queries)}, gallery {len(photos)} รูป) ที่ threshold={best_thr}")
    tp = fp = fn = 0
    for q, rel in queries:
        hits = loaded.search(emb[q][None, :], threshold=best_thr, max_results=1000)
        found = {h.photo_id for h in hits} - {name_to_pid[q]}  # ไม่นับตัวเอง
        tp += len(found & rel); fp += len(found - rel); fn += len(rel - found)
    prec = tp / max(1, tp + fp); rec = tp / max(1, tp + fn)
    f1 = 2 * prec * rec / max(1e-9, prec + rec)
    print(f"  precision={prec:.3f} recall={rec:.3f} F1={f1:.3f}  (TP={tp} FP={fp} FN={fn})")
    print("\nสรุป: นี่คือผลจริงของ InsightFace บนภาพจริงชุดนี้ ผ่านโค้ดค้นหาจริงของระบบ")
    print("ภาพชุดนี้เป็นหน้าตรงคุณภาพดี — ต้องทดสอบซ้ำกับภาพงานจริง (เบลอ/หันข้าง/ใบหน้าเล็ก/ภาพกลุ่ม)")


if __name__ == "__main__":
    main()
