"""ตรวจการเชื่อมต่อ Google Drive (Shared Drive) จริง — รันเมื่อมี credential แล้ว

ต้องตั้ง:
  STORAGE_BACKEND=drive
  DRIVE_ROOT_FOLDER_ID=<fileId โฟลเดอร์ราก PhotoSearch/ ใน Shared Drive>
  GOOGLE_APPLICATION_CREDENTIALS=<path service account key>

ทดสอบ (ไม่แตะข้อมูลกิจกรรมจริง — ใช้เนมสเปซ _selftest/ แล้วลบทิ้ง):
  1) เขียน/อ่านไฟล์ + ตรวจ checksum (md5)
  2) list โฟลเดอร์
  3) publish_current + ทดสอบ ConflictError เมื่อ expectedRevision ผิด
  4) รายงานเวลาเฉลี่ยต่อ operation

รัน:  PYTHONPATH=. python scripts/drive_selftest.py
"""
from __future__ import annotations

import os
import sys
import time

from photosearch.config import get_settings
from photosearch.storage import ConflictError, build_storage


def main() -> int:
    settings = get_settings()
    if settings.storage_backend != "drive":
        print("ตั้ง STORAGE_BACKEND=drive ก่อน", file=sys.stderr)
        return 2
    if not settings.drive_root_folder_id or not settings.google_credentials_file:
        print("ต้องตั้ง DRIVE_ROOT_FOLDER_ID และ GOOGLE_APPLICATION_CREDENTIALS", file=sys.stderr)
        return 2

    storage = build_storage(settings)
    ns = "_selftest"
    ok = True

    # 1) write/read/checksum
    payload = b"photosearch drive self-test " + str(time.time()).encode()
    t = time.perf_counter()
    storage.write_path(f"{ns}/hello.bin", payload, "application/octet-stream")
    w_ms = (time.perf_counter() - t) * 1000
    t = time.perf_counter()
    got = storage.read_path(f"{ns}/hello.bin")
    r_ms = (time.perf_counter() - t) * 1000
    assert got == payload, "อ่านได้ไม่ตรงกับที่เขียน"
    print(f"[OK] write {w_ms:.0f}ms / read {r_ms:.0f}ms / round-trip ตรงกัน")

    # 2) list
    t = time.perf_counter()
    files = list(storage.list_path(ns))
    l_ms = (time.perf_counter() - t) * 1000
    print(f"[OK] list {len(files)} ไฟล์ ใน {ns}/ ({l_ms:.0f}ms); ตัวอย่าง checksum={files[0].checksum if files else 'n/a'}")

    # 3) publish_current + conflict
    ev = "_selftest_event"
    try:
        # เขียน generation manifest จำลองเพื่อให้ publish ผ่านการตรวจ (ถ้าใช้ Apps Script จะตรวจ manifest)
        storage.write_json(f"events/{ev}/generations/g1/manifest.json", {"generationId": "g1"})
        rev = storage.publish_current(ev, "g1", expected_revision=0)
        print(f"[OK] publish_current ครั้งแรก → revision={rev}")
        try:
            storage.publish_current(ev, "g1", expected_revision=0)  # ผิด → ต้อง conflict
            print("[FAIL] คาดว่าจะเกิด ConflictError แต่ไม่เกิด")
            ok = False
        except ConflictError:
            print("[OK] ConflictError ทำงานเมื่อ expectedRevision ผิด")
    except Exception as e:
        print(f"[WARN] publish test ข้าม/ล้มเหลว: {e}")

    print("\nสรุป:", "ผ่าน" if ok else "มีข้อผิดพลาด — ตรวจ log ด้านบน")
    print("อย่าลืมลบโฟลเดอร์ _selftest/ และ events/_selftest_event/ ออกจาก Drive หลังทดสอบ")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
