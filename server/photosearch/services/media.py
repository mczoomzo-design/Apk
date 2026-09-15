"""MediaService — ให้บริการรูปย่อ (แคช RAM) และดาวน์โหลดต้นฉบับ (streaming)

- แคชรูปย่อใน RAM แบบจำกัดขนาด (LRU) ล้างรายการที่ไม่ได้ใช้นาน
- รวมคำขอซ้ำของรูปเดียวกันระหว่างดึงจาก Drive (single-flight) กันยิง Drive ซ้ำ
- ตรวจสิทธิ์กิจกรรม+รูปก่อนอ่านทุกครั้ง แม้รูปยังอยู่ในดัชนีเก่า
- ไม่เปิดโฟลเดอร์ทั้งงานเป็น public; ลิงก์รูปใช้ token ของแอปเรา (มีอายุ)
"""
from __future__ import annotations

import threading
from collections import OrderedDict
from dataclasses import dataclass
from typing import Optional

from ..config import Settings
from ..storage import NotFoundError, PermissionDeniedError, Storage
from .registry import GenerationRegistry
from ..tokens import make_media_token, verify_media_token


class DeniedError(Exception):
    pass


class NotReadyError(Exception):
    pass


@dataclass
class ResolvedPhoto:
    original_file_id: str
    preview_file_id: Optional[str]


class _LRUBytes:
    def __init__(self, max_bytes: int):
        self.max_bytes = max_bytes
        self._d: OrderedDict[str, bytes] = OrderedDict()
        self._size = 0
        self._lock = threading.Lock()

    def get(self, key: str) -> Optional[bytes]:
        with self._lock:
            if key in self._d:
                self._d.move_to_end(key)
                return self._d[key]
            return None

    def put(self, key: str, val: bytes) -> None:
        with self._lock:
            if key in self._d:
                self._size -= len(self._d[key])
                self._d.pop(key)
            self._d[key] = val
            self._size += len(val)
            while self._size > self.max_bytes and self._d:
                _, old = self._d.popitem(last=False)
                self._size -= len(old)


class MediaService:
    def __init__(self, storage: Storage, registry: GenerationRegistry, settings: Settings):
        self.storage = storage
        self.registry = registry
        self.settings = settings
        self._preview_cache = _LRUBytes(settings.preview_cache_mb * 1024 * 1024)
        self._inflight: dict[str, threading.Event] = {}
        self._inflight_guard = threading.Lock()
        # จำกัดจำนวนการดึง Drive พร้อมกันต่อ instance
        self._drive_sem = threading.BoundedSemaphore(settings.drive_concurrency)

    # ---- สร้าง url (token ของแอปเรา) ----
    def preview_url(self, event_id: str, photo_id: str) -> str:
        t = make_media_token(self.settings.media_token_secret, event_id, photo_id, "preview", self.settings.media_token_ttl_s)
        return f"/api/media/preview/{event_id}/{photo_id}?t={t}"

    def download_url(self, event_id: str, photo_id: str) -> str:
        t = make_media_token(self.settings.media_token_secret, event_id, photo_id, "original", self.settings.media_token_ttl_s)
        return f"/api/media/original/{event_id}/{photo_id}?t={t}"

    # ---- ตรวจสิทธิ์ + resolve fileId ----
    def _authorize(self, event_id: str, photo_id: str, kind: str, token: str) -> ResolvedPhoto:
        if not verify_media_token(self.settings.media_token_secret, token, event_id, photo_id, kind):
            raise DeniedError("token ไม่ถูกต้องหรือหมดอายุ")
        event = self.registry.get_event(event_id)
        if event is None or event.status == "closed" or not event.public:
            raise DeniedError("กิจกรรมปิดหรือไม่อนุญาต")
        if photo_id in self.registry.get_removed(event_id):
            raise DeniedError("รูปถูกถอนออกจากผลค้นหา")
        loaded = self.registry.get_loaded(event_id)
        if loaded is None:
            raise NotReadyError("ดัชนีกำลังโหลด")
        rec = loaded.photos.get(photo_id)
        if rec is None or rec.processingStatus != "ok":
            raise DeniedError("รูปไม่พร้อมให้บริการ")
        return ResolvedPhoto(rec.originalFileId, rec.previewFileId)

    # ---- รูปย่อ (แคช + single-flight) ----
    def get_preview(self, event_id: str, photo_id: str, token: str) -> tuple[bytes, str]:
        resolved = self._authorize(event_id, photo_id, "preview", token)
        file_id = resolved.preview_file_id or resolved.original_file_id
        cache_key = f"prev:{file_id}"
        cached = self._preview_cache.get(cache_key)
        if cached is not None:
            return cached, "image/jpeg"
        data = self._single_flight_fetch(cache_key, file_id)
        return data, "image/jpeg"

    def _single_flight_fetch(self, cache_key: str, file_id: str) -> bytes:
        # ถ้ามีคนกำลังดึงอยู่ ให้รอผลร่วมกัน
        while True:
            cached = self._preview_cache.get(cache_key)
            if cached is not None:
                return cached
            with self._inflight_guard:
                ev = self._inflight.get(cache_key)
                if ev is None:
                    ev = threading.Event()
                    self._inflight[cache_key] = ev
                    leader = True
                else:
                    leader = False
            if leader:
                try:
                    with self._drive_sem:
                        data = self.storage.read_bytes(file_id)
                    self._preview_cache.put(cache_key, data)
                    return data
                finally:
                    with self._inflight_guard:
                        self._inflight.pop(cache_key, None)
                    ev.set()
            else:
                ev.wait(timeout=15)
                cached = self._preview_cache.get(cache_key)
                if cached is not None:
                    return cached
                # leader ล้มเหลว/หมดเวลา — วนไปเป็น leader เอง

    # ---- ต้นฉบับ (streaming) ----
    def stream_original(self, event_id: str, photo_id: str, token: str):
        resolved = self._authorize(event_id, photo_id, "original", token)
        with self._drive_sem:
            yield from self.storage.open_stream(resolved.original_file_id)
