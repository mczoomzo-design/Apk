# คู่มือการใช้งาน (ผู้ดูแล)

## สร้างกิจกรรม
- ผ่าน Apps Script `upsertEvent` (แนะนำสำหรับผู้ดูแล) หรือ API:
```bash
curl -X POST $API/api/admin/events -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" -d '{
    "eventId":"wedding-2026","title":"งานแต่งคุณเอ","date":"2026-10-01",
    "originalsFolderId":"<fileId โฟลเดอร์รูปต้นฉบับ>","status":"draft","public":true
  }'
```
- สร้างลิงก์ + QR: Apps Script `eventUrl(eventId)` / `qrImageUrl(eventId)`

## นำเข้ารูป / เตรียมรูป
1. อัปโหลดรูปเข้าโฟลเดอร์ต้นฉบับใน Drive (หรือชี้ `originalsFolderId` ไปโฟลเดอร์เดิม)
2. สั่งเตรียมรูป:
   - Cloud Run Job: `python -m photosearch.worker.run once --event wedding-2026`
   - หรือ CLI ในเครื่อง (local): `python -m photosearch.cli prepare --event <id>`
3. ดูความคืบหน้า:
```bash
curl $API/api/admin/events/wedding-2026/prepare-status -H "Authorization: Bearer $ADMIN_TOKEN"
# { state, total, processed, ok, no_face, failed, generationId, ... }
```
4. worker ตรวจ checksum แล้วเผยแพร่ generation ใหม่ (current.json) อัตโนมัติ

### กรณีที่แยกและแสดงตามจริง
- **รูปเสีย / ไม่มีใบหน้า / ถูกลบ / ไม่มีสิทธิ์** ถูกแยกนับ — รูปที่พร้อมยังเผยแพร่ได้
- ดูรายการล้มเหลว: `events/<id>/processing/failures/failures.json`
- **ลองใหม่เฉพาะที่ผิดพลาด:** รัน prepare ซ้ำได้ทันที — checkpoint (jobKey) ข้ามงานที่สำเร็จแล้ว
  จึงประมวลผลเฉพาะรูปใหม่/ที่เนื้อหาเปลี่ยน/ที่เคยล้มเหลว

## เพิ่มรูปภายหลัง
- อัปโหลดรูปใหม่เข้าโฟลเดอร์เดิม → สั่ง prepare อีกครั้ง
- ระหว่างรอ ผู้ใช้ยังค้นจากรุ่นเดิมได้ เมื่อเสร็จจะสลับไปรุ่นใหม่
- หน้าเว็บมีเวลาที่อัปเดตล่าสุด + ปุ่ม "ค้นหาอีกครั้ง"

## เปิด / ปิดกิจกรรม
```bash
# เปิด
curl -X POST "$API/api/admin/events/<id>/status?status=open"  -H "Authorization: Bearer $ADMIN_TOKEN"
# ปิด (ผู้ใช้ค้นหา/เข้าถึงรูปไม่ได้)
curl -X POST "$API/api/admin/events/<id>/status?status=closed" -H "Authorization: Bearer $ADMIN_TOKEN"
```
การปิดมีผลตาม `PERMISSION_TTL_S` (ค่าเริ่มต้น 60 วินาที)

## ถอน / คืนรูป (ไม่ต้อง reindex)
```bash
curl -X POST $API/api/admin/events/<id>/photos/remove  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" -d '["p_ab12...","p_cd34..."]'
curl -X POST $API/api/admin/events/<id>/photos/restore -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" -d '["p_ab12..."]'
```
รูปที่ถอนหายจากผลค้นหาและถูกปฏิเสธเมื่อขอรูป (แม้ยังอยู่ในดัชนีเก่า)
การย้อนรุ่นดัชนีจะไม่ทำให้รูปที่ถอนกลับมาเข้าถึงได้

## ดูรุ่นดัชนีที่ใช้งาน
- `events/<id>/current.json` → generationId + revision + publishedAt
- readiness ราย instance: `GET /api/ready?event=<id>`

## แก้ปัญหา (troubleshooting)
| อาการ | สาเหตุที่พบบ่อย | วิธีแก้ |
|---|---|---|
| หน้าเว็บขึ้น "กำลังเตรียมรูป" ตลอด | ยังไม่มี current.json / generation ว่าง | ตรวจ prepare-status, ดู failures.json |
| ค้นแล้ว not_ready แต่ prepare done แล้ว | instance ยังไม่รีเฟรช pointer (TTL) | รอ ≤ PERMISSION_TTL_S หรือรีสตาร์ท instance |
| photoCount = 0 หลัง prepare | `originalsFolderId` ผิด/ไม่มีสิทธิ์ | ตรวจ fileId + แชร์โฟลเดอร์ให้ service account |
| รูปย่อโหลดช้าครั้งแรก | แคช RAM ว่าง (ดึงจาก Drive) | ปกติ; ครั้งถัดไปมาจากแคช |
| publish conflict | มีผู้เผยแพร่พร้อมกัน / revision ไม่ตรง | สร้าง generation จากข้อมูลล่าสุดแล้ว publish ใหม่ |
| 403 เมื่อขอรูป | token หมดอายุ / รูปถูกถอน / งานปิด | ค้นหาใหม่เพื่อรับลิงก์ใหม่ |

## งานต่อยอด (บันทึกไว้ ไม่ผูกกับรุ่นแรก)
- Drive **Changes API** สำหรับ incremental แทน full scan
- lease/heartbeat ของ worker หลายตัว (เวอร์ชันนี้แนะนำ worker เดียว/กิจกรรม + ผู้เผยแพร่เดียว)
- ส่งผ่าน LINE, ขายรูป, กล้องสด — เป็นเฟสถัดไป
