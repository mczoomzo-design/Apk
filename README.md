# Photo Booth — Sticker Photo Booth (APK)

แอปตู้ถ่ายรูปที่พิมพ์ออกมาเป็น **สลิปใบเสร็จ** (มีรูปถ่ายฝังในสลิป + หัวร้าน + เลขที่ + บาร์โค้ด + **QR สแกนดาวน์โหลดรูปได้จริง**) ผ่านเครื่องปริ้นบลูทูธ **80mm (576px @203DPI, ESC/POS)**
สร้างด้วย Capacitor (หน้า UI เป็นเว็บ + สะพานปริ้น native)

## ฟีเจอร์
- เลือกรูปแบบสลิปได้: **1 / 2 / 3 / 4 รูป** (มีเลย์เอาต์แนวตั้ง/เคียงข้างให้เลือก)
- **กดถ่ายครั้งเดียว นับถอยหลังยิงรวดทุกรูป** อัตโนมัติ (ตั้งเวลา 3/5/10 วิได้)
- พิมพ์เป็นสลิปใบเสร็จ 80mm (ทำ dithering ให้ภาพคมบนกระดาษความร้อน)
- **QR โค้ดจริง** บนสลิป → สแกนแล้วเปิดหน้าเว็บดูรูป + ปุ่มดาวน์โหลด
- ตั้งชื่อร้าน (หัวสลิป) และเลือกเครื่องปริ้นได้ในหน้า ⚙︎

---

## ส่วนที่ 1 — ทำ APK

### ทางที่ 1: Build บนคลาวด์ (แนะนำ ไม่ต้องลงโปรแกรม)
1. สร้าง repo ใหม่บน GitHub (เช่น `photobooth-app`) → อัปโหลดไฟล์ทั้งหมด **ยกเว้นโฟลเดอร์ `pages/`** (pages ใช้อีก repo)
2. แท็บ **Actions** → workflow **Build APK** → **Run workflow** (หรือ push โค้ดก็รันเอง)
3. ดาวน์โหลด artifact **photo-booth-apk** → ได้ `app-debug.apk` → ติดตั้งลงแท็บเล็ต

### ทางที่ 2: Android Studio
```bash
npm install
npx cap sync android
npx cap open android      # กด Run / Build APK
```

---

## ส่วนที่ 2 — ทำ QR ให้ดาวน์โหลดรูปได้จริง (GitHub Pages)

ใช้ **repo แยกอีกอันสำหรับเก็บรูป** (public) เพื่อไม่ปนกับโค้ดแอป

1. สร้าง repo ใหม่ **public** เช่น `booth-photos`
2. อัปโหลดไฟล์ในโฟลเดอร์ `pages/` ขึ้น repo นี้ (จะได้ `index.html`, `view.html`, `photos/`)
3. เปิด **Settings → Pages** ของ repo นี้ → Source = **Deploy from a branch** → `main` / root → Save
   จะได้ URL เช่น `https://<user>.github.io/booth-photos`
4. สร้าง **Fine-grained Personal Access Token**: GitHub → Settings → Developer settings → Fine-grained tokens
   - Repository access = เฉพาะ `booth-photos`
   - Permissions → **Contents: Read and write**
5. เปิดแอป → ปุ่ม **⚙︎** หน้าแรก → กรอก:
   - **GitHub Owner** = ชื่อผู้ใช้ของคุณ
   - **Repository** = `booth-photos`
   - **Pages URL** = `https://<user>.github.io/booth-photos`
   - **Access Token** = token ที่สร้าง

เมื่อสั่งพิมพ์ แอปจะอัปโหลดสลิปขึ้น `booth-photos/photos/<เลขที่>.png` และ QR บนสลิปจะชี้ไป
`https://<user>.github.io/booth-photos/view.html?id=<เลขที่>` → ลูกค้าสแกนแล้วกด **บันทึกรูป** ได้ทันที
(หน้า viewer ดึงรูปจาก `raw.githubusercontent.com` ซึ่งอัปเดตทันที และมี retry ให้ระหว่างรอ)

> **ความปลอดภัย:** token ฝังในเครื่องบูธ ควรใช้ fine-grained scope แค่ repo เดียว + contents:write เท่านั้น
> ถ้าต้องการปลอดภัยขึ้น ให้เปลี่ยนเป็น endpoint อัปโหลดของตัวเองภายหลัง (จุดนี้เหมาะให้ Claude Code ต่อยอด)

---

## การเชื่อมเครื่องปริ้น
1. เปิดบลูทูธ + **จับคู่ (pair)** เครื่องปริ้น 80mm ในตั้งค่าบลูทูธของแท็บเล็ตก่อน
2. เปิดแอป → ปุ่ม **⚙︎** → เลือกเครื่องปริ้นจากรายการที่จับคู่ไว้
> เครื่อง 80mm ส่วนใหญ่เป็น **Bluetooth Classic (SPP)** ซึ่งรองรับแล้ว ถ้าเป็น BLE อย่างเดียวต้องปรับสะพานปริ้น

---

## โครงสร้างสำคัญ
| ไฟล์ | หน้าที่ |
|---|---|
| `www/index.html` | หน้า UI ทั้งหมด + เรนเดอร์สลิป + QR จริง (ฝัง qrcode-generator) |
| `pages/view.html` | หน้า viewer บน GitHub Pages (ดู/ดาวน์โหลดรูปจาก QR) |
| `android/.../BluetoothPrinterPlugin.java` | ปลั๊กอิน `listDevices()` + `print()` ผ่าน SPP |
| `android/.../EscPos.java` | แปลงภาพ → raster 576px (Floyd–Steinberg) + ตัดกระดาษ |
| `.github/workflows/build-apk.yml` | build APK อัตโนมัติ |

## จูนงานพิมพ์
- ความเข้ม: `EscPos.java` บรรทัด `oldv < 128`
- ขนาดแบนด์: ตัวแปร `band = 128`
- การตัดกระดาษ: `CUT = {0x1D,0x56,0x01}` (partial) → `0x00` = ตัดเต็ม
