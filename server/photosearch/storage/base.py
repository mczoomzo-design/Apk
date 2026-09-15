"""Storage abstraction — ขอบเขตที่ทำให้ข้อมูลกิจกรรมอยู่ Drive เพียงแห่งเดียว

ทั้ง local (ทดสอบ) และ drive (ใช้จริง) ต้อง implement interface นี้เหมือนกัน
ทุกที่ในระบบอ้างอิงไฟล์ด้วย fileId ไม่ใช่ชื่อไฟล์ (ชื่อใน Drive ซ้ำกันได้)
"""
from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Iterable, Optional, Protocol


@dataclass
class FileMeta:
    file_id: str
    name: str
    mime_type: str
    size: Optional[int] = None
    modified_time: Optional[str] = None
    # md5/checksum ใช้เป็น contentVersion — เป็น None ได้ถ้า backend ไม่ให้
    checksum: Optional[str] = None


class ConflictError(Exception):
    """expectedRevision ไม่ตรง — มีผู้เขียนทับไปแล้ว"""


class NotFoundError(Exception):
    pass


class PermissionDeniedError(Exception):
    pass


class Storage(Protocol):
    """สัญญาการเข้าถึงข้อมูลกิจกรรมใน Drive

    เส้นทางเชิงตรรกะ (logical path) เช่น
    "events/E1/generations/G1/index.faiss" ใช้ระบุตำแหน่งภายในต้นไม้ PhotoSearch/
    backend แปลงเป็น fileId จริงเอง
    """

    def list_folder(self, folder_id: str) -> Iterable[FileMeta]:
        """คืนไฟล์ในโฟลเดอร์ (ใช้กับโฟลเดอร์ต้นฉบับ)"""
        ...

    def read_bytes(self, file_id: str) -> bytes:
        ...

    def open_stream(self, file_id: str):
        """คืน iterator ของ chunk bytes สำหรับ streaming download"""
        ...

    def stat(self, file_id: str) -> FileMeta:
        ...

    # ---- การเข้าถึงตามเส้นทางเชิงตรรกะภายในต้นไม้ PhotoSearch/ ----
    def read_path(self, logical_path: str) -> bytes:
        ...

    def write_path(self, logical_path: str, data: bytes, mime_type: str = "application/octet-stream") -> str:
        """เขียน/แทนที่ไฟล์ตามเส้นทาง คืน fileId"""
        ...

    def exists(self, logical_path: str) -> bool:
        ...

    def list_path(self, logical_prefix: str) -> Iterable[FileMeta]:
        ...

    def read_json(self, logical_path: str) -> dict:
        return json.loads(self.read_bytes_by_path(logical_path).decode("utf-8"))

    def read_bytes_by_path(self, logical_path: str) -> bytes:
        return self.read_path(logical_path)

    def write_json(self, logical_path: str, obj: dict) -> str:
        data = json.dumps(obj, ensure_ascii=False, indent=2).encode("utf-8")
        return self.write_path(logical_path, data, mime_type="application/json")

    def publish_current(self, event_id: str, generation_id: str, expected_revision: int) -> int:
        """อัปเดต current.json แบบ optimistic-locked

        ในระบบจริงจุดนี้ทำผ่าน Apps Script โครงการเดียว (ScriptLock + expectedRevision)
        การเขียนหลายไฟล์ใน Drive ไม่ใช่ transaction — จึงมีจุดเผยแพร่เดียวเท่านั้น
        คืน revision ใหม่ ถ้า expected ไม่ตรงให้โยน ConflictError
        """
        ...
