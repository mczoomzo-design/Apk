import { useEffect, useRef } from "react";
import { ResultItem } from "../lib/api";

export default function ResultsGrid({
  items,
  onOpen,
  onLoadMore,
  hasMore,
  loadingMore,
}: {
  items: ResultItem[];
  onOpen: (item: ResultItem) => void;
  onLoadMore: () => void;
  hasMore: boolean;
  loadingMore: boolean;
}) {
  const sentinel = useRef<HTMLDivElement | null>(null);

  // โหลดเพิ่มเมื่อเลื่อนใกล้ท้ายรายการ
  useEffect(() => {
    if (!hasMore) return;
    const el = sentinel.current;
    if (!el) return;
    const io = new IntersectionObserver(
      (entries) => entries[0].isIntersecting && onLoadMore(),
      { rootMargin: "400px" },
    );
    io.observe(el);
    return () => io.disconnect();
  }, [hasMore, onLoadMore]);

  return (
    <>
      <div className="grid">
        {items.map((it) => (
          <button
            key={it.photoId}
            className="thumb"
            onClick={() => onOpen(it)}
            aria-label={`เปิดรูป (ความคล้าย ${(it.score * 100).toFixed(0)}%)`}
          >
            {/* lazy loading — ไม่โหลดต้นฉบับเพื่อทำตาราง */}
            <img src={it.previewUrl} loading="lazy" alt="" />
          </button>
        ))}
      </div>
      {hasMore && (
        <div ref={sentinel} className="status-line center">
          {loadingMore && <span className="spinner" />}
          <span className="muted">กำลังโหลดเพิ่ม…</span>
        </div>
      )}
    </>
  );
}
