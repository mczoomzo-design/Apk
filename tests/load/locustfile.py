"""Locust load test — ทางเลือกแทน k6 (Python)

สถานการณ์ (เลือกด้วย --tags):
  browse  : ผู้ใช้ทยอยใช้งาน — เปิดกิจกรรม → ค้นหา → เลื่อนดูผล → โหลดรูปย่อ
  burst   : ส่งคำค้นถี่ ๆ เพื่อทดสอบการควบคุมคิว (สังเกตสถานะ busy)

วิธีรัน:
  SELFIE=./identity_3.jpg EVENT=demo \
    locust -f tests/load/locustfile.py --host http://localhost:8000 \
      --users 50 --spawn-rate 5 --run-time 90s --headless

หมายเหตุ: ต้องมี selfie จริงหรือ fixture; ยังไม่ใช่ผลรับรองจนกว่าจะรันกับ backend จริง
"""
from __future__ import annotations

import os

from locust import HttpUser, between, tag, task

EVENT = os.environ.get("EVENT", "demo")
SELFIE = os.environ.get("SELFIE", "./identity_3.jpg")

with open(SELFIE, "rb") as _f:
    SELFIE_BYTES = _f.read()


class Participant(HttpUser):
    wait_time = between(1, 4)

    @tag("browse")
    @task
    def browse_and_search(self):
        self.client.get(f"/api/events/{EVENT}", name="event")
        with self.client.post(
            f"/api/events/{EVENT}/search",
            files={"selfie": ("selfie.jpg", SELFIE_BYTES, "image/jpeg")},
            name="search",
            catch_response=True,
        ) as r:
            if r.status_code != 200:
                r.failure(f"http {r.status_code}")
                return
            body = r.json()
            if body.get("status") not in {"ok", "no_face", "busy"}:
                r.failure(f"status {body.get('status')}")
                return
            token = body.get("searchToken")
            cursor = body.get("nextCursor")
            # โหลดรูปย่อของผลแรก ๆ
            for it in body.get("items", [])[:6]:
                self.client.get(it["previewUrl"], name="preview")
            # เลื่อนดูหน้าถัดไปหนึ่งครั้ง
            if token and cursor:
                self.client.get(
                    f"/api/events/{EVENT}/results?token={token}&cursor={cursor}", name="page"
                )

    @tag("burst")
    @task
    def burst_search(self):
        self.client.post(
            f"/api/events/{EVENT}/search",
            files={"selfie": ("selfie.jpg", SELFIE_BYTES, "image/jpeg")},
            name="search-burst",
        )
