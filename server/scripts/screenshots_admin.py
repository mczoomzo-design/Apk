"""ถ่ายภาพหน้าจอ Admin console (ใส่โทเคนผ่าน localStorage ก่อนโหลด)"""
import sys
from pathlib import Path

from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1:8000"
OUT = Path(__file__).resolve().parents[2] / "docs" / "screenshots"
OUT.mkdir(parents=True, exist_ok=True)
EXE = "/opt/pw-browsers/chromium-1194/chrome-linux/chrome"
IPHONE = {"width": 390, "height": 844}


def main() -> int:
    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path=EXE, args=["--no-sandbox"])
        ctx = browser.new_context(viewport=IPHONE, device_scale_factor=2)
        page = ctx.new_page()
        # ตั้งโทเคนก่อนเข้าแอป
        page.goto(f"{BASE}/admin")
        page.evaluate("localStorage.setItem('photosearch_admin_token','demo')")
        page.goto(f"{BASE}/admin", wait_until="networkidle")
        page.wait_for_selector("text=กิจกรรมทั้งหมด")
        page.wait_for_timeout(500)
        page.screenshot(path=str(OUT / "04-admin-home.png"), full_page=True)

        page.goto(f"{BASE}/admin/e/gala", wait_until="networkidle")
        page.wait_for_selector("text=เตรียมรูป")
        page.wait_for_timeout(1200)  # ให้ QR โหลด
        page.screenshot(path=str(OUT / "05-admin-event.png"), full_page=True)
        browser.close()
    print("saved admin screenshots")
    return 0


if __name__ == "__main__":
    sys.exit(main())
