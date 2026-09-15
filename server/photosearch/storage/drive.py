"""Google Drive storage (Shared Drive) — ใช้จริงตอน deploy

ต้องมี:
  - service account key (GOOGLE_APPLICATION_CREDENTIALS)
  - แชร์ Shared Drive / โฟลเดอร์ราก PhotoSearch/ ให้ service account เป็น Content manager
  - DRIVE_ROOT_FOLDER_ID = fileId ของโฟลเดอร์ราก PhotoSearch/

หมายเหตุสำคัญ:
  - อ้างอิงไฟล์ด้วย fileId เสมอ ชื่อไฟล์ซ้ำกันได้
  - การเขียนหลายไฟล์ใน Drive ไม่ใช่ database transaction
  - จุดเผยแพร่ current.json ควรทำผ่าน Apps Script โครงการเดียว (ScriptLock)
    ที่นี่ทำ optimistic-lock ด้วย headRevisionId/revision field เพื่อกันเขียนทับ
    แต่จุด commit เดียวที่ปลอดภัยสุดคือ Apps Script (ดู apps-script/Code.gs)

โค้ดนี้ใช้ google-api-python-client. ยังไม่ถูกรันในชุดทดสอบเพราะไม่มี credential
จึงถือเป็นส่วนที่ "ยังไม่พิสูจน์" จนกว่าจะทดสอบกับบัญชีจริง (ดู docs/status.md)
"""
from __future__ import annotations

import io
import json
import threading
from typing import Iterable, Optional

from .base import ConflictError, FileMeta, NotFoundError, PermissionDeniedError, Storage

try:  # import แบบ optional — local backend ไม่ต้องมีไลบรารีนี้
    from google.oauth2 import service_account  # type: ignore
    from googleapiclient.discovery import build  # type: ignore
    from googleapiclient.errors import HttpError  # type: ignore
    from googleapiclient.http import MediaIoBaseDownload, MediaIoBaseUpload  # type: ignore

    _DRIVE_AVAILABLE = True
except Exception:  # pragma: no cover - ขึ้นกับ environment
    _DRIVE_AVAILABLE = False

SCOPES = ["https://www.googleapis.com/auth/drive"]
FOLDER_MIME = "application/vnd.google-apps.folder"


class DriveStorage(Storage):
    def __init__(self, root_folder_id: str, credentials_file: str = "", credentials_json: str = ""):
        if not _DRIVE_AVAILABLE:
            raise RuntimeError(
                "ต้องติดตั้ง google-api-python-client และ google-auth ก่อนใช้ DriveStorage"
            )
        if not root_folder_id:
            raise RuntimeError("ต้องตั้ง DRIVE_ROOT_FOLDER_ID")
        # รับ credential ได้ 2 ทาง: เนื้อ JSON ผ่าน env (GOOGLE_CREDENTIALS_JSON) หรือ path ไฟล์
        if credentials_json:
            import json as _json

            info = _json.loads(credentials_json)
            creds = service_account.Credentials.from_service_account_info(info, scopes=SCOPES)
        elif credentials_file:
            creds = service_account.Credentials.from_service_account_file(
                credentials_file, scopes=SCOPES
            )
        else:
            raise RuntimeError(
                "ต้องตั้ง GOOGLE_CREDENTIALS_JSON หรือ GOOGLE_APPLICATION_CREDENTIALS อย่างใดอย่างหนึ่ง"
            )
        self.svc = build("drive", "v3", credentials=creds, cache_discovery=False)
        self.root_id = root_folder_id
        # cache: logical path -> fileId (ลด round-trip; ล้างได้เมื่อจำเป็น)
        self._path_cache: dict[str, str] = {"": root_folder_id}
        self._cache_lock = threading.Lock()

    # ---- helper การแปลง path <-> fileId ----
    def _resolve_folder(self, parts: list[str], create: bool) -> str:
        cur = self.root_id
        acc = ""
        for part in parts:
            acc = f"{acc}/{part}" if acc else part
            with self._cache_lock:
                cached = self._path_cache.get(acc)
            if cached:
                cur = cached
                continue
            fid = self._find_child(cur, part, folder=True)
            if fid is None:
                if not create:
                    raise NotFoundError(acc)
                fid = self._create_folder(cur, part)
            with self._cache_lock:
                self._path_cache[acc] = fid
            cur = fid
        return cur

    def _find_child(self, parent: str, name: str, folder: bool) -> Optional[str]:
        q = [
            f"'{parent}' in parents",
            f"name = '{name.replace(chr(39), chr(92) + chr(39))}'",
            "trashed = false",
        ]
        if folder:
            q.append(f"mimeType = '{FOLDER_MIME}'")
        else:
            q.append(f"mimeType != '{FOLDER_MIME}'")
        res = (
            self.svc.files()
            .list(
                q=" and ".join(q),
                spaces="drive",
                corpora="allDrives",
                includeItemsFromAllDrives=True,
                supportsAllDrives=True,
                fields="files(id,name)",
                pageSize=1,
            )
            .execute()
        )
        files = res.get("files", [])
        return files[0]["id"] if files else None

    def _create_folder(self, parent: str, name: str) -> str:
        meta = {"name": name, "mimeType": FOLDER_MIME, "parents": [parent]}
        f = (
            self.svc.files()
            .create(body=meta, fields="id", supportsAllDrives=True)
            .execute()
        )
        return f["id"]

    def _path_to_file_id(self, logical_path: str, create_parents: bool) -> tuple[str, str]:
        parts = [p for p in logical_path.split("/") if p]
        folder = self._resolve_folder(parts[:-1], create=create_parents)
        return folder, parts[-1]

    # ---- Storage interface ----
    def list_folder(self, folder_id: str) -> Iterable[FileMeta]:
        out: list[FileMeta] = []
        page = None
        while True:
            res = (
                self.svc.files()
                .list(
                    q=f"'{folder_id}' in parents and trashed = false and mimeType != '{FOLDER_MIME}'",
                    spaces="drive",
                    corpora="allDrives",
                    includeItemsFromAllDrives=True,
                    supportsAllDrives=True,
                    fields="nextPageToken, files(id,name,mimeType,size,modifiedTime,md5Checksum)",
                    pageSize=1000,
                    pageToken=page,
                )
                .execute()
            )
            for f in res.get("files", []):
                out.append(_meta_from_api(f))
            page = res.get("nextPageToken")
            if not page:
                break
        return out

    def read_bytes(self, file_id: str) -> bytes:
        buf = io.BytesIO()
        try:
            req = self.svc.files().get_media(fileId=file_id, supportsAllDrives=True)
            dl = MediaIoBaseDownload(buf, req, chunksize=1024 * 1024)
            done = False
            while not done:
                _, done = dl.next_chunk()
        except HttpError as e:  # pragma: no cover
            _translate(e, file_id)
        return buf.getvalue()

    def open_stream(self, file_id: str):
        # ดึงเป็น chunk ผ่าน MediaIoBaseDownload แล้ว yield ทีละส่วน
        req = self.svc.files().get_media(fileId=file_id, supportsAllDrives=True)
        buf = io.BytesIO()
        dl = MediaIoBaseDownload(buf, req, chunksize=512 * 1024)

        def _gen():
            done = False
            last = 0
            while not done:
                try:
                    _, done = dl.next_chunk()
                except HttpError as e:  # pragma: no cover
                    _translate(e, file_id)
                data = buf.getvalue()
                if len(data) > last:
                    yield data[last:]
                    last = len(data)

        return _gen()

    def stat(self, file_id: str) -> FileMeta:
        try:
            f = (
                self.svc.files()
                .get(
                    fileId=file_id,
                    fields="id,name,mimeType,size,modifiedTime,md5Checksum",
                    supportsAllDrives=True,
                )
                .execute()
            )
        except HttpError as e:  # pragma: no cover
            _translate(e, file_id)
        return _meta_from_api(f)

    def read_path(self, logical_path: str) -> bytes:
        folder, name = self._path_to_file_id(logical_path, create_parents=False)
        fid = self._find_child(folder, name, folder=False)
        if fid is None:
            raise NotFoundError(logical_path)
        return self.read_bytes(fid)

    def write_path(self, logical_path: str, data: bytes, mime_type: str = "application/octet-stream") -> str:
        folder, name = self._path_to_file_id(logical_path, create_parents=True)
        existing = self._find_child(folder, name, folder=False)
        media = MediaIoBaseUpload(io.BytesIO(data), mimetype=mime_type, resumable=False)
        if existing:
            f = (
                self.svc.files()
                .update(fileId=existing, media_body=media, supportsAllDrives=True, fields="id")
                .execute()
            )
        else:
            meta = {"name": name, "parents": [folder]}
            f = (
                self.svc.files()
                .create(body=meta, media_body=media, supportsAllDrives=True, fields="id")
                .execute()
            )
        return f["id"]

    def exists(self, logical_path: str) -> bool:
        try:
            folder, name = self._path_to_file_id(logical_path, create_parents=False)
        except NotFoundError:
            return False
        return self._find_child(folder, name, folder=False) is not None

    def list_path(self, logical_prefix: str) -> Iterable[FileMeta]:
        parts = [p for p in logical_prefix.split("/") if p]
        folder = self._resolve_folder(parts, create=False)
        return self.list_folder(folder)

    def list_dirs(self, logical_prefix: str) -> list[str]:
        parts = [p for p in logical_prefix.split("/") if p]
        try:
            folder = self._resolve_folder(parts, create=False)
        except NotFoundError:
            return []
        if folder is None:
            return []
        names: list[str] = []
        page = None
        while True:
            res = (
                self.svc.files()
                .list(
                    q=f"'{folder}' in parents and trashed = false and mimeType = '{FOLDER_MIME}'",
                    spaces="drive",
                    corpora="allDrives",
                    includeItemsFromAllDrives=True,
                    supportsAllDrives=True,
                    fields="nextPageToken, files(name)",
                    pageSize=1000,
                    pageToken=page,
                )
                .execute()
            )
            names.extend(f["name"] for f in res.get("files", []))
            page = res.get("nextPageToken")
            if not page:
                break
        return sorted(names)

    def publish_current(self, event_id: str, generation_id: str, expected_revision: int) -> int:
        # หมายเหตุ: จุดเผยแพร่จริงควรทำผ่าน Apps Script (apps-script/Code.gs)
        # ที่นี่รองรับกรณีเรียกตรง โดยอ่าน-ตรวจ-เขียน (ยังไม่ atomic ระดับ Drive)
        path = f"events/{event_id}/current.json"
        cur_rev = 0
        if self.exists(path):
            cur = json.loads(self.read_path(path).decode("utf-8"))
            cur_rev = int(cur.get("revision", 0))
        if cur_rev != expected_revision:
            raise ConflictError(f"expected {expected_revision} found {cur_rev}")
        from datetime import datetime, timezone

        new_rev = cur_rev + 1
        obj = {
            "eventId": event_id,
            "generationId": generation_id,
            "revision": new_rev,
            "publishedAt": datetime.now(timezone.utc).isoformat(),
        }
        self.write_path(
            path, json.dumps(obj, ensure_ascii=False, indent=2).encode("utf-8"), "application/json"
        )
        return new_rev


def _meta_from_api(f: dict) -> FileMeta:
    return FileMeta(
        file_id=f["id"],
        name=f.get("name", ""),
        mime_type=f.get("mimeType", "application/octet-stream"),
        size=int(f["size"]) if f.get("size") else None,
        modified_time=f.get("modifiedTime"),
        checksum=f.get("md5Checksum"),
    )


def _translate(e, ident: str):  # pragma: no cover
    status = getattr(getattr(e, "resp", None), "status", None)
    if status == 404:
        raise NotFoundError(ident)
    if status in (401, 403):
        raise PermissionDeniedError(ident)
    raise e
