# Load test

สคริปต์ทดสอบภาระสำหรับ API ค้นหา (k6 และ Locust)

## เตรียม selfie
ต้องมีไฟล์รูปเซลฟีจริงหรือ fixture — เช่นคัดลอกจากข้อมูลจำลอง:
```bash
cp ../../server/_demo/_fixtures/demo/selfies/identity_3.jpg ./identity_3.jpg
```
> ห้าม commit รูปเซลฟีจริงของบุคคล — ไฟล์ `*.jpg` ในโฟลเดอร์นี้ควรอยู่ใน .gitignore ของงานโหลด

## k6
```bash
BASE=http://localhost:8000 EVENT=demo SELFIE=./identity_3.jpg \
  k6 run --env SCENARIO=ramp  k6_search.js     # 50 users ทยอย
  k6 run --env SCENARIO=burst k6_search.js     # 50 users พร้อมกัน
```

## Locust
```bash
EVENT=demo SELFIE=./identity_3.jpg \
  locust -f locustfile.py --host http://localhost:8000 \
    --tags browse --users 50 --spawn-rate 10 --run-time 90s --headless
# burst:
  locust -f locustfile.py --host http://localhost:8000 \
    --tags burst --users 50 --spawn-rate 50 --run-time 30s --headless
```

## สิ่งที่ต้องรายงาน
p50/p95/p99, error rate, req/s, เวลาในคิว, cache hit rate, จำนวนคำขอ Drive, peak memory
แยกผล **ทยอยใช้งาน** ออกจาก **burst พร้อมกัน** และแยก warm-cache ออกจาก cold start
(ดู ../../docs/performance-plan.md)
