import { useCallback, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { nextPage, ResultItem, search, SearchResponse } from "../lib/api";
import ResultsGrid from "../components/ResultsGrid";
import Lightbox from "../components/Lightbox";

type Phase = "pick" | "uploading" | "searching" | "results";

export default function SearchPage() {
  const { eventId = "" } = useParams();
  const [file, setFile] = useState<File | null>(null);
  const [previewSrc, setPreviewSrc] = useState<string | null>(null);
  const [phase, setPhase] = useState<Phase>("pick");
  const [resp, setResp] = useState<SearchResponse | null>(null);
  const [items, setItems] = useState<ResultItem[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState<ResultItem | null>(null);
  const fileInput = useRef<HTMLInputElement | null>(null);
  const cameraInput = useRef<HTMLInputElement | null>(null);

  function choose(f: File | null) {
    if (!f) return;
    setFile(f);
    setPreviewSrc(URL.createObjectURL(f));
    setResp(null);
    setItems([]);
    setError(null);
    setPhase("pick");
  }

  async function doSearch() {
    if (!file) return;
    setError(null);
    setPhase("uploading");
    try {
      // สถานะตามขั้นตอนจริง: กำลังส่งรูป → กำลังค้นหา
      setPhase("searching");
      const r = await search(eventId, file);
      setResp(r);
      if (r.status === "ok") {
        setItems(r.items);
        setCursor(r.nextCursor ?? null);
        setPhase("results");
      } else {
        setPhase("pick");
      }
    } catch (e: any) {
      setError(e.message || "เครือข่ายขัดข้อง");
      setPhase("pick");
    }
  }

  const loadMore = useCallback(async () => {
    if (!resp?.searchToken || !cursor || loadingMore) return;
    setLoadingMore(true);
    try {
      const p = await nextPage(eventId, resp.searchToken, cursor);
      if (p.status === "expired") {
        setError("ผลการค้นหาหมดอายุ กรุณาค้นหาอีกครั้ง");
        setCursor(null);
      } else {
        setItems((prev) => [...prev, ...p.items]);
        setCursor(p.nextCursor ?? null);
      }
    } catch (e: any) {
      setError(e.message);
    } finally {
      setLoadingMore(false);
    }
  }, [resp, cursor, loadingMore, eventId]);

  return (
    <div className="app">
      <p><Link to={`/e/${eventId}`} className="muted">← กลับหน้ากิจกรรม</Link></p>

      <div className="card">
        <h2>เลือกรูปใบหน้าของคุณ</h2>
        <p className="muted small">ใช้รูปที่เห็นใบหน้าชัด ตรง ไม่เบลอ และมีใบหน้าเดียว</p>

        {previewSrc && <img className="preview-img mt" src={previewSrc} alt="ตัวอย่างรูปที่เลือก" />}

        <div className="row mt">
          <button className="btn secondary" onClick={() => fileInput.current?.click()}>
            🖼️ เลือกรูปจากเครื่อง
          </button>
          <button className="btn secondary" onClick={() => cameraInput.current?.click()}>
            📷 ถ่ายรูป
          </button>
        </div>
        <input
          ref={fileInput}
          type="file"
          accept="image/*"
          onChange={(e) => choose(e.target.files?.[0] ?? null)}
        />
        <input
          ref={cameraInput}
          type="file"
          accept="image/*"
          capture="user"
          onChange={(e) => choose(e.target.files?.[0] ?? null)}
        />

        {file && phase === "pick" && (
          <button className="btn mt" onClick={doSearch}>ค้นหารูปของฉัน</button>
        )}

        {error && <div className="notice error mt" role="alert">{error}</div>}

        {/* สถานะระหว่างค้นหา — ไม่ใช้เปอร์เซ็นต์ปลอม */}
        {(phase === "uploading" || phase === "searching") && (
          <div className="status-line">
            <span className="spinner" />
            {phase === "uploading" ? "กำลังส่งรูป…" : "กำลังค้นหาใบหน้าของคุณ…"}
          </div>
        )}

        {/* สถานะผลลัพธ์แยกกรณีชัดเจน */}
        {resp && resp.status === "no_face" && (
          <div className="notice warn mt">ไม่พบใบหน้าในรูป กรุณาถ่ายหรือเลือกรูปที่เห็นใบหน้าชัด</div>
        )}
        {resp && resp.status === "multiple_faces" && (
          <div className="notice warn mt">
            พบ {resp.faceCount} ใบหน้าในรูป กรุณาใช้รูปที่มีใบหน้าของคุณคนเดียว
          </div>
        )}
        {resp && resp.status === "not_ready" && (
          <div className="notice info mt">กิจกรรมกำลังเตรียมรูป/โหลดดัชนี กรุณาลองใหม่อีกครั้ง</div>
        )}
        {resp && resp.status === "busy" && (
          <div className="notice info mt">ขณะนี้มีผู้ใช้งานจำนวนมาก กรุณาลองใหม่ในอีกสักครู่</div>
        )}
      </div>

      {phase === "results" && resp?.status === "ok" && (
        <div className="card">
          {items.length === 0 ? (
            <div className="center">
              <p style={{ fontSize: "2.4rem", margin: 0 }}>🔍</p>
              <h2>ไม่พบรูปที่ใกล้เคียง</h2>
              <p className="muted">ลองใช้รูปใบหน้าอีกใบ หรือรูปที่ชัดกว่านี้</p>
              <button className="btn secondary mt" onClick={() => setPhase("pick")}>ลองรูปอื่น</button>
            </div>
          ) : (
            <>
              <h2>พบ {resp.total.toLocaleString("th-TH")} รูปที่น่าจะเป็นคุณ</h2>
              <p className="muted small">แตะที่รูปเพื่อดูใหญ่และดาวน์โหลด</p>
              <ResultsGrid
                items={items}
                onOpen={setOpen}
                onLoadMore={loadMore}
                hasMore={!!cursor}
                loadingMore={loadingMore}
              />
              <button className="btn secondary mt" onClick={() => setPhase("pick")}>
                ค้นหาด้วยรูปอื่น
              </button>
            </>
          )}
        </div>
      )}

      {open && <Lightbox item={open} onClose={() => setOpen(null)} />}
    </div>
  );
}
