"""SearchService — แปลงเซลฟีเป็นผลค้นหา พร้อม pagination ผูกกับ generation เดียว

- เซลฟีและเวกเตอร์คำค้นอยู่ใน RAM ระหว่างประมวลผลเท่านั้น ไม่บันทึกถาวร
- ranking ต่อคำค้นเก็บใน RAM แบบ TTL (LRU-ish) เพื่อ pagination ที่คงที่
- ถ้า token หมดอายุ/หาไม่เจอ (เช่น instance อื่น) ตอบ 'expired' ให้ค้นใหม่ (ไม่แกล้งว่าไม่พบ)
"""
from __future__ import annotations

import threading
import time
import uuid
from dataclasses import dataclass, field

import numpy as np

from ..config import Settings
from ..embedder import Embedder
from ..schemas import PageResponse, SearchResponse, SearchResultItem
from .registry import GenerationRegistry


@dataclass
class _Ranking:
    generation_id: str
    event_id: str
    hits: list  # list[(photoId, score)]
    face_count: int
    ts: float = field(default_factory=time.time)


class SearchService:
    def __init__(self, registry: GenerationRegistry, embedder: Embedder, settings: Settings, media):
        self.registry = registry
        self.embedder = embedder
        self.settings = settings
        self.media = media  # MediaService (สำหรับสร้าง url)
        self._rankings: dict[str, _Ranking] = {}
        self._lock = threading.Lock()
        # คิวคำนวณมีขอบเขต — กันคำขอสะสมจนหน่วยความจำหมด
        self._sem = threading.BoundedSemaphore(settings.search_concurrency)
        self._inflight = 0

    def _gc(self) -> None:
        now = time.time()
        ttl = self.settings.search_cache_ttl_s
        with self._lock:
            dead = [k for k, v in self._rankings.items() if now - v.ts > ttl]
            for k in dead:
                self._rankings.pop(k, None)

    def search(self, event_id: str, selfie_bytes: bytes) -> SearchResponse:
        readiness = self.registry.readiness(event_id)
        if not readiness.exists or readiness.event is None:
            return SearchResponse(status="not_ready", message="ไม่พบกิจกรรม")
        if not readiness.ready:
            return SearchResponse(status="not_ready", message="กิจกรรมกำลังเตรียมรูป/โหลดดัชนี")

        # จำกัดงานคำนวณพร้อมกัน — ถ้าเต็มให้ตอบ busy (ไม่สะสมคำขอ)
        if not self._sem.acquire(blocking=False):
            return SearchResponse(status="busy", message="คำขอหนาแน่น กรุณาลองใหม่")
        try:
            faces = self.embedder.detect_and_embed(selfie_bytes)
            if len(faces) == 0:
                return SearchResponse(status="no_face", message="ไม่พบใบหน้าในรูป กรุณาถ่าย/เลือกใหม่")
            if len(faces) > 1:
                return SearchResponse(
                    status="multiple_faces",
                    faceCount=len(faces),
                    message="พบหลายใบหน้า กรุณาใช้รูปที่เห็นใบหน้าคุณคนเดียว",
                )
            loaded = self.registry.get_loaded(event_id)
            if loaded is None:
                return SearchResponse(status="not_ready", message="ดัชนีกำลังโหลด")
            query = np.stack([f.embedding for f in faces]).astype(np.float32)
            hits = loaded.search(query, self.settings.match_threshold, self.settings.max_results)
            # กรองรูปที่ถอน (permission overrides) แยกจากดัชนี
            removed = self.registry.get_removed(event_id)
            hits = [h for h in hits if h.photo_id not in removed]

            token = uuid.uuid4().hex
            ranking = _Ranking(
                generation_id=loaded.generation_id,
                event_id=event_id,
                hits=[(h.photo_id, h.score) for h in hits],
                face_count=len(faces),
            )
            self._gc()
            with self._lock:
                self._rankings[token] = ranking

            page_items, next_cursor = self._page(ranking, 0)
            return SearchResponse(
                status="ok",
                searchToken=token,
                generationId=loaded.generation_id,
                total=len(hits),
                faceCount=len(faces),
                items=page_items,
                nextCursor=next_cursor,
            )
        finally:
            self._sem.release()

    def page(self, token: str, cursor: str) -> PageResponse:
        with self._lock:
            ranking = self._rankings.get(token)
        if ranking is None:
            return PageResponse(status="expired")
        if (time.time() - ranking.ts) > self.settings.search_cache_ttl_s:
            return PageResponse(status="expired")
        try:
            offset = int(cursor) if cursor else 0
        except ValueError:
            offset = 0
        items, next_cursor = self._page(ranking, offset)
        return PageResponse(status="ok", items=items, nextCursor=next_cursor, total=len(ranking.hits))

    def _page(self, ranking: _Ranking, offset: int) -> tuple[list[SearchResultItem], str | None]:
        size = self.settings.page_size
        chunk = ranking.hits[offset : offset + size]
        items = [
            SearchResultItem(
                photoId=pid,
                score=round(score, 4),
                previewUrl=self.media.preview_url(ranking.event_id, pid),
                downloadUrl=self.media.download_url(ranking.event_id, pid),
            )
            for pid, score in chunk
        ]
        next_offset = offset + size
        next_cursor = str(next_offset) if next_offset < len(ranking.hits) else None
        return items, next_cursor
