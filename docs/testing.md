# การทดสอบ

## เทสต์ที่รันได้ในเครื่อง (ไม่ต้องมี credential)
```bash
cd server && python -m pytest -v
```
ครอบคลุม: pipeline accuracy (precision/recall บนข้อมูลจำลอง), API flow, media token,
admin remove, pagination, checksum, publish conflict, idempotent resume, index roundtrip

> เทสต์เหล่านี้ใช้ `EMBEDDER=mock` — พิสูจน์ **ความถูกต้องของเส้นทางข้อมูล** ไม่ใช่ความแม่นยำโมเดลจริง

## ข้อมูลจำลอง (SYNTHETIC — ระบุชัดว่าไม่ใช่ใบหน้าจริง)
`scripts/make_fixtures.py` สร้างรูปที่ "ใบหน้า" = สี่เหลี่ยมสีจาก palette (สี = identity)
พร้อม `ground_truth.json` เพื่อวัด precision/recall ของ pipeline

## Benchmark เวลาเทียบเวกเตอร์
```bash
cd server && PYTHONPATH=. python scripts/bench_vector_search.py --iters 500
```

## Load test
```bash
# k6
BASE=http://localhost:8000 EVENT=demo SELFIE=./selfie.jpg \
  k6 run --env SCENARIO=ramp  tests/load/k6_search.js
  k6 run --env SCENARIO=burst tests/load/k6_search.js
# Locust
EVENT=demo SELFIE=./selfie.jpg locust -f tests/load/locustfile.py \
  --host http://localhost:8000 --users 50 --spawn-rate 10 --run-time 90s --headless
```

## ทดสอบความแม่นยำ InsightFace จริง (ทำแล้วบางส่วน — ดู docs/status.md)
```bash
pip install insightface onnxruntime scikit-learn
# ชุดภาพจริงที่ได้รับอนุญาต เช่น DeepFace test set (MIT, งานวิจัย):
git clone --depth 1 https://github.com/serengil/deepface /tmp/deepface
cd server && PYTHONPATH=. EMBEDDER=insightface python scripts/accuracy_faces.py \
  --dataset /tmp/deepface/tests/unit/dataset --pairs /tmp/deepface/tests/unit/dataset/master.csv
```
วัด verification 1:1 (distribution + threshold) และ search 1:N (precision/recall) ด้วยโค้ดจริง

## ทดสอบ Google Drive จริง (เมื่อมี credential)
```bash
export STORAGE_BACKEND=drive DRIVE_ROOT_FOLDER_ID=xxx GOOGLE_APPLICATION_CREDENTIALS=/secure/sa.json
cd server && PYTHONPATH=. python scripts/drive_selftest.py
```

## ทดสอบความแม่นยำบนภาพงานจริง (ต้องทำก่อนใช้งานจริง)
1. `EMBEDDER=insightface`
2. เตรียมชุดภาพจริง **ที่ได้รับอนุญาตให้ใช้** มี ground truth (ใครอยู่ในรูปไหน)
3. **แยกรูปตั้ง threshold ออกจากรูปทดสอบ** (อย่าปรับ threshold บนชุดที่วัดผล)
4. รัน matching แล้วรายงาน:
   - false positive: รูปคนอื่นที่ปะปนเข้าผล
   - false negative: รูปของผู้ใช้ที่ตกหล่น
   - แยกตามสภาพ: รูปเบลอ / หันข้าง / ใบหน้าเล็ก / ภาพกลุ่ม
5. ปรับ `MATCH_THRESHOLD` ตามผลสอบเทียบ (อย่าแสดง similarity เป็น % ความมั่นใจโดยไม่สอบเทียบ)

## กรณีขอบที่ต้องครอบคลุม (ดู performance-plan.md ข้อ 1–10)
รวมถึง: คนไม่อยู่ในงาน, รูปเดียวหลายคน, คนเดียวหลายรูป (>100), เพิ่มรูประหว่างค้นหา,
worker หยุดกลางทาง/เรียกซ้ำ, Drive rate limit/timeout/ลบรูป/ถอนสิทธิ์, cold start instance

## ห้าม
- แต่งตัวเลข benchmark
- ใช้ unit test เวกเตอร์สังเคราะห์อ้างว่าโมเดลจับคู่ใบหน้าจริงแม่นยำ
- ทำปุ่มค้นหาแล้วสุ่มรูปมาแสดงเสมือนจับคู่ได้จริง
