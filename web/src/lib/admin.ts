// Admin API client — ใช้ bearer token ของผู้ดูแล (เก็บใน localStorage ต่อเครื่อง)

const TOKEN_KEY = "photosearch_admin_token";

export function getToken(): string {
  try {
    return localStorage.getItem(TOKEN_KEY) || "";
  } catch {
    return "";
  }
}
export function setToken(t: string) {
  try {
    localStorage.setItem(TOKEN_KEY, t);
  } catch {
    /* ignore */
  }
}

function h(): HeadersInit {
  return { Authorization: `Bearer ${getToken()}`, "Content-Type": "application/json" };
}

async function j<T>(r: Response): Promise<T> {
  if (r.status === 401) throw new Error("โทเคนผู้ดูแลไม่ถูกต้อง");
  if (!r.ok) throw new Error(`ผิดพลาด (${r.status})`);
  return r.json();
}

export interface AdminEventRow {
  eventId: string;
  title?: string;
  status?: string;
  public?: boolean;
  ready: boolean;
  photoCount: number;
  generationId?: string | null;
  updatedAt?: string | null;
}

export interface EventDoc {
  schemaVersion?: number;
  eventId: string;
  title: string;
  date?: string | null;
  coverPreviewId?: string | null;
  originalsFolderId: string;
  status: "draft" | "open" | "closed";
  public: boolean;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface PrepareStatus {
  eventId: string;
  state: string;
  running?: boolean;
  total?: number;
  processed?: number;
  ok?: number;
  no_face?: number;
  failed?: number;
  generationId?: string | null;
  updatedAt?: string | null;
  message?: string | null;
}

export const admin = {
  list: () => fetch("/api/admin/events", { headers: h() }).then(j<{ events: AdminEventRow[] }>),
  get: (id: string) => fetch(`/api/admin/events/${id}/full`, { headers: h() }).then(j<EventDoc>),
  upsert: (doc: EventDoc) =>
    fetch("/api/admin/events", { method: "POST", headers: h(), body: JSON.stringify(doc) }).then(j<EventDoc>),
  setStatus: (id: string, status: string) =>
    fetch(`/api/admin/events/${id}/status?status=${status}`, { method: "POST", headers: h() }).then(j),
  setPublic: (id: string, pub: boolean) =>
    fetch(`/api/admin/events/${id}/public?public=${pub}`, { method: "POST", headers: h() }).then(j),
  prepare: (id: string) =>
    fetch(`/api/admin/events/${id}/prepare`, { method: "POST", headers: h() }).then(
      j<{ started: boolean; running: boolean }>,
    ),
  prepareStatus: (id: string) =>
    fetch(`/api/admin/events/${id}/prepare-status`, { headers: h() }).then(j<PrepareStatus>),
  removePhotos: (id: string, ids: string[]) =>
    fetch(`/api/admin/events/${id}/photos/remove`, { method: "POST", headers: h(), body: JSON.stringify(ids) }).then(j),
  restorePhotos: (id: string, ids: string[]) =>
    fetch(`/api/admin/events/${id}/photos/restore`, { method: "POST", headers: h(), body: JSON.stringify(ids) }).then(j),
};
