# การติดตั้ง

## สิ่งที่รันได้ในเครื่องทันที (ไม่ต้องมีบัญชีจริง)
- backend + worker ด้วย `STORAGE_BACKEND=local` และ `EMBEDDER=mock`
- หน้าเว็บ (Vite dev/preview หรือให้ FastAPI เสิร์ฟ)
- เทสต์, benchmark, load test (ใช้ selfie จำลอง)

## สิ่งที่ต้องมีบัญชีจริง (ทดสอบภายหลัง)
- `STORAGE_BACKEND=drive` → Shared Drive + service account key
- `EMBEDDER=insightface` → ติดตั้ง insightface/onnxruntime + ดาวน์โหลดโมเดล
- deploy Cloud Run → โปรเจกต์ GCP

## ค่าที่ต้องใส่จริง
| ตัวแปร | ใช้เมื่อ | ค่า |
|---|---|---|
| `DRIVE_ROOT_FOLDER_ID` | drive | fileId ของโฟลเดอร์ราก `PhotoSearch/` |
| `GOOGLE_APPLICATION_CREDENTIALS` | drive | path ของ service account key (ห้าม commit) |
| `INSIGHTFACE_MODEL`/`INSIGHTFACE_ROOT` | insightface | ชื่อโมเดล + โฟลเดอร์เก็บโมเดล |
| `ADMIN_TOKEN` | เสมอ | โทเคน bearer ของผู้ดูแล |
| `MEDIA_TOKEN_SECRET` | เสมอ | ค่าสุ่มยาว สำหรับเซ็นลิงก์รูป |

คัดลอก `server/.env.example` → `.env` แล้วแก้ค่า

## Backend (local)
```bash
cd server
python -m venv .venv && source .venv/bin/activate   # แนะนำ
pip install -r requirements.txt
export $(grep -v '^#' .env | xargs)   # หรือ set ตัวแปรเอง
python -m pytest        # 14 เทสต์ควรผ่าน
```

## Backend (โมเดลจริง)
```bash
pip install insightface onnxruntime
export EMBEDDER=insightface INSIGHTFACE_ROOT=~/.insightface
# โมเดล buffalo_l จะถูกดึงครั้งแรกที่ใช้ (ตรวจสิทธิ์ก่อน — ดู models.md)
```

## Backend (Drive จริง)
```bash
pip install google-api-python-client google-auth
export STORAGE_BACKEND=drive DRIVE_ROOT_FOLDER_ID=xxxx \
       GOOGLE_APPLICATION_CREDENTIALS=/secure/sa.json
# ตั้งค่า Drive ตาม docs/drive-setup.md ก่อน
```

## Frontend
```bash
cd web
npm install
npm run dev       # dev server (proxy /api ไป http://localhost:8000)
npm run build     # ได้ web/dist สำหรับให้ FastAPI เสิร์ฟ (ตั้ง WEB_DIST)
npm run typecheck
```

## Docker (local + mock)
```bash
docker compose up --build            # API ที่ http://localhost:8080
docker compose run --rm worker once --event demo
```

## หมายเหตุความปลอดภัย
- ห้าม commit: service account key, `.env`, โมเดล (มี `.gitignore` แล้ว)
- credential ของ Google อยู่ฝั่ง backend เท่านั้น ไม่ส่งไปหน้าเว็บ
