# โมเดลใบหน้าและสิทธิ์การใช้งาน

## ต้องเป็น face recognition จริง
ระบบต้องใช้โมเดลที่ทำ **face recognition** (สร้างเวกเตอร์ระบุตัวบุคคล)
การตรวจ "พบใบหน้า" หรือหาตำแหน่งตา/จมูก/ปาก (landmark) เพียงอย่างเดียว **ไม่ใช่** ระบบจับคู่บุคคล

## ค่าเริ่มต้น: InsightFace `buffalo_l`
- ให้ทั้ง detection + recognition (ArcFace embedding 512 มิติ, normalize L2)
- ใช้ผ่าน ONNX Runtime (CPU/GPU)

### สิทธิ์การใช้งาน (สำคัญ)
- **โค้ด** InsightFace เป็น MIT — แต่ **pretrained models** (รวม buffalo_l) มีเงื่อนไข
  **ใช้เพื่อการวิจัย/ไม่พาณิชย์**
- ผู้ใช้ยืนยันว่างานนี้ **ใช้เชิงวิจัย/ไม่พาณิชย์** → ใช้ buffalo_l ได้
- **หากเปลี่ยนเป็นเชิงพาณิชย์ในอนาคต** ต้องเปลี่ยนไปใช้โมเดลที่มีสิทธิ์เหมาะสม (เช่นโมเดลใบอนุญาต Apache/MIT
  หรือรุ่นที่ซื้อสิทธิ์) — โค้ดออกแบบเป็น adapter (`embedder/`) ให้สลับโมเดลได้โดยแก้ที่เดียว
- อ้างอิง: [InsightFace](https://github.com/deepinsight/insightface) (ตรวจเงื่อนไขล่าสุดก่อนใช้จริง)

## การจัดเก็บไฟล์โมเดล
- ระบุแหล่งที่มา รุ่น และสิทธิ์ในบันทึกของกิจกรรม/ระบบ
- เก็บสำเนาที่ระบบต้องใช้ตามนโยบายจัดเก็บที่ตกลง (โมเดลไม่ commit เข้า repo — อยู่ใน `.gitignore`)
- `manifest.json` ของทุก generation บันทึก `modelId`, `modelVersion`, `preprocessingVersion`,
  `vectorDimension`, `metric`, `normalization` เพื่อกันการปนรุ่นโมเดล

## การเปลี่ยนโมเดล
- ถ้าเปลี่ยนโมเดล/preprocessing → `preprocessingVersion`/`modelVersion` เปลี่ยน → jobKey เปลี่ยน
  → ระบบจะประมวลผลใหม่และสร้าง generation ใหม่ (ไม่ปนกับรุ่นเก่า)
- เซลฟีและรูปกิจกรรมต้องใช้โมเดล+preprocessing **เดียวกัน** เสมอ (บังคับผ่าน interface เดียว)

## Mock embedder (ทดสอบเท่านั้น)
`EMBEDDER=mock` ไม่ใช่ face recognition — ใช้พิสูจน์ pipeline โดยไม่ต้องมีโมเดล/สิทธิ์
ห้ามนำผลจาก mock ไปอ้างความแม่นยำ (ดู `status.md`)
