import { request, newIdempotencyKey, datedPath, type ViewerDate } from './client';
import type { ShoppingList } from './shopping';
import type { Meal } from './schedules';
export type Addition = { unit: 'day' | 'week'; count: number; weekday: number | null; localTime: string; timeZone: string; startDate: string };
export type Attachment = { id: string; sourceScheduleId: string; enabled: boolean; addition: Addition; nextDueAt: string; lastCoveredStartDate: string | null; lastCoveredEndDateExclusive: string | null };
export type Occurrence = { id: string; kind: string; status: string; failureCode: string | null; coverageStartDate: string; coverageEndDateExclusive: string };
export type ScheduleState = { attachment: Attachment | null; latest: Occurrence | null; templateId: string | null; anchorDate: string | null; sourceVersion: number | null };
export type Preview = { coverageStartDate: string; coverageEndDateExclusive: string; previouslyCoveredEndDate: string | null; displayedFrom: string | null; displayedTo: string | null; isPartial: boolean; beyondHorizon: boolean; plan: { from: string; to: string; meals: Meal[]; ingredients: { ingredientId: string; name: string; quantity: string; unit: string }[] } | null };
const path = (id: string) => `/api/lists/${encodeURIComponent(id)}/schedule`;
export const listSchedules = {
 state: (id: string, signal?: AbortSignal) => request<ScheduleState>(path(id), { signal }),
 configure: (list: ShoppingList, input: { userScheduleId: string; addition: Addition; enabled: boolean }, key: string = newIdempotencyKey()) => request<ScheduleState>(path(list.id), { method: 'PUT', body: input, version: list.version, idempotencyKey: key }),
 detach: (list: ShoppingList) => request<void>(path(list.id), { method: 'DELETE', version: list.version }),
 retry: (id: string, occurrenceId: string) => request<ScheduleState>(`${path(id)}/retry`, { method: 'POST', body: { occurrenceId } }),
 preview: (id: string, viewer: ViewerDate, signal?: AbortSignal) => request<Preview>(`${path(id)}/preview?${new URLSearchParams({ timeZone: viewer.timeZone })}`, { signal }),
 meals: (id: string, viewer: ViewerDate, from: string, to: string, signal?: AbortSignal) => request<{ meals: Meal[] }>(datedPath(`${path(id)}/meals`, viewer, from, to), { signal }),
 policy: (list: ShoppingList, count: number, unit: string) => request<ShoppingList>(`/api/lists/${list.id}/archive-policy`, { method: 'PUT', body: { ageLimit: { count, unit } }, version: list.version }),
};
