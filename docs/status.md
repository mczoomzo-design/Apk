# สถานะ: อะไรพิสูจน์แล้ว / อะไรยังไม่

เอกสารนี้ระบุขอบเขตความจริงของงานตามหลัก "ห้ามอ้างเกินกว่าที่วัดจริง"

## ✅ พิสูจน์แล้ว (รันจริงในสภาพแวดล้อมนี้)

| สิ่งที่พิสูจน์ | วิธี | ผล |
|---|---|---|
| เส้นทางข้อมูลครบวงจร | `photosearch.cli` + `tests/` | Drive(local)→index→selfie→match→preview→download ทำงาน |
| ความถูกต้อง pipeline บนข้อมูลจำลอง | `tests/test_pipeline_accuracy.py` | precision = recall = 1.0 |
| dedupe รูปเดียวหลายใบหน้า / pagination ผูก generation | `tests/test_api.py` | ผ่าน |
| สถานะแยกกรณี (no_face/multiple_faces/not_ready/busy/expired) | เทสต์ + UI | ผ่าน |
| ถอนรูปมีผลโดยไม่ reindex + ปฏิเสธ media ของรูปที่ถอน | `test_admin_remove_photo_hides_from_results` | ผ่าน |
| ตรวจจับ checksum ดัชนีเสีย + publish conflict | `test_generation.py` | ผ่าน |
| idempotent resume (รัน prepare ซ้ำได้ผลเท่าเดิม) | `test_resume_is_idempotent` | ผ่าน |
| เวลาเทียบเวกเตอร์ใน RAM | `scripts/bench_vector_search.py` | ดู `performance-plan.md` |
| โหลด API เบื้องต้น (mock embedder, 1 instance) | Locust | 50 users ทยอย: 0 fail, search p95 ~400ms |
| หน้าเว็บภาษาไทยมือถือ | Playwright | `docs/screenshots/` |

## ⛔ ยังไม่พิสูจน์ (ต้องมี credential/ข้อมูลจริง)

| สิ่งที่ยังไม่ทำ | ต้องใช้ | หมายเหตุ |
|---|---|---|
| face recognition จริง (InsightFace) บนภาพจริงชุดเล็ก | ✅ **พิสูจน์แล้ว** | ดูด้านล่าง — verification acc 1.0, search P/R 1.0 บนภาพหน้าตรง 25 รูป/9 identity |
| ความแม่นยำบน **ภาพงานจริง** (เบลอ/หันข้าง/ภาพกลุ่ม) | ⛔ ยังไม่วัด | ต้องมีภาพงานจริงมี ground truth |
| **Google Drive backend จริง** | Shared Drive + service account | `storage/drive.py` เขียนตาม API แล้วแต่ยังไม่รันจริง |
| **การเผยแพร่ผ่าน Apps Script จริง** | Apps Script Web App + PUBLISH_TOKEN | โค้ดพร้อม แต่ยังไม่ deploy |
| **โหลดจริงกับโมเดลจริง** | Cloud Run + InsightFace | เวลาจะถูกครอบด้วยเวลารันโมเดล (mock ไม่สะท้อน) |
| **การเพิ่ม instance / min-instances** | Cloud Run | ต้องทดสอบ scale จริง |
| **ค่าใช้จ่ายจริง** | ราคาปัจจุบัน + การใช้งานจริง | ดู `cost.md` (โครง ยังไม่ผูกตัวเลขรับประกัน) |

## ผลทดสอบ InsightFace จริง (buffalo_l) — ภาพจริงชุด DeepFace (MIT, บุคคลสาธารณะ, งานวิจัย)
รันผ่านโค้ดจริงของระบบ (`scripts/accuracy_faces.py` → InsightFaceEmbedder + LoadedGeneration.search)

- ภาพ 25 รูป, คู่ที่ label แล้ว 300 คู่ (คนเดียวกัน 38 / คนละคน 262), ตรวจไม่พบใบหน้า **0**
- **Verification 1:1** cosine: คนเดียวกัน mean **0.759** (min 0.550) · คนละคน mean **0.010** (max 0.232)
  → ช่องว่างกว้างชัด; accuracy **1.000** (FAR 0, FRR 0) ที่ threshold 0.25–0.50
- **Search 1:N** (9 identity, gallery 25): precision **1.000** recall **1.000**
- เวลา embed บน CPU: **~436 ms/รูป** (ส่วนที่ mock ไม่สะท้อน — สำคัญต่อ throughput worker และเวลาค้นหา)
- **ยืนยันค่าเริ่มต้น `MATCH_THRESHOLD=0.35`** (อยู่ระหว่าง max ของคนละคน 0.23 กับ min ของคนเดียวกัน 0.55)

**ข้อจำกัด:** ชุดนี้เล็กและเป็นภาพหน้าตรงคุณภาพดี — เป็น "ขอบบน" ต้องทดสอบซ้ำกับภาพงานจริง
(เบลอ/หันข้าง/ใบหน้าเล็ก/ภาพกลุ่ม) ก่อนสรุปค่าใช้งานจริงและปรับ threshold

## เหตุที่ mock ไม่ใช่หลักฐานความแม่นยำ
`EMBEDDER=mock` แปลง "สี่เหลี่ยมสี" เป็นเวกเตอร์แบบ deterministic เพื่อทดสอบว่า **ระบบส่งข้อมูลถูกท่อ**
(ตรวจหลายใบหน้า/รูป → index → search → dedupe → เสิร์ฟรูป) — ไม่เกี่ยวกับความสามารถแยกแยะใบหน้าจริง
การพิสูจน์ความแม่นยำต้องสลับเป็น `EMBEDDER=insightface` แล้วรันชุดทดสอบใน `testing.md`

## ก่อน deploy จริง (รอคำสั่ง)
ยังไม่เปิดบริการที่มีค่าใช้จ่าย ไม่เผยแพร่รูป ไม่ deploy เข้าบัญชีจริง — ดู `deploy.md`
