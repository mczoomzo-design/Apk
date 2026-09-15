"""FastAPI application — API ผู้ใช้ + ผู้ดูแล + media + health

หน้าเว็บผู้ร่วมกิจกรรมเรียก API เหล่านี้โดยตรง (ไม่วิ่งผ่าน Apps Script)
ควร host หน้าเว็บและ API ให้อยู่ origin เดียวกันเมื่อทำได้
"""
from __future__ import annotations

from typing import Optional

from fastapi import Depends, FastAPI, File, Header, HTTPException, Query, UploadFile
from fastapi.responses import StreamingResponse

from ..config import get_settings
from ..schemas import EventDoc, EventPublic, PageResponse, PrepareRequest, SearchResponse
from ..storage import NotFoundError
from .deps import AppContext, get_context
from ..services.media import DeniedError, NotReadyError

app = FastAPI(title="PhotoSearch API", version="0.1.0")

MAX_SELFIE_BYTES = 12 * 1024 * 1024
ALLOWED_SELFIE = {"image/jpeg", "image/png", "image/webp"}


def _require_admin(authorization: Optional[str], ctx: AppContext) -> None:
    token = ctx.settings.admin_token
    if not token:
        raise HTTPException(503, "ยังไม่ตั้ง ADMIN_TOKEN")
    if authorization != f"Bearer {token}":
        raise HTTPException(401, "unauthorized")


# --------------------------- health / readiness ---------------------------
@app.get("/api/health")
def health():
    return {"status": "ok"}


@app.get("/api/ready")
def ready(event: Optional[str] = None, ctx: AppContext = Depends(get_context)):
    # ไม่มี event → พร้อมรับ traffic ระดับ process
    if not event:
        return {"status": "ok"}
    r = ctx.registry.readiness(event)
    return {"event": event, "ready": r.ready, "reason": r.reason, "generationId": r.generation_id}


# --------------------------- ผู้ร่วมกิจกรรม ---------------------------
@app.get("/api/events/{event_id}", response_model=EventPublic)
def get_event(event_id: str, ctx: AppContext = Depends(get_context)):
    r = ctx.registry.readiness(event_id)
    if not r.exists or r.event is None:
        raise HTTPException(404, "ไม่พบกิจกรรม")
    ev = r.event
    cover = None
    if ev.coverPreviewId:
        cover = ctx.media.preview_url(event_id, "cover")
    return EventPublic(
        eventId=ev.eventId,
        title=ev.title,
        date=ev.date,
        coverUrl=cover,
        status=ev.status,
        ready=r.ready,
        photoCount=r.photo_count,
        faceCount=r.face_count,
        generationId=r.generation_id,
        updatedAt=ev.updatedAt,
    )


@app.post("/api/events/{event_id}/search", response_model=SearchResponse)
async def search(event_id: str, selfie: UploadFile = File(...), ctx: AppContext = Depends(get_context)):
    if selfie.content_type not in ALLOWED_SELFIE:
        raise HTTPException(415, "ชนิดไฟล์ไม่รองรับ (ใช้ JPEG/PNG/WebP)")
    data = await selfie.read(MAX_SELFIE_BYTES + 1)
    if len(data) > MAX_SELFIE_BYTES:
        raise HTTPException(413, "ไฟล์ใหญ่เกินไป")
    if not data:
        raise HTTPException(400, "ไฟล์ว่าง")
    resp = ctx.search.search(event_id, data)
    # เซลฟีไม่ถูกเก็บถาวร — ตัวแปร data หลุด scope หลังจบ request
    return resp


@app.get("/api/events/{event_id}/results", response_model=PageResponse)
def results_page(
    event_id: str,
    token: str = Query(...),
    cursor: str = Query(""),
    ctx: AppContext = Depends(get_context),
):
    return ctx.search.page(token, cursor)


# --------------------------- media ---------------------------
@app.get("/api/media/preview/{event_id}/{photo_id}")
def media_preview(event_id: str, photo_id: str, t: str = Query(...), ctx: AppContext = Depends(get_context)):
    try:
        data, mime = ctx.media.get_preview(event_id, photo_id, t)
    except DeniedError as e:
        raise HTTPException(403, str(e))
    except NotReadyError as e:
        raise HTTPException(503, str(e))
    except NotFoundError:
        raise HTTPException(404, "ไม่พบรูป")
    return _img_response(data, mime)


@app.get("/api/media/original/{event_id}/{photo_id}")
def media_original(event_id: str, photo_id: str, t: str = Query(...), ctx: AppContext = Depends(get_context)):
    try:
        stream = ctx.media.stream_original(event_id, photo_id, t)
    except DeniedError as e:
        raise HTTPException(403, str(e))
    except NotReadyError as e:
        raise HTTPException(503, str(e))
    except NotFoundError:
        raise HTTPException(404, "ไม่พบรูป")
    return StreamingResponse(
        stream,
        media_type="image/jpeg",
        headers={"Content-Disposition": f'attachment; filename="{photo_id}.jpg"'},
    )


def _img_response(data: bytes, mime: str):
    from fastapi.responses import Response

    return Response(content=data, media_type=mime, headers={"Cache-Control": "private, max-age=300"})


# --------------------------- ผู้ดูแล ---------------------------
@app.post("/api/admin/events", response_model=EventDoc)
def admin_upsert_event(doc: EventDoc, authorization: Optional[str] = Header(None), ctx: AppContext = Depends(get_context)):
    _require_admin(authorization, ctx)
    return ctx.events.create_or_update(doc)


@app.post("/api/admin/events/{event_id}/status")
def admin_set_status(event_id: str, status: str = Query(...), authorization: Optional[str] = Header(None), ctx: AppContext = Depends(get_context)):
    _require_admin(authorization, ctx)
    if status not in {"draft", "open", "closed"}:
        raise HTTPException(400, "status ไม่ถูกต้อง")
    return ctx.events.set_status(event_id, status).model_dump()


@app.post("/api/admin/events/{event_id}/photos/remove")
def admin_remove_photos(event_id: str, photo_ids: list[str], authorization: Optional[str] = Header(None), ctx: AppContext = Depends(get_context)):
    _require_admin(authorization, ctx)
    removed = ctx.events.remove_photos(event_id, photo_ids)
    return {"eventId": event_id, "removedCount": len(removed)}


@app.post("/api/admin/events/{event_id}/photos/restore")
def admin_restore_photos(event_id: str, photo_ids: list[str], authorization: Optional[str] = Header(None), ctx: AppContext = Depends(get_context)):
    _require_admin(authorization, ctx)
    removed = ctx.events.restore_photos(event_id, photo_ids)
    return {"eventId": event_id, "removedCount": len(removed)}


@app.get("/api/admin/events/{event_id}/prepare-status")
def admin_prepare_status(event_id: str, authorization: Optional[str] = Header(None), ctx: AppContext = Depends(get_context)):
    _require_admin(authorization, ctx)
    try:
        raw = ctx.storage.read_path(f"events/{event_id}/processing/status.json")
    except NotFoundError:
        return {"eventId": event_id, "state": "idle"}
    import json

    return json.loads(raw.decode("utf-8"))


# --------------------------- เสิร์ฟหน้าเว็บ (origin เดียวกัน) ---------------------------
# ตั้ง WEB_DIST ชี้ไปยัง web/dist เพื่อให้ FastAPI เสิร์ฟทั้งหน้าเว็บและ API ที่ origin เดียว
def _mount_web() -> None:
    import os
    from pathlib import Path

    dist = os.environ.get("WEB_DIST")
    if not dist:
        return
    dist_path = Path(dist)
    index = dist_path / "index.html"
    if not index.is_file():
        return
    from fastapi.responses import FileResponse
    from fastapi.staticfiles import StaticFiles

    app.mount("/assets", StaticFiles(directory=str(dist_path / "assets")), name="assets")

    @app.get("/{full_path:path}")
    def spa(full_path: str):
        # ปล่อยเส้นทาง /api ให้ router อื่นจัดการ; ที่เหลือคืน index.html (SPA fallback)
        if full_path.startswith("api/"):
            raise HTTPException(404, "not found")
        candidate = dist_path / full_path
        if full_path and candidate.is_file():
            return FileResponse(str(candidate))
        return FileResponse(str(index))


_mount_web()
