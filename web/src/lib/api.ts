// API client — เรียก FastAPI โดยตรง (origin เดียวกัน)

export interface EventPublic {
  eventId: string;
  title: string;
  date?: string | null;
  coverUrl?: string | null;
  status: string;
  ready: boolean;
  photoCount: number;
  faceCount: number;
  generationId?: string | null;
  updatedAt?: string | null;
}

export interface ResultItem {
  photoId: string;
  score: number;
  previewUrl: string;
  downloadUrl: string;
}

export type SearchStatus =
  | "ok"
  | "no_face"
  | "multiple_faces"
  | "not_ready"
  | "busy";

export interface SearchResponse {
  status: SearchStatus;
  searchToken?: string | null;
  generationId?: string | null;
  total: number;
  faceCount: number;
  items: ResultItem[];
  nextCursor?: string | null;
  message?: string | null;
}

export interface PageResponse {
  status: "ok" | "expired" | "not_ready";
  items: ResultItem[];
  nextCursor?: string | null;
  total: number;
}

const base = "";

export async function getEvent(eventId: string): Promise<EventPublic> {
  const r = await fetch(`${base}/api/events/${encodeURIComponent(eventId)}`);
  if (r.status === 404) throw new Error("ไม่พบกิจกรรม");
  if (!r.ok) throw new Error("เชื่อมต่อไม่สำเร็จ");
  return r.json();
}

export async function search(eventId: string, file: Blob): Promise<SearchResponse> {
  const form = new FormData();
  form.append("selfie", file, "selfie.jpg");
  const r = await fetch(`${base}/api/events/${encodeURIComponent(eventId)}/search`, {
    method: "POST",
    body: form,
  });
  if (r.status === 413) throw new Error("ไฟล์ใหญ่เกินไป");
  if (r.status === 415) throw new Error("ชนิดไฟล์ไม่รองรับ");
  if (!r.ok) throw new Error("เครือข่ายขัดข้อง กรุณาลองใหม่");
  return r.json();
}

export async function nextPage(
  eventId: string,
  token: string,
  cursor: string,
): Promise<PageResponse> {
  const r = await fetch(
    `${base}/api/events/${encodeURIComponent(eventId)}/results?token=${encodeURIComponent(
      token,
    )}&cursor=${encodeURIComponent(cursor)}`,
  );
  if (!r.ok) throw new Error("โหลดหน้าถัดไปไม่สำเร็จ");
  return r.json();
}
