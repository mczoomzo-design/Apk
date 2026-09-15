"""ถ่ายภาพหน้าจอหน้าหลักด้วย Playwright (ใช้ Chromium ที่ติดตั้งไว้แล้ว)"""
import sys
from pathlib import Path

from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1:8000"
OUT = Path(__file__).resolve().parents[2] / "docs" / "screenshots"
OUT.mkdir(parents=True, exist_ok=True)
SELFIE = Path(__file__).resolve().parents[1] / "_demo" / "_fixtures" / "demo" / "selfies" / "identity_3.jpg"

IPHONE = {"width": 390, "height": 844}


def main() -> int:
    exe = "/opt/pw-browsers/chromium-1194/chrome-linux/chrome"
    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path=exe, args=["--no-sandbox"])
        ctx = browser.new_context(viewport=IPHONE, device_scale_factor=2)
        page = ctx.new_page()

        page.goto(f"{BASE}/e/demo", wait_until="networkidle")
        page.wait_for_selector("text=ค้นหารูปของฉัน")
        page.screenshot(path=str(OUT / "01-event.png"))

        page.goto(f"{BASE}/e/demo/search", wait_until="networkidle")
        page.set_input_files("input[type=file]", str(SELFIE))
        page.wait_for_selector("text=ค้นหารูปของฉัน")
        page.get_by_role("button", name="ค้นหารูปของฉัน").click()
        page.wait_for_selector(".grid .thumb", timeout=15000)
        page.wait_for_timeout(1200)  # ให้รูปย่อโหลด
        page.screenshot(path=str(OUT / "02-results.png"), full_page=True)

        page.locator(".grid .thumb").first.click()
        page.wait_for_selector(".lightbox")
        page.wait_for_timeout(800)
        page.screenshot(path=str(OUT / "03-lightbox.png"))

        browser.close()
    print("screenshots saved to", OUT)
    return 0


if __name__ == "__main__":
    sys.exit(main())
