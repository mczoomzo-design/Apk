import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { admin, AdminEventRow, EventDoc, getToken } from "../lib/admin";
import AdminAuth from "../components/AdminAuth";

const emptyDoc: EventDoc = {
  eventId: "",
  title: "",
  date: "",
  originalsFolderId: "",
  status: "draft",
  public: true,
};

export default function AdminHome() {
  const [rows, setRows] = useState<AdminEventRow[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [authTick, setAuthTick] = useState(0);
  const [creating, setCreating] = useState(false);
  const [draft, setDraft] = useState<EventDoc>(emptyDoc);

  const load = useCallback(() => {
    if (!getToken()) return;
    admin
      .list()
      .then((r) => (setRows(r.events), setError(null)))
      .catch((e) => setError(e.message));
  }, [authTick]);

  useEffect(() => load(), [load]);

  async function create() {
    try {
      await admin.upsert(draft);
      setCreating(false);
      setDraft(emptyDoc);
      load();
    } catch (e: any) {
      setError(e.message);
    }
  }

  return (
    <div className="app">
      <h1>ผู้ดูแล — กิจกรรม</h1>
      <AdminAuth onChange={() => setAuthTick((t) => t + 1)} />
      {error && <div className="notice error">{error}</div>}

      {getToken() && (
        <>
          <div className="card">
            <div className="row" style={{ justifyContent: "space-between" }}>
              <h2 style={{ margin: 0 }}>กิจกรรมทั้งหมด ({rows.length})</h2>
              <button className="btn secondary" style={{ width: "auto" }} onClick={() => setCreating((v) => !v)}>
                {creating ? "ยกเลิก" : "+ สร้างกิจกรรม"}
              </button>
            </div>

            {creating && (
              <div className="mt" style={{ display: "grid", gap: 8 }}>
                <Field label="รหัสกิจกรรม (eventId)" v={draft.eventId} on={(v) => setDraft({ ...draft, eventId: v })} placeholder="wedding-2026" />
                <Field label="ชื่องาน" v={draft.title} on={(v) => setDraft({ ...draft, title: v })} placeholder="งานแต่งคุณเอ" />
                <Field label="วันที่" v={draft.date || ""} on={(v) => setDraft({ ...draft, date: v })} placeholder="2026-10-01" />
                <Field label="fileId โฟลเดอร์ต้นฉบับ (Drive)" v={draft.originalsFolderId} on={(v) => setDraft({ ...draft, originalsFolderId: v })} placeholder="events/<id>/originals หรือ Drive fileId" />
                <button className="btn" onClick={create} disabled={!draft.eventId || !draft.title || !draft.originalsFolderId}>
                  สร้าง
                </button>
              </div>
            )}
          </div>

          {rows.map((r) => (
            <Link key={r.eventId} to={`/admin/e/${r.eventId}`} className="card" style={{ display: "block", textDecoration: "none", color: "inherit" }}>
              <div className="row" style={{ justifyContent: "space-between", alignItems: "center" }}>
                <div>
                  <strong>{r.title || r.eventId}</strong>
                  <div className="small muted">{r.eventId}</div>
                </div>
                <div className="center">
                  <span className="badge">{r.status}</span>
                  <div className="small muted mt">{r.ready ? `${r.photoCount} รูปพร้อม` : "ยังไม่พร้อม"}</div>
                </div>
              </div>
            </Link>
          ))}
        </>
      )}
    </div>
  );
}

function Field({ label, v, on, placeholder }: { label: string; v: string; on: (v: string) => void; placeholder?: string }) {
  return (
    <label className="small">
      {label}
      <input
        value={v}
        placeholder={placeholder}
        onChange={(e) => on(e.target.value)}
        style={{ width: "100%", padding: 10, borderRadius: 10, border: "1px solid var(--border)", background: "var(--card)", color: "var(--text)", marginTop: 4 }}
      />
    </label>
  );
}
