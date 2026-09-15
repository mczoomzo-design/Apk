"""EventService — งานผู้ดูแล: สร้าง/แก้ไขกิจกรรม เปิด/ปิด ถอน/คืนรูป

การถอนรูปอัปเดต overrides.json (ข้อมูลอนุญาต) แยกจากการสร้างดัชนี
จึงมีผลเร็วโดยไม่ต้อง reindex ทั้งงาน (ตาม TTL ของ permission)
"""
from __future__ import annotations

import json
import threading
from datetime import datetime, timezone
from typing import Optional

from ..schemas import EventDoc
from ..storage import ConflictError, NotFoundError, Storage
from .registry import GenerationRegistry


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


class EventService:
    def __init__(self, storage: Storage, registry: GenerationRegistry):
        self.storage = storage
        self.registry = registry
        self._lock = threading.Lock()

    def create_or_update(self, doc: EventDoc) -> EventDoc:
        path = f"events/{doc.eventId}/event.json"
        existing = None
        try:
            existing = EventDoc(**json.loads(self.storage.read_path(path).decode("utf-8")))
        except NotFoundError:
            pass
        if existing is None:
            doc.createdAt = _now()
        else:
            doc.createdAt = existing.createdAt
        doc.updatedAt = _now()
        self.storage.write_json(path, doc.model_dump())
        self.registry.invalidate(doc.eventId)
        return doc

    def set_status(self, event_id: str, status: str) -> EventDoc:
        doc = self._read(event_id)
        doc.status = status  # type: ignore
        doc.updatedAt = _now()
        self.storage.write_json(f"events/{event_id}/event.json", doc.model_dump())
        self.registry.invalidate(event_id)
        return doc

    def set_public(self, event_id: str, public: bool) -> EventDoc:
        doc = self._read(event_id)
        doc.public = public
        doc.updatedAt = _now()
        self.storage.write_json(f"events/{event_id}/event.json", doc.model_dump())
        self.registry.invalidate(event_id)
        return doc

    def remove_photos(self, event_id: str, photo_ids: list[str]) -> set[str]:
        return self._update_overrides(event_id, add=set(photo_ids))

    def restore_photos(self, event_id: str, photo_ids: list[str]) -> set[str]:
        return self._update_overrides(event_id, remove=set(photo_ids))

    def _update_overrides(self, event_id: str, add: set[str] = frozenset(), remove: set[str] = frozenset()) -> set[str]:
        path = f"events/{event_id}/overrides.json"
        with self._lock:
            current: set[str] = set()
            revision = 0
            try:
                data = json.loads(self.storage.read_path(path).decode("utf-8"))
                current = set(data.get("removedPhotoIds", []))
                revision = int(data.get("revision", 0))
            except NotFoundError:
                pass
            current |= add
            current -= remove
            self.storage.write_json(
                path,
                {
                    "eventId": event_id,
                    "removedPhotoIds": sorted(current),
                    "revision": revision + 1,
                    "updatedAt": _now(),
                },
            )
        self.registry.invalidate(event_id)
        return current

    def _read(self, event_id: str) -> EventDoc:
        raw = self.storage.read_path(f"events/{event_id}/event.json")
        return EventDoc(**json.loads(raw.decode("utf-8")))
