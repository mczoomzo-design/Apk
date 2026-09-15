from __future__ import annotations

from conftest import build_demo
from fastapi.testclient import TestClient


def _client():
    from photosearch.api.app import app
    return TestClient(app)


def _selfie(root, ident=3):
    return (root / "_fixtures" / "demo" / "selfies" / f"identity_{ident}.jpg").read_bytes()


def test_event_and_search_flow(env):
    build_demo(env)
    client = _client()

    r = client.get("/api/events/demo")
    assert r.status_code == 200
    body = r.json()
    assert body["ready"] is True
    assert body["photoCount"] > 0

    root = env / "_data"
    r = client.post(
        "/api/events/demo/search",
        files={"selfie": ("s.jpg", _selfie(root), "image/jpeg")},
    )
    assert r.status_code == 200
    data = r.json()
    assert data["status"] == "ok"
    assert data["total"] > 0
    token = data["searchToken"]

    # ดาวน์โหลดรูปย่อของผลแรกได้ (token media ถูกต้อง)
    first = data["items"][0]
    r = client.get(first["previewUrl"])
    assert r.status_code == 200
    assert r.headers["content-type"].startswith("image/")

    # pagination ผูกกับ generation เดิม
    if data["nextCursor"]:
        r = client.get(f"/api/events/demo/results?token={token}&cursor={data['nextCursor']}")
        assert r.status_code == 200
        assert r.json()["status"] == "ok"


def test_media_token_required(env):
    build_demo(env)
    client = _client()
    root = env / "_data"
    r = client.post("/api/events/demo/search", files={"selfie": ("s.jpg", _selfie(root), "image/jpeg")})
    photo_id = r.json()["items"][0]["photoId"]
    # เข้าถึงรูปโดยไม่มี token → 403
    r = client.get(f"/api/media/preview/demo/{photo_id}?t=bogus")
    assert r.status_code == 403


def test_admin_remove_photo_hides_from_results(env):
    build_demo(env)
    client = _client()
    root = env / "_data"
    headers = {"Authorization": "Bearer test-admin"}

    r = client.post("/api/events/demo/search", files={"selfie": ("s.jpg", _selfie(root), "image/jpeg")})
    items = r.json()["items"]
    target = items[0]["photoId"]

    # ถอนรูป
    r = client.post("/api/admin/events/demo/photos/remove", json=[target], headers=headers)
    assert r.status_code == 200
    assert r.json()["removedCount"] >= 1

    # ค้นใหม่ต้องไม่เห็นรูปที่ถอน (permission TTL อาจ cache — invalidate ทำใน service)
    r = client.post("/api/events/demo/search", files={"selfie": ("s.jpg", _selfie(root), "image/jpeg")})
    found = {it["photoId"] for it in r.json()["items"]}
    assert target not in found

    # media ของรูปที่ถอนต้องถูกปฏิเสธด้วย token เดิมของ preview
    prev_url = items[0]["previewUrl"]
    r = client.get(prev_url)
    assert r.status_code == 403


def test_admin_requires_auth(env):
    build_demo(env)
    client = _client()
    r = client.post("/api/admin/events/demo/photos/remove", json=["x"])
    assert r.status_code == 401


def test_expired_token_page(env):
    build_demo(env)
    client = _client()
    r = client.get("/api/events/demo/results?token=doesnotexist&cursor=0")
    assert r.json()["status"] == "expired"


def test_not_ready_event(env):
    # กิจกรรมที่ไม่มีอยู่
    build_demo(env)
    client = _client()
    r = client.get("/api/events/ghost")
    assert r.status_code == 404


def test_admin_console_endpoints(env):
    build_demo(env)
    client = _client()
    headers = {"Authorization": "Bearer test-admin"}

    # list events
    r = client.get("/api/admin/events", headers=headers)
    assert r.status_code == 200
    events = r.json()["events"]
    assert any(e["eventId"] == "demo" and e["ready"] for e in events)

    # full doc (admin)
    r = client.get("/api/admin/events/demo/full", headers=headers)
    assert r.status_code == 200
    assert r.json()["originalsFolderId"]

    # toggle public
    r = client.post("/api/admin/events/demo/public?public=false", headers=headers)
    assert r.status_code == 200
    assert r.json()["public"] is False

    # list requires auth
    assert client.get("/api/admin/events").status_code == 401


def test_admin_prepare_via_api(env):
    import time as _t

    build_demo(env)
    client = _client()
    headers = {"Authorization": "Bearer test-admin"}
    r = client.post("/api/admin/events/demo/prepare", headers=headers)
    assert r.status_code == 200
    assert r.json()["started"] in (True, False)  # อาจกำลังรันอยู่
    # รอ background เสร็จ
    for _ in range(60):
        st = client.get("/api/admin/events/demo/prepare-status", headers=headers).json()
        if st.get("state") in ("done", "failed") and not st.get("running"):
            break
        _t.sleep(0.5)
    assert st.get("state") == "done"
