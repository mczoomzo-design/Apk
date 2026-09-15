from __future__ import annotations

import importlib
import json
import os
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "scripts"))


@pytest.fixture()
def env(tmp_path, monkeypatch):
    """ตั้งค่า environment ใหม่ต่อ test + ล้าง settings cache"""
    monkeypatch.setenv("STORAGE_BACKEND", "local")
    monkeypatch.setenv("EMBEDDER", "mock")
    monkeypatch.setenv("LOCAL_ROOT", str(tmp_path / "_data"))
    monkeypatch.setenv("ADMIN_TOKEN", "test-admin")
    monkeypatch.setenv("MEDIA_TOKEN_SECRET", "test-secret")
    monkeypatch.setenv("MATCH_THRESHOLD", "0.35")
    # ล้าง lru_cache ของ settings/context
    import photosearch.config as cfg
    cfg.get_settings.cache_clear()
    import photosearch.api.deps as deps
    deps.get_context.cache_clear()
    return tmp_path


def build_demo(tmp_path, event="demo", photos=60, identities=10, seed=7):
    import make_fixtures  # type: ignore

    root = str(tmp_path / "_data")
    make_fixtures.main  # ensure import
    # เรียกตรงผ่าน argv
    import runpy
    sys.argv = [
        "make_fixtures", "--root", root, "--event", event,
        "--photos", str(photos), "--identities", str(identities), "--seed", str(seed),
    ]
    make_fixtures.main()

    from photosearch.config import get_settings
    from photosearch.embedder import build_embedder
    from photosearch.schemas import EventDoc
    from photosearch.services import EventService, GenerationRegistry
    from photosearch.storage import build_storage
    from photosearch.worker import PreparePipeline

    settings = get_settings()
    storage = build_storage(settings)
    registry = GenerationRegistry(storage, settings)
    events = EventService(storage, registry)
    events.create_or_update(
        EventDoc(eventId=event, title="เดโม", originalsFolderId=f"events/{event}/originals", status="open", public=True)
    )
    pipeline = PreparePipeline(storage, build_embedder(settings), settings)
    status = pipeline.run(event, progress=lambda s: None)
    ground_truth = json.loads((Path(root) / "_fixtures" / event / "ground_truth.json").read_text(encoding="utf-8"))
    return status, ground_truth, Path(root)
