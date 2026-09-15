import { useEffect } from "react";
import { ResultItem } from "../lib/api";

export default function Lightbox({
  item,
  onClose,
}: {
  item: ResultItem;
  onClose: () => void;
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div className="lightbox" role="dialog" aria-modal="true" aria-label="ดูรูปขนาดใหญ่">
      <button className="close" onClick={onClose} aria-label="ปิด">✕</button>
      <div className="body">
        {/* preview ก่อน (โหลดเร็ว) ให้ผู้ใช้เห็นทันที */}
        <img src={item.previewUrl} alt="รูปที่ตรงกับใบหน้าของคุณ" />
      </div>
      <div className="bar">
        <a className="btn" href={item.downloadUrl} download>
          ⬇️ ดาวน์โหลดรูปนี้
        </a>
      </div>
    </div>
  );
}
