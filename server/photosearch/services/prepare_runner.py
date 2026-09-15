"""PrepareRunner — สั่งเตรียมรูปแบบ background จากหน้า Admin

หมายเหตุ: ในโปรดักชันแนะนำให้เตรียมรูปด้วย Cloud Run **Job** (worker แยก) เพื่อไม่แย่ง
ทรัพยากร API ตอนค้นหา ตัวรันนี้มีไว้เพื่อความสะดวกของ Admin/การทดสอบในเครื่อง
กันไม่ให้รันซ้อนกันต่อกิจกรรม (หนึ่ง worker/กิจกรรม + ผู้เผยแพร่เดียว)
"""
from __future__ import annotations

import threading

from ..config import Settings
from ..embedder import Embedder
from ..storage import Storage
from ..worker import PreparePipeline
from .registry import GenerationRegistry


class PrepareRunner:
    def __init__(self, storage: Storage, embedder: Embedder, settings: Settings, registry: GenerationRegistry):
        self.pipeline = PreparePipeline(storage, embedder, settings)
        self.registry = registry
        self._threads: dict[str, threading.Thread] = {}
        self._lock = threading.Lock()

    def is_running(self, event_id: str) -> bool:
        with self._lock:
            t = self._threads.get(event_id)
            return t is not None and t.is_alive()

    def start(self, event_id: str) -> bool:
        """คืน True ถ้าเริ่มงานใหม่, False ถ้ามีงานของกิจกรรมนี้กำลังรันอยู่"""
        with self._lock:
            existing = self._threads.get(event_id)
            if existing is not None and existing.is_alive():
                return False

            def _run():
                try:
                    self.pipeline.run(event_id, progress=lambda s: None)
                finally:
                    # หลังเผยแพร่รุ่นใหม่ ให้ instance นี้เห็นทันที (ล้าง pointer cache)
                    self.registry.invalidate(event_id)

            t = threading.Thread(target=_run, name=f"prepare-{event_id}", daemon=True)
            self._threads[event_id] = t
            t.start()
            return True
