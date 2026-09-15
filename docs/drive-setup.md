# การตั้งค่า Google Drive (Shared Drive)

> ผู้ใช้เลือก **Shared Drive** — เหมาะกับงานนี้เพราะ service account เป็นสมาชิกได้และเป็นเจ้าของไฟล์ที่สร้าง
> (ต่างจาก My Drive ที่ service account เขียนไฟล์ในไดรฟ์ของคนอื่นไม่ได้ตามปกติ)

## 1) สร้าง service account
1. GCP Console → IAM & Admin → Service Accounts → Create
2. สร้าง key (JSON) → เก็บอย่างปลอดภัย (mount ตอน deploy, ห้าม commit)
3. เปิดใช้ **Google Drive API** ในโปรเจกต์

### ให้ credential แก่ระบบ (เลือกทางใดทางหนึ่ง)
- **(ก) ไฟล์:** ตั้ง `GOOGLE_APPLICATION_CREDENTIALS=/secure/sa.json` (ต้องมีไฟล์อยู่ในเครื่อง/คอนเทนเนอร์)
- **(ข) เนื้อ JSON ผ่าน env:** ตั้ง `GOOGLE_CREDENTIALS_JSON` เป็นเนื้อ JSON ทั้งก้อน
  เหมาะกับ **remote/secret** (Cloud Run Secret หรือ environment settings ของ Claude Code)
  เพราะไม่ต้องวางไฟล์และไม่โผล่ใน transcript

> รันทดสอบเมื่อพร้อม: `cd server && PYTHONPATH=. python scripts/drive_selftest.py`
> (ต้อง `pip install google-api-python-client google-auth` ก่อน)

## 2) เตรียม Shared Drive
1. สร้าง Shared Drive (เช่นชื่อ `PhotoSearch`)
2. เพิ่ม **อีเมลของ service account** เป็นสมาชิกระดับ **Content manager** ขึ้นไป
3. สร้างโฟลเดอร์ราก `PhotoSearch/` ใน Shared Drive → คัดลอก **fileId** จาก URL
   → ตั้งเป็น `DRIVE_ROOT_FOLDER_ID`

## 3) โครงสร้างโฟลเดอร์ (ระบบสร้างให้อัตโนมัติ)
```
PhotoSearch/                      (ROOT_FOLDER_ID)
  events/
    <EVENT_ID>/
      event.json
      originals/        # อ้างอิงโฟลเดอร์ต้นฉบับเดิมได้โดยไม่ย้ายรูป (ดูหมายเหตุ)
      previews/
      processing/
        status.json
        checkpoints/checkpoint.json
        failures/failures.json
      generations/
        <GENERATION_ID>/
          manifest.json  photos.json  faces-map.json  index.faiss
      current.json       # ชี้ generation ที่ตรวจครบ + revision
      overrides.json     # รูปที่ถอน (แยกจากดัชนี)
      audit/YYYY-MM-DD.jsonl
```

### อ้างอิงโฟลเดอร์ต้นฉบับเดิมโดยไม่ย้ายรูป
`event.json.originalsFolderId` ตั้งเป็น fileId ของโฟลเดอร์รูปเดิมที่ทีมงานอัปโหลดอยู่แล้วได้
ระบบจะอ่านจากที่นั่นตรง ๆ (ไม่ต้องย้ายไฟล์) — ต้องแชร์โฟลเดอร์นั้นให้ service account ด้วย

## 4) หลักการอ้างอิงไฟล์
- ใช้ **Drive fileId** เป็น key จริงเสมอ — ชื่อไฟล์ใน Drive **ซ้ำกันได้** ห้ามใช้ชื่อเป็น unique key
- `contentVersion` = `md5Checksum` ของต้นฉบับ (ตรวจว่าเนื้อหาเปลี่ยนไหม)

## 5) การอัปเดตส่วนที่เปลี่ยน (Drive Changes API)
- worker ควรใช้ [Drive changes](https://developers.google.com/workspace/drive/api/guides/manage-changes)
  เก็บ page token ไว้ใน Drive และมีการตรวจเทียบรายการตามรอบเพื่อกู้สถานะ
- ตอนสร้างข้อมูลครั้งแรก จัดลำดับการอ่าน token/รายการไม่ให้ตกหล่นการเปลี่ยนแปลงระหว่างสแกน
- (เวอร์ชันนี้ทำ full scan + checkpoint idempotent แล้ว; incremental via Changes เป็นงานต่อยอดที่ระบุใน operations.md)

## 6) โควตา/ลิมิต
- ตรวจ quota ของ **โปรเจกต์ GCP ที่ใช้จริง** ไม่คัดลอกตัวเลขจากบทความเก่า
- ตั้ง timeout + retry แบบ exponential backoff + jitter เฉพาะ error ที่ลองใหม่ได้
  แยก error สิทธิ์ (403 permission) ออกจาก quota
  ([limits](https://developers.google.com/workspace/drive/api/guides/limits))

## 7) ความปลอดภัย
- จำกัดสิทธิ์ service account ให้เข้าถึง **เฉพาะ Shared Drive/โฟลเดอร์ที่กำหนด**
- ไฟล์ดัชนี/mapping เป็นข้อมูลจำกัดสิทธิ์ — ไม่แชร์สาธารณะ ไม่ส่งทั้งหมดลงเครื่องผู้ใช้
- ไม่เปิดโฟลเดอร์งานเป็น public เพื่อแก้ปัญหาลิงก์รูป (API ตรวจสิทธิ์ให้)
