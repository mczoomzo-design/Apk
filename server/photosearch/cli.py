"""CLI สาธิตเส้นทางทั้งระบบในเครื่อง (local storage + mock embedder)

ตัวอย่างครบวงจร:
  python scripts/make_fixtures.py --event demo --photos 60 --identities 10
  python -m photosearch.cli init-event --event demo --originals events/demo/originals
  python -m photosearch.cli prepare --event demo
  python -m photosearch.cli search --event demo --selfie _data/_fixtures/demo/selfies/identity_3.jpg
"""
from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timezone

from .config import get_settings
from .embedder import build_embedder
from .schemas import EventDoc
from .services import EventService, GenerationRegistry, MediaService, SearchService
from .storage import build_storage
from .worker import PreparePipeline


def _ctx():
    settings = get_settings()
    storage = build_storage(settings)
    embedder = build_embedder(settings)
    registry = GenerationRegistry(storage, settings)
    media = MediaService(storage, registry, settings)
    search = SearchService(registry, embedder, settings, media)
    events = EventService(storage, registry)
    return settings, storage, embedder, registry, media, search, events


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(prog="photosearch")
    sub = ap.add_subparsers(dest="cmd", required=True)

    p_init = sub.add_parser("init-event")
    p_init.add_argument("--event", required=True)
    p_init.add_argument("--title", default="กิจกรรมสาธิต")
    p_init.add_argument("--originals", required=True, help="logical path ของโฟลเดอร์ต้นฉบับ")

    p_prep = sub.add_parser("prepare")
    p_prep.add_argument("--event", required=True)

    p_search = sub.add_parser("search")
    p_search.add_argument("--event", required=True)
    p_search.add_argument("--selfie", required=True)

    args = ap.parse_args(argv)
    settings, storage, embedder, registry, media, search, events = _ctx()

    if args.cmd == "init-event":
        doc = EventDoc(
            eventId=args.event,
            title=args.title,
            date=datetime.now(timezone.utc).strftime("%Y-%m-%d"),
            originalsFolderId=args.originals,
            status="open",
            public=True,
        )
        events.create_or_update(doc)
        print(f"สร้างกิจกรรม {args.event} แล้ว (originals={args.originals})")
        return 0

    if args.cmd == "prepare":
        pipeline = PreparePipeline(storage, embedder, settings)
        st = pipeline.run(args.event, progress=lambda s: None)
        print(json.dumps(st.model_dump(), ensure_ascii=False, indent=2))
        return 0 if st.state == "done" else 1

    if args.cmd == "search":
        with open(args.selfie, "rb") as f:
            data = f.read()
        resp = search.search(args.event, data)
        out = resp.model_dump()
        # ตัด url ยาว ๆ ให้อ่านง่าย
        for it in out.get("items", []):
            it["previewUrl"] = it["previewUrl"].split("?")[0] + "?t=..."
            it["downloadUrl"] = it["downloadUrl"].split("?")[0] + "?t=..."
        print(json.dumps(out, ensure_ascii=False, indent=2))
        return 0

    return 2


if __name__ == "__main__":
    sys.exit(main())
