"""Local filesystem storage — จำลอง Drive สำหรับพัฒนา/ทดสอบโดยไม่ต้องมี credential

fileId = เส้นทางสัมพัทธ์จาก root (stable). checksum = md5 ของเนื้อไฟล์
publish_current ใช้ os.replace เพื่อให้การสลับ current.json เป็น atomic ในเครื่องเดียว
พร้อมตรวจ expectedRevision (optimistic concurrency)
"""
from __future__ import annotations

import hashlib
import json
import os
import threading
from pathlib import Path
from typing import Iterable

from .base import ConflictError, FileMeta, NotFoundError, Storage


def _md5(data: bytes) -> str:
    return hashlib.md5(data).hexdigest()


class LocalStorage(Storage):
    def __init__(self, root: str):
        self.root = Path(root).expanduser().resolve()
        self.root.mkdir(parents=True, exist_ok=True)
        self._locks: dict[str, threading.Lock] = {}
        self._locks_guard = threading.Lock()

    # fileId คือ logical path ที่ normalize แล้ว
    def _abs(self, file_id: str) -> Path:
        p = (self.root / file_id).resolve()
        if not str(p).startswith(str(self.root)):
            raise NotFoundError(f"path escapes root: {file_id}")
        return p

    def _lock_for(self, key: str) -> threading.Lock:
        with self._locks_guard:
            if key not in self._locks:
                self._locks[key] = threading.Lock()
            return self._locks[key]

    def list_folder(self, folder_id: str) -> Iterable[FileMeta]:
        base = self._abs(folder_id)
        if not base.is_dir():
            return []
        out = []
        for entry in sorted(base.iterdir()):
            if entry.is_file():
                out.append(self._meta(entry))
        return out

    def _meta(self, p: Path) -> FileMeta:
        rel = str(p.relative_to(self.root))
        data = p.read_bytes()
        return FileMeta(
            file_id=rel,
            name=p.name,
            mime_type=_guess_mime(p.name),
            size=p.stat().st_size,
            modified_time=str(int(p.stat().st_mtime)),
            checksum=_md5(data),
        )

    def read_bytes(self, file_id: str) -> bytes:
        p = self._abs(file_id)
        if not p.is_file():
            raise NotFoundError(file_id)
        return p.read_bytes()

    def open_stream(self, file_id: str):
        p = self._abs(file_id)
        if not p.is_file():
            raise NotFoundError(file_id)

        def _gen():
            with p.open("rb") as f:
                while True:
                    chunk = f.read(256 * 1024)
                    if not chunk:
                        break
                    yield chunk

        return _gen()

    def stat(self, file_id: str) -> FileMeta:
        p = self._abs(file_id)
        if not p.is_file():
            raise NotFoundError(file_id)
        return self._meta(p)

    def read_path(self, logical_path: str) -> bytes:
        return self.read_bytes(logical_path)

    def write_path(self, logical_path: str, data: bytes, mime_type: str = "application/octet-stream") -> str:
        p = self._abs(logical_path)
        p.parent.mkdir(parents=True, exist_ok=True)
        tmp = p.with_suffix(p.suffix + ".tmp")
        tmp.write_bytes(data)
        os.replace(tmp, p)
        return logical_path

    def exists(self, logical_path: str) -> bool:
        return self._abs(logical_path).is_file()

    def list_path(self, logical_prefix: str) -> Iterable[FileMeta]:
        base = self._abs(logical_prefix)
        if not base.is_dir():
            return []
        return [self._meta(p) for p in sorted(base.iterdir()) if p.is_file()]

    def publish_current(self, event_id: str, generation_id: str, expected_revision: int) -> int:
        path = f"events/{event_id}/current.json"
        lock = self._lock_for(path)
        with lock:
            cur_rev = 0
            if self.exists(path):
                cur = json.loads(self.read_path(path).decode("utf-8"))
                cur_rev = int(cur.get("revision", 0))
            if cur_rev != expected_revision:
                raise ConflictError(
                    f"expected revision {expected_revision} but found {cur_rev}"
                )
            new_rev = cur_rev + 1
            from datetime import datetime, timezone

            obj = {
                "eventId": event_id,
                "generationId": generation_id,
                "revision": new_rev,
                "publishedAt": datetime.now(timezone.utc).isoformat(),
            }
            self.write_path(
                path,
                json.dumps(obj, ensure_ascii=False, indent=2).encode("utf-8"),
                "application/json",
            )
            return new_rev


def _guess_mime(name: str) -> str:
    lower = name.lower()
    if lower.endswith((".jpg", ".jpeg")):
        return "image/jpeg"
    if lower.endswith(".png"):
        return "image/png"
    if lower.endswith(".webp"):
        return "image/webp"
    if lower.endswith(".json"):
        return "application/json"
    if lower.endswith(".faiss"):
        return "application/octet-stream"
    return "application/octet-stream"
