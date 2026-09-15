"""GenerationRegistry — แคชดัชนีที่โหลดแล้วใน RAM ต่อกิจกรรม

หน้าที่:
  - โหลด generation ที่ current.json ชี้ เข้าสู่ RAM และใช้ซ้ำระหว่างคำค้น
  - ตรวจ current.json เป็นระยะ (permission_ttl) เพื่อสลับไป generation ใหม่
    โดยยังค้นจากรุ่นเดิมได้ระหว่างโหลด (เผื่อ RAM สองรุ่นชั่วคราว)
  - อ่าน overrides.json (รูปที่ถอน) แยกจากการสร้างดัชนี → ถอนรูปได้โดยไม่ต้อง reindex
  - ให้สถานะ readiness รายกิจกรรม (instance ที่ยังไม่พร้อมต้องไม่ตอบเหมือน "ไม่พบ")

การรีสตาร์ท instance ต้องกู้ได้จาก Drive ล้วน ๆ (ไม่มี state เฉพาะใน RAM)
"""
from __future__ import annotations

import json
import threading
import time
from dataclasses import dataclass, field
from typing import Optional

from ..config import Settings
from ..faceindex import LoadedGeneration, load_generation
from ..schemas import CurrentPointer, EventDoc
from ..storage import NotFoundError, Storage


@dataclass
class _Cached:
    value: object
    ts: float


@dataclass
class EventReadiness:
    exists: bool
    event: Optional[EventDoc]
    ready: bool
    generation_id: Optional[str]
    photo_count: int = 0
    face_count: int = 0
    reason: str = ""


class GenerationRegistry:
    def __init__(self, storage: Storage, settings: Settings):
        self.storage = storage
        self.settings = settings
        self._loaded: dict[str, LoadedGeneration] = {}
        self._pointer: dict[str, _Cached] = {}
        self._overrides: dict[str, _Cached] = {}
        self._event_docs: dict[str, _Cached] = {}
        self._locks: dict[str, threading.Lock] = {}
        self._guard = threading.Lock()

    def _lock(self, event_id: str) -> threading.Lock:
        with self._guard:
            return self._locks.setdefault(event_id, threading.Lock())

    # ---- event.json ----
    def get_event(self, event_id: str) -> Optional[EventDoc]:
        c = self._event_docs.get(event_id)
        if c and (time.time() - c.ts) < self.settings.permission_ttl_s:
            return c.value  # type: ignore
        try:
            raw = self.storage.read_path(f"events/{event_id}/event.json")
        except NotFoundError:
            return None
        doc = EventDoc(**json.loads(raw.decode("utf-8")))
        self._event_docs[event_id] = _Cached(doc, time.time())
        return doc

    # ---- current.json (pointer) ----
    def get_pointer(self, event_id: str) -> Optional[CurrentPointer]:
        c = self._pointer.get(event_id)
        if c and (time.time() - c.ts) < self.settings.permission_ttl_s:
            return c.value  # type: ignore
        try:
            raw = self.storage.read_path(f"events/{event_id}/current.json")
        except NotFoundError:
            self._pointer[event_id] = _Cached(None, time.time())
            return None
        ptr = CurrentPointer(**json.loads(raw.decode("utf-8")))
        self._pointer[event_id] = _Cached(ptr, time.time())
        return ptr

    # ---- overrides.json (รูปที่ถอน) — แยกจากดัชนี ----
    def get_removed(self, event_id: str) -> set[str]:
        c = self._overrides.get(event_id)
        if c and (time.time() - c.ts) < self.settings.permission_ttl_s:
            return c.value  # type: ignore
        removed: set[str] = set()
        try:
            raw = self.storage.read_path(f"events/{event_id}/overrides.json")
            data = json.loads(raw.decode("utf-8"))
            removed = set(data.get("removedPhotoIds", []))
        except NotFoundError:
            pass
        self._overrides[event_id] = _Cached(removed, time.time())
        return removed

    # ---- โหลด/สลับ generation ----
    def get_loaded(self, event_id: str) -> Optional[LoadedGeneration]:
        ptr = self.get_pointer(event_id)
        if ptr is None:
            return None
        loaded = self._loaded.get(event_id)
        if loaded is not None and loaded.generation_id == ptr.generationId:
            return loaded
        # ต้องโหลดรุ่นใหม่ — ล็อกเฉพาะกิจกรรมนี้ ระหว่างนั้น get_loaded อื่นยังได้รุ่นเดิม
        with self._lock(event_id):
            loaded = self._loaded.get(event_id)
            if loaded is not None and loaded.generation_id == ptr.generationId:
                return loaded
            new_gen = load_generation(self.storage, event_id, ptr.generationId)
            # ตรวจสำเร็จแล้วค่อยสลับ reference (atomic ในระดับ dict assignment)
            self._loaded[event_id] = new_gen
            return new_gen

    def readiness(self, event_id: str) -> EventReadiness:
        event = self.get_event(event_id)
        if event is None:
            return EventReadiness(False, None, False, None, reason="not_found")
        ptr = self.get_pointer(event_id)
        if ptr is None:
            return EventReadiness(True, event, False, None, reason="no_generation")
        try:
            loaded = self.get_loaded(event_id)
        except Exception as e:  # ดัชนีเสีย/โหลดไม่ได้
            return EventReadiness(True, event, False, ptr.generationId, reason=f"load_error:{e}")
        if loaded is None:
            return EventReadiness(True, event, False, ptr.generationId, reason="loading")
        return EventReadiness(
            True,
            event,
            True,
            loaded.generation_id,
            photo_count=loaded.manifest.photoCount,
            face_count=loaded.manifest.faceCount,
        )

    def invalidate(self, event_id: str) -> None:
        self._pointer.pop(event_id, None)
        self._overrides.pop(event_id, None)
        self._event_docs.pop(event_id, None)
