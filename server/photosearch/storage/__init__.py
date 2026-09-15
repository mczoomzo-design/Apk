"""เลือก backend เก็บข้อมูลกิจกรรมตามการตั้งค่า"""
from __future__ import annotations

from ..config import Settings
from .base import (
    ConflictError,
    FileMeta,
    NotFoundError,
    PermissionDeniedError,
    Storage,
)
from .local import LocalStorage


def build_storage(settings: Settings) -> Storage:
    if settings.storage_backend == "drive":
        from .drive import DriveStorage

        return DriveStorage(
            settings.drive_root_folder_id,
            settings.google_credentials_file,
            credentials_json=settings.google_credentials_json,
        )
    return LocalStorage(settings.local_root)


__all__ = [
    "Storage",
    "FileMeta",
    "ConflictError",
    "NotFoundError",
    "PermissionDeniedError",
    "LocalStorage",
    "build_storage",
]
