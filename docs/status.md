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
| **ความแม่นยำ face recognition จริง** | InsightFace + ภาพจริงมี ground truth ที่ได้รับอนุญาต | mock วัดได้แค่ pipeline ไม่ใช่ความแม่นยำโมเดล |
| **Google Drive backend จริง** | Shared Drive + service account | `storage/drive.py` เขียนตาม API แล้วแต่ยังไม่รันจริง |
| **การเผยแพร่ผ่าน Apps Script จริง** | Apps Script Web App + PUBLISH_TOKEN | โค้ดพร้อม แต่ยังไม่ deploy |
| **โหลดจริงกับโมเดลจริง** | Cloud Run + InsightFace | เวลาจะถูกครอบด้วยเวลารันโมเดล (mock ไม่สะท้อน) |
| **การเพิ่ม instance / min-instances** | Cloud Run | ต้องทดสอบ scale จริง |
| **ค่าใช้จ่ายจริง** | ราคาปัจจุบัน + การใช้งานจริง | ดู `cost.md` (โครง ยังไม่ผูกตัวเลขรับประกัน) |

## เหตุที่ mock ไม่ใช่หลักฐานความแม่นยำ
`EMBEDDER=mock` แปลง "สี่เหลี่ยมสี" เป็นเวกเตอร์แบบ deterministic เพื่อทดสอบว่า **ระบบส่งข้อมูลถูกท่อ**
(ตรวจหลายใบหน้า/รูป → index → search → dedupe → เสิร์ฟรูป) — ไม่เกี่ยวกับความสามารถแยกแยะใบหน้าจริง
การพิสูจน์ความแม่นยำต้องสลับเป็น `EMBEDDER=insightface` แล้วรันชุดทดสอบใน `testing.md`

## ก่อน deploy จริง (รอคำสั่ง)
ยังไม่เปิดบริการที่มีค่าใช้จ่าย ไม่เผยแพร่รูป ไม่ deploy เข้าบัญชีจริง — ดู `deploy.md`
