#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Epson waste-ink counter reader  (READ-ONLY / อ่านอย่างเดียว)
============================================================

อ่านค่า "ตัวนับซับหมึก" (waste ink pad counter) และระดับหมึกของเครื่องพิมพ์
Epson ที่ต่อผ่านสาย USB บน Windows

*** โปรแกรมนี้อ่านอย่างเดียว ไม่เขียน ไม่รีเซ็ต ไม่แก้ค่าใด ๆ ลงเครื่องพิมพ์ ***
จึงไม่มีความเสี่ยงต่อฮาร์ดแวร์เลย ใช้ดูว่าซับหมึกเต็มกี่ % เพื่อตัดสินใจว่า
ถึงเวลาต้องล้าง/เปลี่ยนแผ่นซับและรีเซ็ตหรือยัง

รองรับ Epson L3150 / L3151 และรุ่นอื่นที่ไลบรารี ez-reset รองรับ

การติดตั้ง (ครั้งเดียว):
    git clone https://github.com/CiRIP/ez-reset
    cd ez-reset
    pip install .

การใช้งาน:
    python check_waste_ink.py            # เลือกเครื่องพิมพ์อัตโนมัติ/แบบเมนู
    python check_waste_ink.py --list     # แสดงรายชื่อเครื่องพิมพ์ USB ที่เจอ
    python check_waste_ink.py --index 0  # เลือกเครื่องพิมพ์ตามลำดับที่ต้องการ

หมายเหตุ: ต้องรันบน Windows เพราะการต่อ USB ใช้ Windows USBPRINT API
(ผ่านไลบรารี ez_reset.win_usbprint)
"""

from __future__ import annotations

import argparse
import sys

READ_ONLY_BANNER = (
    "โหมดอ่านอย่างเดียว (READ-ONLY) — ไม่มีการเขียน/รีเซ็ตค่าใด ๆ ลงเครื่องพิมพ์"
)

# เกณฑ์เตือนของ % ซับหมึก
WARN_PERCENT = 85.0   # ใกล้เต็ม
FULL_PERCENT = 100.0  # เต็ม


def _die(msg: str, code: int = 1) -> None:
    print(msg, file=sys.stderr)
    raise SystemExit(code)


def _load_ez_reset():
    """โหลดไลบรารี ez-reset พร้อมข้อความช่วยเหลือถ้าไม่พบ/ไม่ใช่ Windows."""
    try:
        from ez_reset.d4 import D4ControlBackend
        from ez_reset.devices import by_model
        from ez_reset.printer import Printer
        from ez_reset.status import ConsumableStatus, PrinterError
        from ez_reset.utils import parse_identifier
        from ez_reset.win_usbprint import USBPRINTTransport, enumerate_printers
    except ImportError as exc:  # noqa: BLE001
        _die(
            "ไม่พบไลบรารี ez-reset หรือกำลังรันบนระบบที่ไม่ใช่ Windows\n"
            f"รายละเอียด: {exc}\n\n"
            "วิธีติดตั้ง (บน Windows):\n"
            "    git clone https://github.com/CiRIP/ez-reset\n"
            "    cd ez-reset\n"
            "    pip install .\n"
        )
    return {
        "D4ControlBackend": D4ControlBackend,
        "by_model": by_model,
        "Printer": Printer,
        "ConsumableStatus": ConsumableStatus,
        "PrinterError": PrinterError,
        "parse_identifier": parse_identifier,
        "USBPRINTTransport": USBPRINTTransport,
        "enumerate_printers": enumerate_printers,
    }


class _Session:
    """เปิด transport + D4 backend ให้เครื่องพิมพ์ตาม path แล้วปิดให้อัตโนมัติ."""

    def __init__(self, api: dict, path: str) -> None:
        self._api = api
        self._path = path
        self._transport = None
        self._backend = None

    def __enter__(self):
        self._transport = self._api["USBPRINTTransport"](self._path).__enter__()
        self._backend = self._api["D4ControlBackend"](self._transport).__enter__()
        return self._backend

    def __exit__(self, exc_type, exc, tb) -> None:
        # ปิดตามลำดับย้อนกลับ ไม่ให้ error ตอนปิดบดบัง error เดิม
        try:
            if self._backend is not None:
                self._backend.__exit__(exc_type, exc, tb)
        finally:
            if self._transport is not None:
                self._transport.__exit__(exc_type, exc, tb)


def _identify(api: dict, path: str) -> dict[str, str]:
    """เปิดเครื่องพิมพ์แล้วอ่าน IEEE-1284 identifier (อ่านอย่างเดียว)."""
    with _Session(api, path) as backend:
        return api["parse_identifier"](backend.identify())


def _choose_printer(api: dict, index: int | None) -> str:
    printers = list(api["enumerate_printers"]())
    if not printers:
        _die(
            "ไม่พบเครื่องพิมพ์ USB เลย\n"
            "- ตรวจว่าเสียบสาย USB และเปิดเครื่องพิมพ์แล้ว\n"
            "- ตรวจว่าติดตั้งไดรเวอร์ Epson แล้ว (เห็นในหน้า Printers & scanners)"
        )

    if index is not None:
        if index < 0 or index >= len(printers):
            _die(f"--index {index} ไม่ถูกต้อง (มีเครื่องพิมพ์ {len(printers)} ตัว: 0..{len(printers) - 1})")
        return printers[index]

    if len(printers) == 1:
        return printers[0]

    # หลายเครื่อง: แสดงเมนูให้เลือก (ระบุชื่อรุ่นจาก identify)
    print("พบเครื่องพิมพ์ USB หลายตัว เลือกตัวที่ต้องการ:\n")
    labels: list[str] = []
    for i, path in enumerate(printers):
        try:
            ident = _identify(api, path)
            label = ident.get("DES") or ident.get("MDL") or path
        except Exception:  # noqa: BLE001
            label = path
        labels.append(label)
        print(f"  [{i}] {label}")
    print()
    try:
        raw = input(f"พิมพ์หมายเลข 0..{len(printers) - 1} แล้ว Enter: ").strip()
        sel = int(raw)
        if sel < 0 or sel >= len(printers):
            raise ValueError
    except (ValueError, EOFError, KeyboardInterrupt):
        _die("ยกเลิก หรือหมายเลขไม่ถูกต้อง")
    return printers[sel]


def _list_printers(api: dict) -> None:
    printers = list(api["enumerate_printers"]())
    if not printers:
        print("ไม่พบเครื่องพิมพ์ USB")
        return
    print(f"พบเครื่องพิมพ์ USB {len(printers)} ตัว:\n")
    for i, path in enumerate(printers):
        try:
            ident = _identify(api, path)
            desc = ident.get("DES", "?")
            model = ident.get("MDL", "?")
            print(f"  [{i}] {desc}  (MDL={model})")
        except Exception as exc:  # noqa: BLE001
            print(f"  [{i}] {path}  (อ่านรุ่นไม่สำเร็จ: {exc})")


def _fmt_ink_level(level, consumable_status_enum) -> str:
    """แปลง InkLevel เป็นข้อความ รองรับค่าพิเศษ (unknown/missing)."""
    status = getattr(level, "status", None)
    lvl = getattr(level, "level", -1)
    if status == consumable_status_enum.MISSING:
        return "ไม่พบตลับ/ถัง"
    if status == consumable_status_enum.UNKNOWN:
        return "ไม่ทราบ (ถัง EcoTank ไม่มีชิปวัด)"
    if lvl is None or lvl < 0:
        return "ไม่ทราบ"
    return f"{lvl}%"


def _bar(percent: float, width: int = 24) -> str:
    percent = max(0.0, min(percent, 100.0))
    filled = int(round(percent / 100.0 * width))
    return "[" + "#" * filled + "-" * (width - filled) + "]"


def _verdict(percent: float) -> str:
    if percent >= FULL_PERCENT:
        return "*** เต็มแล้ว — ต้องล้าง/เปลี่ยนแผ่นซับหมึก แล้วรีเซ็ตตัวนับ ***"
    if percent >= WARN_PERCENT:
        return "ใกล้เต็ม — เตรียมล้างแผ่นซับหมึกเร็ว ๆ นี้"
    return "ปกติ"


def _report(api: dict, path: str) -> int:
    with _Session(api, path) as backend:
        ident = api["parse_identifier"](backend.identify())
        model_id = ident.get("MDL", "")
        device = api["by_model"](model_id)
        printer = api["Printer"](backend, device=device)

        status = printer.get_status()   # อ่านอย่างเดียว
        wastes = printer.get_waste()     # อ่านอย่างเดียว -> [(level, max), ...]

    cs = api["ConsumableStatus"]
    perr = api["PrinterError"]

    print("=" * 56)
    print(f"เครื่องพิมพ์ : {ident.get('DES', '?')}")
    print(f"รุ่น (MDL)   : {model_id or '?'}")
    if getattr(status, "serial", ""):
        print(f"Serial       : {status.serial}")
    print(f"สถานะเครื่อง : {getattr(status.state, 'name', status.state)}")

    err = status.error
    err_name = getattr(err, "name", str(err))
    if err == perr.SERVICEREQ:
        print(f"ข้อผิดพลาด   : {err_name}  <-- สถานะ 'Service Required' (มักคือซับหมึกเต็ม)")
    elif err != perr.NONE:
        print(f"ข้อผิดพลาด   : {err_name}")
    print("=" * 56)

    # ระดับหมึก
    print("\nระดับหมึก:")
    levels = list(getattr(status, "levels", []) or [])
    if not levels:
        print("  (ไม่มีข้อมูล)")
    for lv in levels:
        color = getattr(getattr(lv, "color", None), "name", "?")
        print(f"  - {color:<14} {_fmt_ink_level(lv, cs)}")

    # กล่อง/แผ่นซับ maintenance box (ถ้ามี)
    mb = getattr(status, "maintenance_box", None)
    if mb is not None and getattr(mb, "level", -1) >= 0:
        print(f"\nMaintenance box: {mb.level}%")

    # ตัวนับซับหมึก (สำคัญที่สุด)
    print("\nตัวนับซับหมึก (Waste ink pad counter):")
    if not wastes:
        print("  (รุ่นนี้ไม่มีข้อมูลตัวนับซับหมึกในฐานข้อมูล ez-reset)")
    labels = ["ซับหมึกหลัก (Main pad)", "ซับหมึกรอง (Platen/Borderless)"]
    worst = 0.0
    for i, (level, max_level) in enumerate(wastes):
        name = labels[i] if i < len(labels) else f"Counter {i}"
        if not max_level:
            print(f"  - {name}: {level} (ไม่ทราบค่าสูงสุด)")
            continue
        percent = level / max_level * 100.0
        worst = max(worst, percent)
        print(f"  - {name}")
        print(f"      {_bar(percent)}  {percent:6.2f}%   ({level} / {max_level})")
        print(f"      -> {_verdict(percent)}")

    print("\n" + "-" * 56)
    if wastes:
        print(f"สรุป: ซับหมึกที่เต็มที่สุดอยู่ที่ {worst:.2f}%")
        if worst >= FULL_PERCENT:
            print(
                "ต้องดำเนินการ: ล้าง/เปลี่ยนแผ่นซับหมึกจริงในเครื่อง แล้วจึงรีเซ็ตตัวนับ\n"
                "(การรีเซ็ตทำได้ด้วย ez-reset GUI: `python -m ez_reset` หรือ WIC Reset)\n"
                "อย่ารีเซ็ตอย่างเดียวโดยไม่จัดการแผ่นซับ ไม่งั้นหมึกอาจล้นออกมา"
            )
    print(READ_ONLY_BANNER)
    print("-" * 56)
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="อ่านค่าตัวนับซับหมึก Epson ผ่าน USB (อ่านอย่างเดียว)",
    )
    parser.add_argument("--list", action="store_true", help="แสดงรายชื่อเครื่องพิมพ์ USB ที่เจอ แล้วจบ")
    parser.add_argument("--index", type=int, default=None, help="เลือกเครื่องพิมพ์ตามลำดับ (ดูจาก --list)")
    args = parser.parse_args(argv)

    api = _load_ez_reset()

    if args.list:
        _list_printers(api)
        return 0

    path = _choose_printer(api, args.index)
    try:
        return _report(api, path)
    except Exception as exc:  # noqa: BLE001
        _die(
            f"อ่านสถานะไม่สำเร็จ: {exc}\n"
            "- ปิดโปรแกรมอื่นที่กำลังใช้เครื่องพิมพ์ (เช่นคิวงานพิมพ์) แล้วลองใหม่\n"
            "- ตรวจว่าเป็นเครื่อง Epson ที่ต่อ USB จริง\n"
            "- ลอง `python check_waste_ink.py --list` เพื่อดูว่าเลือกถูกตัวไหม"
        )


if __name__ == "__main__":
    raise SystemExit(main())
