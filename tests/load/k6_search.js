// k6 load test — API ค้นหา (ตั้งแต่รับไฟล์ครบจนได้รายการผลลัพธ์)
//
// แยกสองสถานการณ์ตามข้อกำหนด:
//   ramp  = ผู้ใช้ 50 คนทยอยใช้งาน (arrival ค่อย ๆ เพิ่ม)
//   burst = ผู้ใช้ 50 คนส่งคำค้นพร้อมกัน (ทดสอบการควบคุมคิว/สถานะ busy)
//
// วิธีรัน (ต้องมี selfie จริงหรือ fixture):
//   BASE=http://localhost:8000 EVENT=demo SELFIE=./identity_3.jpg \
//     k6 run --env SCENARIO=ramp tests/load/k6_search.js
//   ... --env SCENARIO=burst ...
//
// รายงาน: k6 แสดง p50/p90/p95/p99, error rate, req/s อยู่แล้ว
// หมายเหตุ: ยังไม่ใช่ผลรับรอง จนกว่าจะรันกับ backend จริง + ข้อมูลจริง

import http from "k6/http";
import { check } from "k6";
import { Counter, Trend } from "k6/metrics";

const BASE = __ENV.BASE || "http://localhost:8000";
const EVENT = __ENV.EVENT || "demo";
const SELFIE = __ENV.SELFIE || "./identity_3.jpg";
const SCENARIO = __ENV.SCENARIO || "ramp";

const selfieBin = open(SELFIE, "b");

const searchLatency = new Trend("search_to_results_ms", true);
const okResults = new Counter("results_ok");
const busy = new Counter("results_busy");
const noFace = new Counter("results_no_face");

const scenarios = {
  ramp: {
    executor: "ramping-vus",
    startVUs: 0,
    stages: [
      { duration: "20s", target: 50 },
      { duration: "60s", target: 50 },
      { duration: "10s", target: 0 },
    ],
    gracefulStop: "10s",
  },
  burst: {
    executor: "per-vu-iterations",
    vus: 50,
    iterations: 1,
    maxDuration: "30s",
  },
};

export const options = {
  scenarios: { [SCENARIO]: scenarios[SCENARIO] },
  thresholds: {
    // เป้าหมายออกแบบ (ต้องพิสูจน์): p95 <= 3000 ms สำหรับ API ค้นหา
    search_to_results_ms: ["p(95)<3000"],
    http_req_failed: ["rate<0.05"],
  },
};

export default function () {
  const payload = {
    selfie: http.file(selfieBin, "selfie.jpg", "image/jpeg"),
  };
  const t0 = Date.now();
  const res = http.post(`${BASE}/api/events/${EVENT}/search`, payload);
  const dt = Date.now() - t0;

  check(res, { "status 200": (r) => r.status === 200 });
  if (res.status === 200) {
    let body = {};
    try {
      body = res.json();
    } catch (e) {
      body = {};
    }
    searchLatency.add(dt);
    if (body.status === "ok") okResults.add(1);
    else if (body.status === "busy") busy.add(1);
    else if (body.status === "no_face") noFace.add(1);
  }
}
