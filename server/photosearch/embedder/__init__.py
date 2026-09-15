from __future__ import annotations

from ..config import Settings
from .base import Embedder, Face, l2_normalize


def build_embedder(settings: Settings) -> Embedder:
    if settings.embedder == "insightface":
        from .insightface import InsightFaceEmbedder

        return InsightFaceEmbedder(
            settings.insightface_model, settings.insightface_root, settings.min_face_px
        )
    from .mock import MockEmbedder

    return MockEmbedder(min_face_px=8)


__all__ = ["Embedder", "Face", "build_embedder", "l2_normalize"]
