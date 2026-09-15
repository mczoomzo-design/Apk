import { useState } from "react";
import { getToken, setToken } from "../lib/admin";

// แถบใส่โทเคนผู้ดูแล (เก็บในเครื่องนี้เท่านั้น)
export default function AdminAuth({ onChange }: { onChange: () => void }) {
  const [val, setVal] = useState(getToken());
  const has = !!getToken();
  return (
    <div className="card">
      <h2>โทเคนผู้ดูแล</h2>
      <p className="muted small">ใช้ค่า ADMIN_TOKEN ของระบบ เก็บไว้ในเบราว์เซอร์นี้เท่านั้น ไม่ส่งไปที่อื่น</p>
      <div className="row">
        <input
          type="password"
          value={val}
          onChange={(e) => setVal(e.target.value)}
          placeholder="วางโทเคนที่นี่"
          style={{ flex: 1, minWidth: 0, padding: "12px", borderRadius: 10, border: "1px solid var(--border)", background: "var(--card)", color: "var(--text)" }}
        />
        <button
          className="btn secondary"
          style={{ width: "auto" }}
          onClick={() => {
            setToken(val.trim());
            onChange();
          }}
        >
          {has ? "อัปเดต" : "บันทึก"}
        </button>
      </div>
    </div>
  );
}
