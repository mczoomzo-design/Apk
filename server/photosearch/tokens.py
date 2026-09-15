"""Token ของแอปเราสำหรับลิงก์รูป และ bearer ของผู้ดูแล

media token = token ของแอปเรา (HMAC) ไม่ใช่ Google Drive signed URL
ผูก eventId+photoId+kind+exp เพื่อกันนำลิงก์ไปใช้ข้ามรูป/ข้ามงาน
credential ของ Google ไม่ถูกส่งออกไปหน้าเว็บหรือฝังใน token นี้
"""
from __future__ import annotations

import base64
import hashlib
import hmac
import json
import time


def _b64(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode("ascii").rstrip("=")


def _unb64(s: str) -> bytes:
    pad = "=" * (-len(s) % 4)
    return base64.urlsafe_b64decode(s + pad)


def make_media_token(secret: str, event_id: str, photo_id: str, kind: str, ttl_s: int) -> str:
    payload = {"e": event_id, "p": photo_id, "k": kind, "x": int(time.time()) + ttl_s}
    body = _b64(json.dumps(payload, separators=(",", ":")).encode("utf-8"))
    sig = hmac.new(secret.encode("utf-8"), body.encode("ascii"), hashlib.sha256).digest()
    return f"{body}.{_b64(sig)}"


def verify_media_token(secret: str, token: str, event_id: str, photo_id: str, kind: str) -> bool:
    try:
        body, sig = token.split(".", 1)
    except ValueError:
        return False
    expected = hmac.new(secret.encode("utf-8"), body.encode("ascii"), hashlib.sha256).digest()
    if not hmac.compare_digest(_unb64(sig), expected):
        return False
    try:
        payload = json.loads(_unb64(body).decode("utf-8"))
    except Exception:
        return False
    if payload.get("e") != event_id or payload.get("p") != photo_id or payload.get("k") != kind:
        return False
    if int(payload.get("x", 0)) < int(time.time()):
        return False
    return True
