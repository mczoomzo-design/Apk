"""Checkpoint การเตรียมรูป — เก็บใน Drive เพื่อเริ่มต่อได้หลังหยุด

คีย์งาน (jobKey) = eventId + fileId + contentVersion + modelVersion + preprocessingVersion
ทำให้การลองใหม่ไม่สร้างรายการซ้ำ และประมวลผลเฉพาะรูปใหม่/ที่เนื้อหาเปลี่ยน
"""
from __future__ import annotations

import json
from dataclasses import dataclass, field

from ..storage import NotFoundError, Storage


def job_key(event_id: str, file_id: str, content_version: str, model_version: str, preproc: str) -> str:
    return f"{event_id}|{file_id}|{content_version}|{model_version}|{preproc}"


@dataclass
class Checkpoint:
    event_id: str
    entries: dict = field(default_factory=dict)  # jobKey -> entry dict

    @classmethod
    def load(cls, storage: Storage, event_id: str) -> "Checkpoint":
        path = f"events/{event_id}/processing/checkpoints/checkpoint.json"
        try:
            data = json.loads(storage.read_path(path).decode("utf-8"))
            return cls(event_id=event_id, entries=data.get("entries", {}))
        except NotFoundError:
            return cls(event_id=event_id, entries={})

    def save(self, storage: Storage) -> None:
        path = f"events/{self.event_id}/processing/checkpoints/checkpoint.json"
        storage.write_json(path, {"eventId": self.event_id, "entries": self.entries})

    def get(self, key: str):
        return self.entries.get(key)

    def put(self, key: str, entry: dict) -> None:
        self.entries[key] = entry
