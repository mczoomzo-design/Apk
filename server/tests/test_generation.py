from __future__ import annotations

import json

import numpy as np
import pytest
from conftest import build_demo

from photosearch.config import get_settings
from photosearch.embedder import build_embedder
from photosearch.faceindex import load_generation
from photosearch.faceindex.generation import verify_generation
from photosearch.schemas import Manifest
from photosearch.storage import ConflictError, build_storage
from photosearch.worker import PreparePipeline


def test_generation_checksum_detects_tamper(env):
    status, _, root = build_demo(env)
    gen = status.generationId
    base = f"events/demo/generations/{gen}"
    settings = get_settings()
    storage = build_storage(settings)

    manifest = Manifest(**json.loads(storage.read_path(f"{base}/manifest.json").decode()))
    index_bytes = storage.read_path(f"{base}/index.faiss")
    photos_bytes = storage.read_path(f"{base}/photos.json")
    faces_bytes = storage.read_path(f"{base}/faces-map.json")

    # ปกติต้องผ่าน
    verify_generation(manifest, index_bytes, photos_bytes, faces_bytes)
    # แก้ไฟล์ → ต้องตรวจจับได้
    with pytest.raises(ValueError):
        verify_generation(manifest, index_bytes + b"x", photos_bytes, faces_bytes)


def test_publish_conflict(env):
    build_demo(env)
    settings = get_settings()
    storage = build_storage(settings)
    # current.json มี revision=1 หลัง build_demo → publish ด้วย expected=0 ต้อง conflict
    with pytest.raises(ConflictError):
        storage.publish_current("demo", "gen-x", expected_revision=0)


def test_resume_is_idempotent(env):
    """รัน prepare ซ้ำต้องได้จำนวนเท่าเดิม (checkpoint/jobKey กันซ้ำ)"""
    status1, _, _ = build_demo(env)
    settings = get_settings()
    storage = build_storage(settings)
    pipeline = PreparePipeline(storage, build_embedder(settings), settings)
    status2 = pipeline.run("demo", progress=lambda s: None)
    assert status2.ok == status1.ok
    assert status2.no_face == status1.no_face
    assert status2.failed == 0


def test_index_roundtrip_matches_bruteforce(env):
    """ดัชนีที่บันทึก/โหลดกลับ ให้ผลค้นหาตรงกับ brute-force numpy"""
    status, _, _ = build_demo(env)
    settings = get_settings()
    storage = build_storage(settings)
    loaded = load_generation(storage, "demo", status.generationId)
    # สร้าง query จากเวกเตอร์แถวแรก → ต้องเจอตัวเองด้วยคะแนนสูงสุด ~1.0
    if loaded.index_kind == "npy":
        v0 = loaded.index_obj[0:1]
    else:
        import faiss
        v0 = loaded.index_obj.reconstruct_n(0, 1)
    hits = loaded.search(np.asarray(v0, dtype=np.float32), threshold=0.35, max_results=5)
    assert hits
    assert hits[0].score > 0.99
