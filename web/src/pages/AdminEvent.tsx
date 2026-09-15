import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import QRCode from "qrcode";
import { admin, EventDoc, PrepareStatus } from "../lib/admin";

export default function AdminEvent() {
  const { eventId = "" } = useParams();
  const [doc, setDoc] = useState<EventDoc | null>(null);
  const [status, setStatus] = useState<PrepareStatus | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [removeIds, setRemoveIds] = useState("");
  const [qrData, setQrData] = useState<string>("");
  const poll = useRef<number | null>(null);

  const eventUrl = `${location.origin}/e/${eventId}`;

  // สร้าง QR ในเครื่อง (ไม่ส่ง URL กิจกรรมไปบริการภายนอก)
  useEffect(() => {
    QRCode.toDataURL(eventUrl, { width: 220, margin: 1 }).then(setQrData).catch(() => setQrData(""));
  }, [eventUrl]);

  const loadDoc = useCallback(() => {
    admin.get(eventId).then(setDoc).catch((e) => setError(e.message));
  }, [eventId]);

  const loadStatus = useCallback(() => {
    admin.prepareStatus(eventId).then(setStatus).catch(() => {});
  }, [eventId]);

  useEffect(() => {
    loadDoc();
    loadStatus();
  }, [loadDoc, loadStatus]);

  // poll ระหว่างเตรียมรูป
  useEffect(() => {
    if (status?.running || status?.state === "running" || status?.state === "publishing") {
      poll.current = window.setInterval(loadStatus, 1500);
      return () => {
        if (poll.current) window.clearInterval(poll.current);
      };
    }
  }, [status?.running, status?.state, loadStatus]);

  async function act(fn: () => Promise<unknown>, ok: string) {
    setError(null);
    setMsg(null);
    try {
      await fn();
      setMsg(ok);
      loadDoc();
      loadStatus();
    } catch (e: any) {
      setError(e.message);
    }
  }

  if (error && !doc) return <div className="app"><div className="notice error">{error}</div></div>;
  if (!doc) return <div className="app"><div className="status-line"><span className="spinner" />กำลังโหลด…</div></div>;

  return (
    <div className="app">
      <p><Link to="/admin" className="muted">← รายการกิจกรรม</Link></p>
      <h1>{doc.title}</h1>
      <p className="muted small">{doc.eventId}</p>

      {error && <div className="notice error">{error}</div>}
      {msg && <div className="notice info">{msg}</div>}

      <div className="card">
        <h2>สถานะ</h2>
        <div className="row">
          <button className="btn secondary" style={{ width: "auto" }} onClick={() => act(() => admin.setStatus(eventId, "open"), "เปิดกิจกรรมแล้ว")}>เปิด</button>
          <button className="btn secondary" style={{ width: "auto" }} onClick={() => act(() => admin.setStatus(eventId, "closed"), "ปิดกิจกรรมแล้ว")}>ปิด</button>
          <button className="btn secondary" style={{ width: "auto" }} onClick={() => act(() => admin.setPublic(eventId, !doc.public), "อัปเดตการเข้าถึงแล้ว")}>
            {doc.public ? "ตั้งเป็นไม่สาธารณะ" : "ตั้งเป็นสาธารณะ"}
          </button>
        </div>
        <p className="small muted mt">สถานะ: <b>{doc.status}</b> · การเข้าถึง: <b>{doc.public ? "สาธารณะ" : "ไม่สาธารณะ"}</b></p>
      </div>

      <div className="card">
        <h2>เตรียมรูป</h2>
        <button
          className="btn"
          onClick={() => act(() => admin.prepare(eventId), "เริ่มเตรียมรูปแล้ว")}
          disabled={status?.running || status?.state === "running" || status?.state === "publishing"}
        >
          {status?.running ? "กำลังเตรียม…" : "เริ่มเตรียมรูป (ประมวลผลเฉพาะรูปใหม่/ที่เปลี่ยน)"}
        </button>
        {status && status.state !== "idle" && (
          <div className="mt">
            {(status.state === "running" || status.state === "publishing") && (
              <div className="status-line"><span className="spinner" /> {status.state === "publishing" ? "กำลังเผยแพร่รุ่นใหม่…" : `กำลังประมวลผล ${status.processed}/${status.total}`}</div>
            )}
            <div className="row small">
              <span className="badge">สำเร็จ {status.ok ?? 0}</span>
              <span className="badge">ไม่มีใบหน้า {status.no_face ?? 0}</span>
              <span className="badge">ล้มเหลว {status.failed ?? 0}</span>
            </div>
            {status.generationId && <p className="small muted mt">รุ่นข้อมูล: {status.generationId}</p>}
            {status.message && <div className="notice warn mt">{status.message}</div>}
          </div>
        )}
      </div>

      <div className="card">
        <h2>ลิงก์ & QR</h2>
        <p className="small" style={{ wordBreak: "break-all" }}>{eventUrl}</p>
        {qrData && <img src={qrData} alt="QR สำหรับกิจกรรม" width={220} height={220} style={{ background: "#fff", borderRadius: 10, padding: 8 }} />}
        <p className="small muted">QR สร้างในเครื่อง (ไม่ส่ง URL ไปบริการภายนอก)</p>
      </div>

      <div className="card">
        <h2>ถอน / คืนรูป</h2>
        <p className="small muted">ใส่ photoId คั่นด้วยเว้นวรรค/บรรทัด — มีผลทันทีโดยไม่ต้อง reindex</p>
        <textarea
          value={removeIds}
          onChange={(e) => setRemoveIds(e.target.value)}
          placeholder="p_ab12... p_cd34..."
          rows={3}
          style={{ width: "100%", padding: 10, borderRadius: 10, border: "1px solid var(--border)", background: "var(--card)", color: "var(--text)" }}
        />
        <div className="row mt">
          <button className="btn secondary" style={{ width: "auto" }} onClick={() => act(() => admin.removePhotos(eventId, ids(removeIds)), "ถอนรูปแล้ว")}>ถอนออกจากผลค้นหา</button>
          <button className="btn secondary" style={{ width: "auto" }} onClick={() => act(() => admin.restorePhotos(eventId, ids(removeIds)), "คืนรูปแล้ว")}>คืนรูป</button>
        </div>
      </div>
    </div>
  );
}

function ids(s: string): string[] {
  return s.split(/[\s,]+/).map((x) => x.trim()).filter(Boolean);
}
