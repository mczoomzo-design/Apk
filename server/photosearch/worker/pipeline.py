"""Pipeline เตรียมรูป — อ่าน Drive → วิเคราะห์ทุกใบหน้า → รูปย่อ+ดัชนี → เผยแพร่

หลักการ:
  - หนึ่ง worker ต่อกิจกรรม และตัวเผยแพร่เดียว (ลดการเขียนพร้อมกัน)
  - idempotent: ใช้ checkpoint (jobKey) ข้ามรูปที่ทำแล้ว เริ่มต่อได้หลังหยุด
  - แยกกรณี รูปเสีย/ไม่มีใบหน้า/ลบ/ไม่มีสิทธิ์ → รูปที่พร้อมยังเผยแพร่ได้
  - สร้าง generation ใหม่แยกจากรุ่นที่ใช้งาน ตรวจ checksum ก่อน publish
  - การเขียนหลายไฟล์ใน Drive ไม่ใช่ transaction — publish current.json คือจุด commit เดียว
"""
from __future__ import annotations

import hashlib
import io
import json
from datetime import datetime, timezone
from typing import Callable, Optional

import numpy as np

from ..config import Settings
from ..embedder import Embedder
from ..faceindex import build_generation
from ..schemas import EventDoc, FaceEntry, PhotoRecord, PrepareStatus
from ..storage import ConflictError, NotFoundError, PermissionDeniedError, Storage
from .checkpoint import Checkpoint, job_key


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _photo_id(file_id: str) -> str:
    return "p_" + hashlib.sha1(file_id.encode("utf-8")).hexdigest()[:16]


def _make_preview(image_bytes: bytes, max_edge: int) -> tuple[bytes, int, int]:
    from PIL import Image

    img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
    w, h = img.size
    scale = min(1.0, max_edge / max(w, h))
    if scale < 1.0:
        img = img.resize((max(1, int(w * scale)), max(1, int(h * scale))))
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=82)
    return buf.getvalue(), w, h


class PreparePipeline:
    def __init__(self, storage: Storage, embedder: Embedder, settings: Settings):
        self.storage = storage
        self.embedder = embedder
        self.settings = settings

    def _write_status(self, status: PrepareStatus) -> None:
        status.updatedAt = _now()
        self.storage.write_json(f"events/{status.eventId}/processing/status.json", status.model_dump())

    def run(self, event_id: str, progress: Optional[Callable[[PrepareStatus], None]] = None) -> PrepareStatus:
        raw = self.storage.read_path(f"events/{event_id}/event.json")
        event = EventDoc(**json.loads(raw.decode("utf-8")))
        files = list(self.storage.list_folder(event.originalsFolderId))
        # กรองเฉพาะไฟล์รูป
        files = [f for f in files if f.mime_type.startswith("image/")]

        status = PrepareStatus(eventId=event_id, state="running", total=len(files))
        self._write_status(status)

        checkpoint = Checkpoint.load(self.storage, event_id)
        model_version = getattr(self.embedder, "model_version", "1")
        preproc = self.embedder.preprocessing_version

        photos: list[PhotoRecord] = []
        faces: list[FaceEntry] = []
        vectors: list[np.ndarray] = []
        row = 0

        for meta in files:
            content_version = meta.checksum or (meta.modified_time or "0")
            key = job_key(event_id, meta.file_id, content_version, model_version, preproc)
            photo_id = _photo_id(meta.file_id)
            cached = checkpoint.get(key)
            try:
                if cached is not None:
                    entry = cached
                else:
                    entry = self._process_one(event_id, meta, photo_id, content_version, model_version)
                    checkpoint.put(key, entry)
            except PermissionDeniedError:
                entry = {"status": "failed", "reason": "permission", "photoId": photo_id}
            except NotFoundError:
                entry = {"status": "failed", "reason": "deleted", "photoId": photo_id}
            except Exception as e:  # รูปเสีย ฯลฯ — ไม่ให้ล้มทั้งงาน
                entry = {"status": "failed", "reason": f"decode:{e}", "photoId": photo_id}

            status.processed += 1
            st = entry.get("status")
            if st == "ok":
                status.ok += 1
                rec = PhotoRecord(
                    eventId=event_id,
                    photoId=photo_id,
                    originalFileId=meta.file_id,
                    previewFileId=entry.get("previewFileId"),
                    contentVersion=content_version,
                    width=entry.get("width"),
                    height=entry.get("height"),
                    faceIds=[],
                    processingStatus="ok",
                    modelId=self.embedder.model_id,
                )
                for fi, emb in enumerate(entry["embeddings"]):
                    face_id = f"{photo_id}#{fi}"
                    rec.faceIds.append(face_id)
                    faces.append(
                        FaceEntry(
                            faceId=face_id,
                            photoId=photo_id,
                            vectorRow=row,
                            bbox=entry["bboxes"][fi],
                            detScore=entry["detScores"][fi],
                        )
                    )
                    vectors.append(np.asarray(emb, dtype=np.float32))
                    row += 1
                photos.append(rec)
            elif st == "no_face":
                status.no_face += 1
                photos.append(
                    PhotoRecord(
                        eventId=event_id,
                        photoId=photo_id,
                        originalFileId=meta.file_id,
                        previewFileId=entry.get("previewFileId"),
                        contentVersion=content_version,
                        processingStatus="no_face",
                        modelId=self.embedder.model_id,
                    )
                )
            else:
                status.failed += 1

            if progress and status.processed % 25 == 0:
                progress(status)
                self._write_status(status)
            # บันทึก checkpoint เป็นระยะ
            if status.processed % 100 == 0:
                checkpoint.save(self.storage)

        checkpoint.save(self.storage)
        self._record_failures(event_id, checkpoint)

        # ---- สร้าง generation ----
        status.state = "publishing"
        self._write_status(status)
        generation_id = "gen-" + datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S")
        mat = np.stack(vectors) if vectors else np.zeros((0, self.embedder.dim), dtype=np.float32)
        built = build_generation(
            event_id, generation_id, photos, faces, mat,
            self.embedder.model_id, model_version, preproc,
        )
        base = f"events/{event_id}/generations/{generation_id}"
        self.storage.write_json(f"{base}/photos.json", [p.model_dump() for p in photos])
        self.storage.write_json(f"{base}/faces-map.json", [f.model_dump() for f in faces])
        self.storage.write_path(f"{base}/index.faiss", built.index_bytes, "application/octet-stream")
        self.storage.write_json(f"{base}/manifest.json", built.manifest.model_dump())

        # ---- ตรวจครบแล้ว publish (จุด commit เดียว) ----
        expected_rev = self._current_revision(event_id)
        try:
            self.storage.publish_current(event_id, generation_id, expected_rev)
            status.state = "done"
            status.generationId = generation_id
            status.activeGenerationId = generation_id
        except ConflictError as e:
            status.state = "failed"
            status.message = f"publish conflict: {e}"

        self._write_status(status)
        if progress:
            progress(status)
        return status

    def _process_one(self, event_id: str, meta, photo_id: str, content_version: str, model_version: str) -> dict:
        data = self.storage.read_bytes(meta.file_id)
        preview_bytes, w, h = _make_preview(data, self.settings.preview_max_edge)
        preview_path = f"events/{event_id}/previews/{photo_id}.jpg"
        preview_id = self.storage.write_path(preview_path, preview_bytes, "image/jpeg")
        faces = self.embedder.detect_and_embed(data)
        if not faces:
            return {"status": "no_face", "previewFileId": preview_id, "width": w, "height": h}
        return {
            "status": "ok",
            "previewFileId": preview_id,
            "width": w,
            "height": h,
            "embeddings": [f.embedding.tolist() for f in faces],
            "bboxes": [f.bbox for f in faces],
            "detScores": [f.det_score for f in faces],
        }

    def _record_failures(self, event_id: str, checkpoint: Checkpoint) -> None:
        failures = [
            {"jobKey": k, **v}
            for k, v in checkpoint.entries.items()
            if v.get("status") == "failed"
        ]
        self.storage.write_json(f"events/{event_id}/processing/failures/failures.json", {"items": failures})

    def _current_revision(self, event_id: str) -> int:
        try:
            cur = json.loads(self.storage.read_path(f"events/{event_id}/current.json").decode("utf-8"))
            return int(cur.get("revision", 0))
        except NotFoundError:
            return 0
