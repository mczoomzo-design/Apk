# Apps Script — งานประสานงานและจัดการ

Apps Script ทำเฉพาะงานที่เหมาะสม **ไม่รับ traffic ค้นหา** และ **ไม่เป็นที่พักดัชนี**
(Apps Script มีเวลารันต่อ execution จำกัด และ CacheService จำกัด 100KB/key อาจถูกนำออกก่อนหมดอายุ)

## บทบาท
- **จุดเผยแพร่ `current.json` เพียงจุดเดียว** ด้วย `LockService` + `expectedRevision`
  (ถือ lock สั้น ๆ เท่านั้น ห้ามถือระหว่างดาวน์โหลด/รัน AI)
- สร้าง/แก้ไขกิจกรรม (เขียน `event.json`) และสร้างลิงก์ + QR
- บันทึก `audit/` แบบไฟล์รายวัน

## ติดตั้ง
1. สร้างโปรเจกต์ Apps Script ผูกกับบัญชีที่เข้าถึง Shared Drive ได้
2. วาง `Code.gs`
3. ตั้ง Script Properties:
   - `ROOT_FOLDER_ID` = fileId ของโฟลเดอร์ราก `PhotoSearch/`
   - `WEB_BASE_URL` = โดเมนหน้าเว็บ (เช่น URL ของ Cloud Run)
   - `PUBLISH_TOKEN` = โทเคนลับสำหรับให้ worker/API เรียกเผยแพร่
4. Deploy เป็น **Web App** (จำกัดผู้เข้าถึงตามนโยบายองค์กร) เพื่อรับ `doPost`

## เหตุผลที่ publish อยู่ที่นี่
การเขียนหลายไฟล์ใน Drive **ไม่ใช่ transaction** การมีจุด commit เดียว (`current.json`)
ผ่านโปรเจกต์ Apps Script เดียวที่ถือ ScriptLock ทำให้การสลับรุ่นข้อมูลปลอดภัยจากการเขียนชนกัน
worker/API สร้าง generation + ตรวจ checksum ให้ครบก่อน แล้วจึงเรียก `publish`
