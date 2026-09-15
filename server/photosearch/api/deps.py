"""ประกอบ dependency ของ API (สร้างครั้งเดียวต่อ process)"""
from __future__ import annotations

from functools import lru_cache

from ..config import Settings, get_settings
from ..embedder import build_embedder
from ..services import EventService, GenerationRegistry, MediaService, SearchService
from ..storage import build_storage


class AppContext:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.storage = build_storage(settings)
        self.embedder = build_embedder(settings)
        self.registry = GenerationRegistry(self.storage, settings)
        self.media = MediaService(self.storage, self.registry, settings)
        self.search = SearchService(self.registry, self.embedder, settings, self.media)
        self.events = EventService(self.storage, self.registry)


@lru_cache(maxsize=1)
def get_context() -> AppContext:
    return AppContext(get_settings())
