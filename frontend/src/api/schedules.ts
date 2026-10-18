import { request, datedPath, newIdempotencyKey, type ViewerDate } from "./client";
export type UserSchedule = { id: string; templateId: string; anchorDate: string; version: number };
export type Meal = {
  date: string;
  status: "not_started" | "rest" | "meal";
  cycleDay?: number;
  recipeId?: string;
};
export const schedules = {
  templates: () =>
    request<{ templates: { id: string; title: string; cycleLength: number }[] }>(
      "/api/schedule-templates",
    ),
  list: () => request<{ schedules: UserSchedule[] }>("/api/user-schedules"),
  create: (templateId: string, anchorDate: string) =>
    request<UserSchedule>("/api/user-schedules", {
      method: "POST",
      body: { templateId, anchorDate },
      idempotencyKey: newIdempotencyKey(),
    }),
  get: (id: string) =>
    request<{ schedule: UserSchedule; linkedLists: { id: string; name: string }[] }>(
      `/api/user-schedules/${encodeURIComponent(id)}`,
    ),
  delete: (s: UserSchedule) =>
    request<void>(`/api/user-schedules/${encodeURIComponent(s.id)}`, {
      method: "DELETE",
      version: s.version,
    }),
  update: (s: UserSchedule, anchorDate: string) =>
    request<UserSchedule>(`/api/user-schedules/${encodeURIComponent(s.id)}`, {
      method: "PATCH",
      version: s.version,
      body: { anchorDate },
    }),
  meals: (id: string, viewer: ViewerDate, from: string, to: string, signal?: AbortSignal) =>
    request<{ revision: string; meals: Meal[] }>(
      datedPath(`/api/user-schedules/${encodeURIComponent(id)}/meals`, viewer, from, to),
      { signal },
    ),
};
export function nextDate(date: string) {
  const d = new Date(`${date}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + 1);
  return d.toISOString().slice(0, 10);
}
