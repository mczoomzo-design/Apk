"""การตั้งค่าระบบ (อ่านจาก environment variables)

ไม่มีความลับฝังในโค้ด — ค่าที่ต้องใส่จริงระบุใน .env.example และ docs/install.md
"""
from __future__ import annotations

import os
from dataclasses import dataclass, field
from functools import lru_cache


def _get_bool(name: str, default: bool) -> bool:
    v = os.environ.get(name)
    if v is None:
        return default
    return v.strip().lower() in {"1", "true", "yes", "on"}


def _get_int(name: str, default: int) -> int:
    v = os.environ.get(name)
    return int(v) if v not in (None, "") else default


def _get_float(name: str, default: float) -> float:
    v = os.environ.get(name)
    return float(v) if v not in (None, "") else default


@dataclass
class Settings:
    # --- storage ---
    # STORAGE_BACKEND: "local" (ทดสอบในเครื่อง) หรือ "drive" (ใช้จริง)
    storage_backend: str = field(default_factory=lambda: os.environ.get("STORAGE_BACKEND", "local"))
    # local backend: โฟลเดอร์รากที่จำลอง Drive
    local_root: str = field(default_factory=lambda: os.environ.get("LOCAL_ROOT", "./_data"))
    # drive backend: fileId ของโฟลเดอร์ราก PhotoSearch/ ใน Shared Drive (ต้องใส่จริง)
    drive_root_folder_id: str = field(default_factory=lambda: os.environ.get("DRIVE_ROOT_FOLDER_ID", ""))
    # path ไปยัง service account key (mount ตอน deploy, ห้าม commit)
    google_credentials_file: str = field(
        default_factory=lambda: os.environ.get("GOOGLE_APPLICATION_CREDENTIALS", "")
    )
    # ทางเลือก: ใส่เนื้อ JSON ของ service account key ตรง ๆ ผ่าน env (เหมาะกับ secret/remote)
    google_credentials_json: str = field(
        default_factory=lambda: os.environ.get("GOOGLE_CREDENTIALS_JSON", "")
    )

    # --- embedder / โมเดลใบหน้า ---
    # EMBEDDER: "mock" (deterministic ทดสอบได้โดยไม่ต้องมีโมเดล) หรือ "insightface"
    embedder: str = field(default_factory=lambda: os.environ.get("EMBEDDER", "mock"))
    insightface_model: str = field(default_factory=lambda: os.environ.get("INSIGHTFACE_MODEL", "buffalo_l"))
    insightface_root: str = field(default_factory=lambda: os.environ.get("INSIGHTFACE_ROOT", "~/.insightface"))
    # ขนาดขั้นต่ำของใบหน้า (px) ที่จะยอมรับเข้า index — กันใบหน้าเล็กเกินจนไม่มีข้อมูล
    min_face_px: int = field(default_factory=lambda: _get_int("MIN_FACE_PX", 24))

    # --- การค้นหา ---
    # threshold cosine similarity ขั้นต่ำที่ถือว่าเป็นคนเดียวกัน (ต้องสอบเทียบกับรูปจริง)
    match_threshold: float = field(default_factory=lambda: _get_float("MATCH_THRESHOLD", 0.35))
    page_size: int = field(default_factory=lambda: _get_int("PAGE_SIZE", 20))
    max_results: int = field(default_factory=lambda: _get_int("MAX_RESULTS", 500))
    # อายุของ ranking cache ต่อคำค้น (วินาที) — เก็บใน RAM เท่านั้น
    search_cache_ttl_s: int = field(default_factory=lambda: _get_int("SEARCH_CACHE_TTL_S", 300))

    # --- แคชรูปย่อใน RAM ---
    preview_cache_mb: int = field(default_factory=lambda: _get_int("PREVIEW_CACHE_MB", 256))
    preview_max_edge: int = field(default_factory=lambda: _get_int("PREVIEW_MAX_EDGE", 600))

    # --- อายุข้อมูลสิทธิ์ (permission/manifest) ---
    permission_ttl_s: int = field(default_factory=lambda: _get_int("PERMISSION_TTL_S", 60))

    # --- ขอบเขตทรัพยากร ---
    search_concurrency: int = field(default_factory=lambda: _get_int("SEARCH_CONCURRENCY", 8))
    drive_concurrency: int = field(default_factory=lambda: _get_int("DRIVE_CONCURRENCY", 8))
    search_queue_max: int = field(default_factory=lambda: _get_int("SEARCH_QUEUE_MAX", 64))

    # --- auth ---
    # token ของแอปเราสำหรับผู้ดูแล (bearer). ห้ามใช้เป็น Google credential
    admin_token: str = field(default_factory=lambda: os.environ.get("ADMIN_TOKEN", ""))
    # secret สำหรับเซ็น token ลิงก์รูป (media token) — token ของแอปเรา ไม่ใช่ Drive signed URL
    media_token_secret: str = field(default_factory=lambda: os.environ.get("MEDIA_TOKEN_SECRET", "dev-insecure-secret"))
    media_token_ttl_s: int = field(default_factory=lambda: _get_int("MEDIA_TOKEN_TTL_S", 3600))

    def vector_dim(self) -> int:
        return 512 if self.embedder == "insightface" else 128


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
