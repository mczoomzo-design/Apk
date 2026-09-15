# Deploy (ขั้นตอนที่ตรวจสอบได้ — ยังไม่รันจริงจนกว่าจะได้รับคำสั่ง)

> ยังไม่เปิดบริการที่มีค่าใช้จ่าย ไม่เผยแพร่รูป และไม่ deploy เข้าบัญชีจริงโดยไม่มีคำสั่ง
> เอกสารนี้เป็น "แผน deploy" ที่ตรวจสอบก่อนได้ ทำจริงเมื่อผู้ใช้สั่ง

## เตรียมก่อน
- [ ] Shared Drive + service account (docs/drive-setup.md)
- [ ] ทดสอบ `STORAGE_BACKEND=drive` ในเครื่องกับกิจกรรมทดลองรูปจริงชุดเล็ก
- [ ] `EMBEDDER=insightface` + ทดสอบความแม่นยำ (docs/testing.md)
- [ ] ตั้ง `ADMIN_TOKEN`, `MEDIA_TOKEN_SECRET` เป็นค่าสุ่มยาว (เก็บใน Secret Manager)

## Build image
```bash
# API (มี web/dist ในตัว)
cd web && npm ci && npm run build && cd ..
gcloud builds submit --tag REGION-docker.pkg.dev/PROJECT/photosearch/api \
  --config /dev/stdin <<'EOF'
steps:
  - name: gcr.io/cloud-builders/docker
    args: ['build','-f','server/Dockerfile.api','-t','$_IMAGE','.']
EOF
# Worker
gcloud builds submit -f server/Dockerfile.worker --tag REGION-docker.pkg.dev/PROJECT/photosearch/worker .
```

## Deploy API (Cloud Run Service)
```bash
gcloud run deploy photosearch-api \
  --image REGION-docker.pkg.dev/PROJECT/photosearch/api \
  --region REGION --no-allow-unauthenticated `# ตั้งสิทธิ์ตามนโยบาย` \
  --memory 2Gi --cpu 2 --concurrency 8 \
  --min-instances 0 --max-instances 5 \
  --set-secrets ADMIN_TOKEN=admin-token:latest,MEDIA_TOKEN_SECRET=media-secret:latest \
  --set-env-vars STORAGE_BACKEND=drive,EMBEDDER=insightface,DRIVE_ROOT_FOLDER_ID=xxx \
  --service-account sa-photosearch@PROJECT.iam.gserviceaccount.com
```
- ค่าเหล่านี้เป็นจุดเริ่ม **ต้องปรับจากผลทดสอบ** (memory/concurrency/instances)
- readiness: ตั้ง startup probe ให้ instance ที่ยังไม่มีดัชนีไม่รับ traffic เหมือนค้นหาไม่พบ

## Worker (Cloud Run Job)
```bash
gcloud run jobs create photosearch-prep \
  --image REGION-docker.pkg.dev/PROJECT/photosearch/worker \
  --region REGION --memory 4Gi --cpu 2 \
  --set-env-vars STORAGE_BACKEND=drive,EMBEDDER=insightface,DRIVE_ROOT_FOLDER_ID=xxx \
  --service-account sa-photosearch@PROJECT.iam.gserviceaccount.com
# รันเตรียมรูปหนึ่งกิจกรรม
gcloud run jobs execute photosearch-prep --args once,--event,wedding-2026
```

## Apps Script
- Deploy `apps-script/Code.gs` เป็น Web App, ตั้ง `ROOT_FOLDER_ID`, `WEB_BASE_URL`, `PUBLISH_TOKEN`
- worker/API เรียก `doPost` เพื่อ publish current.json (จุด commit เดียว)

## หลัง deploy
- ทดสอบ smoke: `/api/health`, `/api/ready?event=...`, ค้นหา 1 ครั้ง
- รัน load test ตาม performance-plan แล้วปรับ instances/concurrency
- ตรวจว่าไม่มีการเขียนข้อมูลกิจกรรมถาวรนอก Drive (log/temp/แคช) โดยไม่ตั้งใจ
- ตรวจว่าไม่เขียนเซลฟี/เวกเตอร์/token/เนื้อรูปลง log

## Checklist ความปลอดภัยก่อนเปิดจริง
- [ ] โฟลเดอร์งานไม่ public; media ผ่าน token แอปเท่านั้น
- [ ] service account จำกัดเฉพาะ Shared Drive ที่กำหนด
- [ ] secret อยู่ใน Secret Manager ไม่อยู่ใน image/repo
- [ ] permission TTL + ผลการถอนรูปทำงานจริง
