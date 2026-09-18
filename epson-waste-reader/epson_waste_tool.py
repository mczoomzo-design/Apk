#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Epson Waste Ink Tool — โปรแกรมจัดการซับหมึก Epson (GUI)
========================================================

โปรแกรมหน้าต่างสำหรับเครื่องพิมพ์ Epson ที่ต่อผ่าน USB บน Windows:
  * ดูระดับหมึกและ % ตัวนับซับหมึก (waste ink pad counter)
  * รีเซ็ตตัวนับซับหมึก (ใช้ค่าที่ยืนยันแล้วของรุ่น เช่น L3150 จาก ez-reset)

รองรับ Epson L3150 / L3151 และรุ่นอื่นที่ไลบรารี ez-reset รองรับ

การติดตั้ง (ครั้งเดียว):
    git clone https://github.com/CiRIP/ez-reset
    cd ez-reset
    pip install .

รัน:
    python epson_waste_tool.py

หรือ build เป็น .exe ด้วย build_exe.bat (ดู README)
"""

from __future__ import annotations

import sys
import tkinter as tk
from tkinter import messagebox, ttk

# สีสำหรับแสดงแถบระดับหมึกแต่ละสี (ชื่อสีจาก InkColor.name)
INK_COLORS = {
    "BLACK": "#333333",
    "CYAN": "#00AEEF",
    "MAGENTA": "#EC008C",
    "YELLOW": "#F5C400",
    "LIGHT_CYAN": "#8FD8F2",
    "LIGHT_MAGENTA": "#F2A6D0",
    "GRAY": "#888888",
    "RED": "#E23B2E",
    "BLUE": "#2E5BE2",
    "ORANGE": "#F08A24",
}

WARN_PERCENT = 85.0
FULL_PERCENT = 100.0

WASTE_LABELS = ["ซับหมึกหลัก (Main pad)", "ซับหมึกรอง (Platen/Borderless)"]


def load_ez_reset():
    """โหลดไลบรารี ez-reset; คืน dict ของสิ่งที่ต้องใช้ หรือ None ถ้าโหลดไม่ได้."""
    try:
        from ez_reset.d4 import D4ControlBackend
        from ez_reset.devices import by_model
        from ez_reset.printer import Printer
        from ez_reset.status import ConsumableStatus, PrinterError
        from ez_reset.utils import parse_identifier
        from ez_reset.win_usbprint import USBPRINTTransport, enumerate_printers
    except ImportError as exc:  # noqa: BLE001
        return None, str(exc)
    return {
        "D4ControlBackend": D4ControlBackend,
        "by_model": by_model,
        "Printer": Printer,
        "ConsumableStatus": ConsumableStatus,
        "PrinterError": PrinterError,
        "parse_identifier": parse_identifier,
        "USBPRINTTransport": USBPRINTTransport,
        "enumerate_printers": enumerate_printers,
    }, None


class WasteTool(ttk.Frame):
    def __init__(self, master: tk.Tk, api: dict) -> None:
        super().__init__(master, padding=10)
        self.master = master
        self.api = api

        # อ็อบเจ็กต์การเชื่อมต่อที่เปิดค้างไว้ระหว่างใช้งาน
        self._transport = None
        self._backend = None
        self._printer = None

        self._paths: list[str] = []
        self._ink_bars: dict[str, tuple[ttk.Progressbar, ttk.Label]] = {}
        self._waste_bars: list[tuple[ttk.Label, ttk.Progressbar, ttk.Label]] = []

        self._build_ui()
        self.scan_printers()

        master.protocol("WM_DELETE_WINDOW", self._on_close)

    # ---------- UI ----------
    def _build_ui(self) -> None:
        style = ttk.Style(self.master)
        try:
            style.theme_use("vista")
        except tk.TclError:
            pass

        # แถวเลือกเครื่องพิมพ์
        top = ttk.Frame(self)
        top.pack(fill="x")
        ttk.Label(top, text="เครื่องพิมพ์:").pack(side="left")
        self.combo = ttk.Combobox(top, state="readonly", width=42)
        self.combo.pack(side="left", padx=6)
        ttk.Button(top, text="สแกนใหม่", command=self.scan_printers).pack(side="left")
        self.btn_connect = ttk.Button(top, text="เชื่อมต่อ", command=self.connect)
        self.btn_connect.pack(side="left", padx=4)

        # แถบสถานะ
        self.status_var = tk.StringVar(value="ยังไม่ได้เชื่อมต่อ")
        ttk.Label(self, textvariable=self.status_var, foreground="#555").pack(
            fill="x", pady=(8, 4)
        )

        # กรอบระดับหมึก
        self.ink_frame = ttk.LabelFrame(self, text="ระดับหมึก")
        self.ink_frame.pack(fill="x", pady=4)
        ttk.Label(self.ink_frame, text="(เชื่อมต่อเพื่อดูข้อมูล)").pack(padx=6, pady=6)

        # กรอบตัวนับซับหมึก
        self.waste_frame = ttk.LabelFrame(self, text="ตัวนับซับหมึก (Waste ink pad)")
        self.waste_frame.pack(fill="x", pady=4)
        ttk.Label(self.waste_frame, text="(เชื่อมต่อเพื่อดูข้อมูล)").pack(padx=6, pady=6)

        # ปุ่มล่าง
        bottom = ttk.Frame(self)
        bottom.pack(fill="x", pady=(8, 0))
        self.btn_refresh = ttk.Button(
            bottom, text="รีเฟรช", command=self.refresh, state="disabled"
        )
        self.btn_refresh.pack(side="left")
        self.btn_reset = ttk.Button(
            bottom, text="รีเซ็ตตัวนับซับหมึก", command=self.reset_waste, state="disabled"
        )
        self.btn_reset.pack(side="right")

    # ---------- การเชื่อมต่อ ----------
    def scan_printers(self) -> None:
        try:
            self._paths = list(self.api["enumerate_printers"]())
        except Exception as exc:  # noqa: BLE001
            messagebox.showerror("ผิดพลาด", f"สแกนเครื่องพิมพ์ไม่สำเร็จ:\n{exc}")
            return

        labels = []
        for path in self._paths:
            labels.append(self._safe_label(path))

        self.combo["values"] = labels
        if labels:
            self.combo.current(0)
            self.status_var.set(f"พบเครื่องพิมพ์ USB {len(labels)} ตัว — เลือกแล้วกด 'เชื่อมต่อ'")
        else:
            self.status_var.set("ไม่พบเครื่องพิมพ์ USB (ตรวจสาย/ไดรเวอร์แล้วกด 'สแกนใหม่')")

    def _safe_label(self, path: str) -> str:
        """เปิดเครื่องพิมพ์ชั่วคราวเพื่ออ่านชื่อรุ่น (อ่านอย่างเดียว)."""
        try:
            transport = self.api["USBPRINTTransport"](path).__enter__()
            try:
                backend = self.api["D4ControlBackend"](transport).__enter__()
                try:
                    ident = self.api["parse_identifier"](backend.identify())
                    return ident.get("DES") or ident.get("MDL") or path
                finally:
                    backend.__exit__(None, None, None)
            finally:
                transport.__exit__(None, None, None)
        except Exception:  # noqa: BLE001
            return path

    def connect(self) -> None:
        idx = self.combo.current()
        if idx < 0 or idx >= len(self._paths):
            messagebox.showwarning("เลือกเครื่องพิมพ์", "กรุณาเลือกเครื่องพิมพ์ก่อน")
            return

        self._disconnect()  # ปิดการเชื่อมต่อเดิมถ้ามี
        path = self._paths[idx]
        self.master.config(cursor="watch")
        self.master.update()
        try:
            self._transport = self.api["USBPRINTTransport"](path).__enter__()
            self._backend = self.api["D4ControlBackend"](self._transport).__enter__()
            ident = self.api["parse_identifier"](self._backend.identify())
            device = self.api["by_model"](ident.get("MDL", ""))
            self._printer = self.api["Printer"](self._backend, device=device)
            self.status_var.set(
                f"เชื่อมต่อแล้ว: {ident.get('DES', '?')}  (MDL={ident.get('MDL', '?')})"
            )
            self.btn_refresh.config(state="normal")
            self.btn_reset.config(state="normal")
            self.refresh()
        except Exception as exc:  # noqa: BLE001
            self._disconnect()
            messagebox.showerror(
                "เชื่อมต่อไม่สำเร็จ",
                f"{exc}\n\n- ปิดคิวงานพิมพ์/โปรแกรมที่ใช้เครื่องพิมพ์อยู่\n"
                "- ตรวจว่าเป็นเครื่อง Epson ที่ต่อ USB จริง",
            )
        finally:
            self.master.config(cursor="")

    def _disconnect(self) -> None:
        self._printer = None
        try:
            if self._backend is not None:
                self._backend.__exit__(None, None, None)
        except Exception:  # noqa: BLE001
            pass
        try:
            if self._transport is not None:
                self._transport.__exit__(None, None, None)
        except Exception:  # noqa: BLE001
            pass
        self._backend = None
        self._transport = None
        self.btn_refresh.config(state="disabled")
        self.btn_reset.config(state="disabled")

    def _on_close(self) -> None:
        self._disconnect()
        self.master.destroy()

    # ---------- อ่านสถานะ ----------
    def refresh(self) -> None:
        if self._printer is None:
            return
        self.master.config(cursor="watch")
        self.master.update()
        try:
            status = self._printer.get_status()
            wastes = self._printer.get_waste()
        except Exception as exc:  # noqa: BLE001
            messagebox.showerror("อ่านสถานะไม่สำเร็จ", str(exc))
            return
        finally:
            self.master.config(cursor="")

        self._render_ink(status)
        self._render_waste(wastes)

    def _render_ink(self, status) -> None:
        for w in self.ink_frame.winfo_children():
            w.destroy()
        self._ink_bars.clear()

        cs = self.api["ConsumableStatus"]
        levels = list(getattr(status, "levels", []) or [])

        # แสดงสถานะ error ถ้าเป็น Service Required
        perr = self.api["PrinterError"]
        err = getattr(status, "error", None)
        if err == perr.SERVICEREQ:
            ttk.Label(
                self.ink_frame,
                text="⚠ สถานะเครื่อง: Service Required (มักคือซับหมึกเต็ม)",
                foreground="#C0392B",
            ).grid(row=0, column=0, columnspan=max(len(levels), 1), pady=(4, 2))

        if not levels:
            ttk.Label(self.ink_frame, text="(ไม่มีข้อมูลระดับหมึก)").grid(
                row=1, column=0, padx=6, pady=6
            )
            return

        for i, lv in enumerate(levels):
            color = getattr(getattr(lv, "color", None), "name", "?")
            cell = ttk.Frame(self.ink_frame)
            cell.grid(row=1, column=i, padx=8, pady=6)
            style_name = f"Ink{i}.Horizontal.TProgressbar"
            ttk.Style(self.master).configure(
                style_name, background=INK_COLORS.get(color, "#999999")
            )
            bar = ttk.Progressbar(cell, length=70, style=style_name, maximum=100)
            bar.pack()
            text = self._ink_text(lv, cs)
            bar["value"] = lv.level if getattr(lv, "level", -1) >= 0 else 0
            ttk.Label(cell, text=color, font=("", 8)).pack()
            ttk.Label(cell, text=text, font=("", 8)).pack()

    def _ink_text(self, lv, cs) -> str:
        status = getattr(lv, "status", None)
        level = getattr(lv, "level", -1)
        if status == cs.MISSING:
            return "ไม่พบถัง"
        if status == cs.UNKNOWN:
            return "ไม่ทราบ*"
        if level is None or level < 0:
            return "ไม่ทราบ"
        return f"{level}%"

    def _render_waste(self, wastes) -> None:
        for w in self.waste_frame.winfo_children():
            w.destroy()
        self._waste_bars.clear()

        if not wastes:
            ttk.Label(
                self.waste_frame,
                text="(รุ่นนี้ไม่มีข้อมูลตัวนับซับหมึกในฐานข้อมูล ez-reset)",
            ).pack(padx=6, pady=6)
            return

        worst = 0.0
        for i, (level, max_level) in enumerate(wastes):
            name = WASTE_LABELS[i] if i < len(WASTE_LABELS) else f"Counter {i}"
            row = ttk.Frame(self.waste_frame)
            row.pack(fill="x", padx=8, pady=4)
            percent = (level / max_level * 100.0) if max_level else 0.0
            worst = max(worst, percent)
            ttk.Label(row, text=name).pack(anchor="w")
            bar = ttk.Progressbar(row, maximum=100)
            bar["value"] = min(percent, 100.0)
            bar.pack(fill="x")
            detail = (
                f"{percent:6.2f}%   ({level} / {max_level})   —  {self._verdict(percent)}"
                if max_level
                else f"{level} (ไม่ทราบค่าสูงสุด)"
            )
            ttk.Label(row, text=detail, font=("", 8)).pack(anchor="w")

        summary = f"ซับหมึกที่เต็มที่สุด: {worst:.2f}%"
        if worst >= FULL_PERCENT:
            summary += "  → ต้องล้าง/เปลี่ยนแผ่นซับหมึกก่อนรีเซ็ต"
        ttk.Label(self.waste_frame, text=summary, foreground="#555").pack(
            anchor="w", padx=8, pady=(2, 6)
        )

    @staticmethod
    def _verdict(percent: float) -> str:
        if percent >= FULL_PERCENT:
            return "เต็มแล้ว"
        if percent >= WARN_PERCENT:
            return "ใกล้เต็ม"
        return "ปกติ"

    # ---------- รีเซ็ต ----------
    def reset_waste(self) -> None:
        if self._printer is None:
            return
        if not getattr(self._printer.device, "reset", None):
            messagebox.showwarning(
                "รีเซ็ตไม่ได้",
                "ไม่พบข้อมูลรีเซ็ตของรุ่นนี้ในฐานข้อมูล ez-reset",
            )
            return

        warn = (
            "กำลังจะรีเซ็ตตัวนับซับหมึก\n\n"
            "⚠ สำคัญ: การรีเซ็ตแค่ทำให้เครื่อง 'นับใหม่' เท่านั้น\n"
            "ไม่ได้ทำให้แผ่นซับหมึกจริงในเครื่องว่างขึ้น\n\n"
            "ถ้าแผ่นซับอิ่มหมึกแล้ว ควรล้าง/เปลี่ยนแผ่นซับก่อน\n"
            "ไม่งั้นหมึกอาจล้นออกมาเลอะข้างในหรือรั่วออกก้นเครื่อง\n\n"
            "ยืนยันจะรีเซ็ตต่อหรือไม่?"
        )
        if not messagebox.askyesno("ยืนยันการรีเซ็ต", warn, icon="warning", default="no"):
            return

        self.master.config(cursor="watch")
        self.master.update()
        try:
            self._printer.reset_waste()
        except Exception as exc:  # noqa: BLE001
            messagebox.showerror("รีเซ็ตไม่สำเร็จ", str(exc))
            return
        finally:
            self.master.config(cursor="")

        messagebox.showinfo(
            "รีเซ็ตสำเร็จ",
            "รีเซ็ตตัวนับซับหมึกเรียบร้อย\nกรุณาปิด-เปิดเครื่องพิมพ์ใหม่ 1 ครั้ง",
        )
        self.refresh()


def main() -> int:
    api, err = load_ez_reset()
    root = tk.Tk()
    root.title("Epson Waste Ink Tool")
    root.minsize(460, 360)

    if api is None:
        # แสดงคำแนะนำติดตั้งในหน้าต่างแทนที่จะ crash
        frame = ttk.Frame(root, padding=16)
        frame.pack(fill="both", expand=True)
        msg = (
            "ไม่พบไลบรารี ez-reset (หรือรันบนระบบที่ไม่ใช่ Windows)\n\n"
            f"รายละเอียด: {err}\n\n"
            "วิธีติดตั้ง (บน Windows):\n"
            "    git clone https://github.com/CiRIP/ez-reset\n"
            "    cd ez-reset\n"
            "    pip install .\n\n"
            "แล้วเปิดโปรแกรมนี้ใหม่อีกครั้ง"
        )
        ttk.Label(frame, text=msg, justify="left").pack(anchor="w")
        ttk.Button(frame, text="ปิด", command=root.destroy).pack(pady=8)
        root.mainloop()
        return 1

    app = WasteTool(root, api)
    app.pack(fill="both", expand=True)
    root.mainloop()
    return 0


if __name__ == "__main__":
    sys.exit(main())
