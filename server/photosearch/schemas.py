"""Schema สำหรับ request/response และรูปแบบข้อมูลถาวรใน Drive

แยกชนิดข้อมูลชัดเจน ตรวจ eventId/photoId และความสัมพันธ์ก่อนใช้งาน
"""
from __future__ import annotations

from typing import Literal, Optional

from pydantic import BaseModel, Field

# ---------------------------------------------------------------------------
# รูปแบบข้อมูลถาวร (เก็บเป็น JSON ใน Drive)
# ---------------------------------------------------------------------------


class EventDoc(BaseModel):
    """events/EVENT_ID/event.json"""

    schemaVersion: int = 1
    eventId: str
    title: str
    date: Optional[str] = None
    coverPreviewId: Optional[str] = None
    # โฟลเดอร์ต้นฉบับ: อ้างอิงโฟลเดอร์เดิมได้โดยไม่ย้ายรูป
    originalsFolderId: str
    status: Literal["draft", "open", "closed"] = "draft"
    # true = เปิดให้ค้นหาสาธารณะผ่านลิงก์/QR
    public: bool = True
    createdAt: Optional[str] = None
    updatedAt: Optional[str] = None


class PhotoRecord(BaseModel):
    """หนึ่งรายการใน photos.json"""

    eventId: str
    photoId: str
    originalFileId: str
    previewFileId: Optional[str] = None
    # เวอร์ชันเนื้อหา (md5/checksum ของต้นฉบับ) ใช้ตรวจว่าเนื้อหาเปลี่ยนไหม
    contentVersion: str
    width: Optional[int] = None
    height: Optional[int] = None
    faceIds: list[str] = Field(default_factory=list)
    # ok = พร้อมค้นหา, no_face, failed, removed (ถอนออกจากผลค้นหา)
    processingStatus: Literal["ok", "no_face", "failed", "removed"] = "ok"
    modelId: Optional[str] = None
    takenAt: Optional[str] = None


class FaceEntry(BaseModel):
    """หนึ่งรายการใน faces-map.json — หนึ่งรูปมีได้หลาย faceId"""

    faceId: str
    photoId: str
    # แถวของเวกเตอร์ในดัชนี FAISS (0-based) ตรงกับลำดับที่ add เข้า index
    vectorRow: int
    bbox: Optional[list[float]] = None  # [x1,y1,x2,y2] บนภาพวิเคราะห์
    detScore: Optional[float] = None


class Manifest(BaseModel):
    """generations/GEN/manifest.json"""

    schemaVersion: int = 1
    generationId: str
    eventId: str
    modelId: str
    modelVersion: str
    preprocessingVersion: str
    vectorDimension: int
    metric: Literal["ip", "l2"] = "ip"
    normalization: Literal["l2", "none"] = "l2"
    photoCount: int
    faceCount: int
    # checksum ของไฟล์ประกอบ ตรวจก่อนโหลด
    checksums: dict[str, str] = Field(default_factory=dict)
    createdAt: Optional[str] = None


class CurrentPointer(BaseModel):
    """events/EVENT_ID/current.json — ชี้ generation ที่ตรวจครบแล้วเท่านั้น"""

    eventId: str
    generationId: str
    # revision ใช้ตรวจการเขียนทับ (optimistic concurrency)
    revision: int = 0
    publishedAt: Optional[str] = None


# ---------------------------------------------------------------------------
# API request/response
# ---------------------------------------------------------------------------


class EventPublic(BaseModel):
    eventId: str
    title: str
    date: Optional[str] = None
    coverUrl: Optional[str] = None
    status: str
    ready: bool
    photoCount: int
    faceCount: int
    generationId: Optional[str] = None
    updatedAt: Optional[str] = None


class SearchResultItem(BaseModel):
    photoId: str
    score: float
    previewUrl: str
    downloadUrl: str


class SearchResponse(BaseModel):
    # สถานะแยกกรณีชัดเจน
    status: Literal[
        "ok",              # ค้นหาสำเร็จ (อาจ 0 ผลลัพธ์ = ไม่พบรูปใกล้เคียง)
        "no_face",         # เซลฟีไม่มีใบหน้า
        "multiple_faces",  # เซลฟีมีหลายใบหน้า ให้เลือก/ส่งใหม่
        "not_ready",       # กิจกรรมยังเตรียมรูปไม่เสร็จ/ดัชนียังไม่โหลด
        "busy",            # คำขอหนาแน่น ให้ลองใหม่
    ]
    searchToken: Optional[str] = None
    generationId: Optional[str] = None
    total: int = 0
    faceCount: int = 0
    items: list[SearchResultItem] = Field(default_factory=list)
    nextCursor: Optional[str] = None
    message: Optional[str] = None


class PageResponse(BaseModel):
    status: Literal["ok", "expired", "not_ready"]
    items: list[SearchResultItem] = Field(default_factory=list)
    nextCursor: Optional[str] = None
    total: int = 0


class PrepareRequest(BaseModel):
    eventId: str
    # full = สแกนทั้งหมด, incremental = เฉพาะที่เปลี่ยน (ใช้ Drive changes)
    mode: Literal["full", "incremental"] = "full"


class PrepareStatus(BaseModel):
    eventId: str
    state: Literal["idle", "running", "publishing", "done", "failed"]
    total: int = 0
    processed: int = 0
    ok: int = 0
    no_face: int = 0
    failed: int = 0
    generationId: Optional[str] = None
    activeGenerationId: Optional[str] = None
    message: Optional[str] = None
    updatedAt: Optional[str] = None
