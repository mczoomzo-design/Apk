"""พิสูจน์ความถูกต้องของ pipeline บนข้อมูลจำลอง (mock)

หมายเหตุ: นี่วัด "ความถูกต้องของเส้นทางข้อมูล" ไม่ใช่ความแม่นยำของโมเดลจริง
ด้วย mock embedder ที่ deterministic คาดหวัง precision/recall = 1.0 พอดี
(การพิสูจน์ความแม่นยำจริงต้องใช้ insightface + ภาพจริง — ดู docs/testing.md)
"""
from __future__ import annotations

import hashlib

from conftest import build_demo

from photosearch.config import get_settings
from photosearch.embedder import build_embedder
from photosearch.services import GenerationRegistry, MediaService, SearchService
from photosearch.storage import build_storage


def _photo_id(name: str, event="demo") -> str:
    file_id = f"events/{event}/originals/{name}"
    return "p_" + hashlib.sha1(file_id.encode("utf-8")).hexdigest()[:16]


def test_prepare_counts(env):
    status, gt, _ = build_demo(env)
    assert status.state == "done"
    expected_empty = sum(1 for v in gt.values() if len(v) == 0)
    expected_with_face = len(gt) - expected_empty
    assert status.ok == expected_with_face
    assert status.no_face == expected_empty
    assert status.failed == 0


def test_search_precision_recall(env):
    status, gt, _ = build_demo(env)
    settings = get_settings()
    storage = build_storage(settings)
    registry = GenerationRegistry(storage, settings)
    media = MediaService(storage, registry, settings)
    search = SearchService(registry, build_embedder(settings), settings, media)

    # ground truth: identity -> set(photoId)
    ident_to_photos: dict[int, set[str]] = {}
    for name, idents in gt.items():
        for i in idents:
            ident_to_photos.setdefault(i, set()).add(_photo_id(name))

    total_tp = total_fp = total_fn = 0
    fixtures = env / "_data" / "_fixtures" / "demo" / "selfies"
    for ident, expected in ident_to_photos.items():
        selfie = (fixtures / f"identity_{ident}.jpg").read_bytes()
        resp = search.search("demo", selfie)
        assert resp.status == "ok", f"identity {ident}: {resp.status}"
        # ดึงทุกหน้าออกมา
        found = {it.photoId for it in resp.items}
        cursor = resp.nextCursor
        while cursor:
            page = search.page(resp.searchToken, cursor)
            found |= {it.photoId for it in page.items}
            cursor = page.nextCursor
        total_tp += len(found & expected)
        total_fp += len(found - expected)
        total_fn += len(expected - found)

    precision = total_tp / (total_tp + total_fp) if (total_tp + total_fp) else 1.0
    recall = total_tp / (total_tp + total_fn) if (total_tp + total_fn) else 1.0
    # บน mock deterministic ต้องแม่นเต็ม
    assert precision == 1.0, f"precision={precision} fp={total_fp}"
    assert recall == 1.0, f"recall={recall} fn={total_fn}"


def test_multiple_faces_selfie_rejected(env):
    import make_fixtures  # type: ignore

    build_demo(env)
    settings = get_settings()
    storage = build_storage(settings)
    registry = GenerationRegistry(storage, settings)
    media = MediaService(storage, registry, settings)
    search = SearchService(registry, build_embedder(settings), settings, media)

    # เซลฟีที่มี 2 ใบหน้า → ต้องได้ multiple_faces
    two = make_fixtures.make_group_photo([1, 2])
    resp = search.search("demo", two)
    assert resp.status == "multiple_faces"
    assert resp.faceCount == 2


def test_no_face_selfie(env):
    import io
    from PIL import Image

    build_demo(env)
    settings = get_settings()
    storage = build_storage(settings)
    registry = GenerationRegistry(storage, settings)
    media = MediaService(storage, registry, settings)
    search = SearchService(registry, build_embedder(settings), settings, media)

    buf = io.BytesIO()
    Image.new("RGB", (200, 200), (128, 128, 128)).save(buf, format="JPEG")
    resp = search.search("demo", buf.getvalue())
    assert resp.status == "no_face"
