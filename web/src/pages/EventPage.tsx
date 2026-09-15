import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { getEvent, EventPublic } from "../lib/api";

function formatUpdated(iso?: string | null): string {
  if (!iso) return "";
  try {
    return new Date(iso).toLocaleString("th-TH", { dateStyle: "medium", timeStyle: "short" });
  } catch {
    return iso;
  }
}

export default function EventPage() {
  const { eventId = "" } = useParams();
  const nav = useNavigate();
  const [ev, setEv] = useState<EventPublic | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    getEvent(eventId)
      .then((e) => alive && (setEv(e), setError(null)))
      .catch((err) => alive && setError(err.message))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [eventId]);

  if (loading) {
    return (
      <div className="app">
        <div className="card">
          <div className="skeleton cover" style={{ aspectRatio: "16/9" }} />
          <div className="status-line"><span className="spinner" /> กำลังโหลดข้อมูลกิจกรรม…</div>
        </div>
      </div>
    );
  }

  if (error || !ev) {
    return (
      <div className="app">
        <div className="card">
          <div className="notice error">{error || "ไม่พบกิจกรรม"}</div>
          <p className="muted">ตรวจสอบลิงก์หรือ QR Code ของงานอีกครั้ง</p>
        </div>
      </div>
    );
  }

  const closed = ev.status === "closed";

  return (
    <div className="app">
      <div className="card">
        {ev.coverUrl ? <img className="cover" src={ev.coverUrl} alt="" /> : <div className="cover" />}
        <h1>{ev.title}</h1>
        {ev.date && <p className="muted" style={{ margin: "0 0 8px" }}>{ev.date}</p>}

        {ev.ready ? (
          <p><span className="badge">พร้อมค้นหา {ev.photoCount.toLocaleString("th-TH")} รูป</span></p>
        ) : (
          <div className="notice warn">
            ระบบกำลังเตรียมรูปสำหรับกิจกรรมนี้ กรุณากลับมาใหม่อีกครั้ง
          </div>
        )}
        {closed && <div className="notice info">กิจกรรมนี้ปิดการค้นหาแล้ว</div>}

        <button
          className="btn mt"
          disabled={!ev.ready || closed}
          onClick={() => nav(`/e/${eventId}/search`)}
        >
          🔍 ค้นหารูปของฉัน
        </button>

        {ev.updatedAt && (
          <p className="small muted mt">อัปเดตชุดรูปล่าสุด: {formatUpdated(ev.updatedAt)}</p>
        )}
      </div>
      <p className="small muted center">
        เราค้นหาเฉพาะรูปภายในกิจกรรมนี้เท่านั้น รูปเซลฟีของคุณใช้เพื่อค้นหาชั่วคราวและไม่ถูกจัดเก็บ
      </p>
    </div>
  );
}
