"""Entrypoint ของ worker เตรียมรูป

รันเป็น process/service แยกจาก API ผู้ใช้ (ไม่แย่งทรัพยากรตอนค้นหา)
โหมดการรัน:
  - once:  ประมวลผลกิจกรรมเดียวแล้วจบ (เหมาะกับ Cloud Run Job)
  - serve: เปิด HTTP เล็ก ๆ ให้สั่งงานเป็นคิว (เหมาะกับ Cloud Run Service)

ตัวอย่าง: python -m photosearch.worker.run once --event E1
"""
from __future__ import annotations

import argparse
import sys

from ..config import get_settings
from ..embedder import build_embedder
from ..storage import build_storage
from .pipeline import PreparePipeline


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(prog="photosearch-worker")
    sub = parser.add_subparsers(dest="cmd", required=True)
    p_once = sub.add_parser("once", help="ประมวลผลกิจกรรมเดียวแล้วจบ")
    p_once.add_argument("--event", required=True)
    args = parser.parse_args(argv)

    settings = get_settings()
    storage = build_storage(settings)
    embedder = build_embedder(settings)
    pipeline = PreparePipeline(storage, embedder, settings)

    if args.cmd == "once":
        def _p(st):
            print(f"[{st.eventId}] {st.state} {st.processed}/{st.total} ok={st.ok} no_face={st.no_face} failed={st.failed}", flush=True)

        status = pipeline.run(args.event, progress=_p)
        print(f"เสร็จ: state={status.state} generation={status.generationId}")
        return 0 if status.state == "done" else 1
    return 2


if __name__ == "__main__":
    sys.exit(main())
