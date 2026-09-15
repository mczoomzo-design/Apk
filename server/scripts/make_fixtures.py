"""สร้างข้อมูลจำลอง (SYNTHETIC) สำหรับทดสอบเส้นทางทั้งระบบด้วย mock embedder

*** นี่คือข้อมูลจำลอง ไม่ใช่ใบหน้าจริง ***
ใช้พิสูจน์ pipeline (detect หลายใบหน้า/รูป → index → search → dedupe) เท่านั้น
ห้ามใช้ประเมินความแม่นยำของการจับคู่ใบหน้าจริง

แต่ละ "ใบหน้า" = สี่เหลี่ยมสีจาก palette (index = identity) บนพื้นหลังเทา
เขียนไฟล์ลง _data/events/<event>/originals/ และสร้าง selfies + ground truth
"""
from __future__ import annotations

import argparse
import io
import json
import random
from pathlib import Path

from PIL import Image

# ต้องตรงกับ photosearch/embedder/mock.py
PALETTE = [
    (220, 20, 60), (30, 144, 255), (34, 139, 34), (255, 140, 0), (148, 0, 211),
    (0, 139, 139), (184, 134, 11), (199, 21, 133), (70, 130, 180), (139, 69, 19),
]
BACKGROUND = (128, 128, 128)


def make_group_photo(identities: list[int], w=800, h=600, face=90, gap=30) -> bytes:
    img = Image.new("RGB", (w, h), BACKGROUND)
    px = img.load()
    x = gap
    y = gap
    for ident in identities:
        color = PALETTE[ident % len(PALETTE)]
        for iy in range(y, min(y + face, h)):
            for ix in range(x, min(x + face, w)):
                px[ix, iy] = color
        x += face + gap
        if x + face > w:
            x = gap
            y += face + gap
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=90)
    return buf.getvalue()


def make_selfie(identity: int, size=200, face=120) -> bytes:
    img = Image.new("RGB", (size, size), BACKGROUND)
    px = img.load()
    color = PALETTE[identity % len(PALETTE)]
    off = (size - face) // 2
    for iy in range(off, off + face):
        for ix in range(off, off + face):
            px[ix, iy] = color
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=90)
    return buf.getvalue()


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default="./_data")
    ap.add_argument("--event", default="demo")
    ap.add_argument("--photos", type=int, default=60)
    ap.add_argument("--identities", type=int, default=10)
    ap.add_argument("--seed", type=int, default=7)
    ap.add_argument("--empty-ratio", type=float, default=0.1, help="สัดส่วนรูปที่ไม่มีใบหน้า")
    args = ap.parse_args()

    rng = random.Random(args.seed)
    root = Path(args.root)
    originals = root / "events" / args.event / "originals"
    originals.mkdir(parents=True, exist_ok=True)
    selfies_dir = root / "_fixtures" / args.event / "selfies"
    selfies_dir.mkdir(parents=True, exist_ok=True)

    ident_count = min(args.identities, len(PALETTE))
    ground_truth: dict[str, list[int]] = {}

    for i in range(args.photos):
        if rng.random() < args.empty_ratio:
            data = Image.new("RGB", (400, 300), BACKGROUND)
            b = io.BytesIO()
            data.save(b, format="JPEG")
            (originals / f"photo_{i:04d}.jpg").write_bytes(b.getvalue())
            ground_truth[f"photo_{i:04d}.jpg"] = []
            continue
        n = rng.randint(1, 4)
        idents = [rng.randrange(ident_count) for _ in range(n)]
        (originals / f"photo_{i:04d}.jpg").write_bytes(make_group_photo(idents))
        ground_truth[f"photo_{i:04d}.jpg"] = idents

    for k in range(ident_count):
        (selfies_dir / f"identity_{k}.jpg").write_bytes(make_selfie(k))

    (root / "_fixtures" / args.event / "ground_truth.json").write_text(
        json.dumps(ground_truth, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(f"สร้างรูปจำลอง {args.photos} รูป, {ident_count} identity ที่ {originals}")
    print(f"selfies + ground_truth ที่ {selfies_dir.parent}")


if __name__ == "__main__":
    main()
