import { request } from './client';
export type ShoppingList = { id: string; name: string; ownerUserId: string; owner: boolean; createdAt: string; itemAgeLimitSeconds: number; version: number; schedule: { id: string; enabled: boolean; nextDueAt: string } | null };
export type ShoppingEntry = { id: string; listId: string; name: string; ingredientId: string | null; quantity: string | null; unit: string | null; createdAt: string; boughtAt: string | null; archivedAt: string | null; version: number };
export type Addition = { name: string; quantity?: string; unit?: string };
const path = (id: string) => `/api/lists/${encodeURIComponent(id)}`;
export const shopping = {
 delete: (list: ShoppingList) => request<void>(path(list.id), { method: 'DELETE', version: list.version }),
 leave: (id: string) => request<void>(`${path(id)}/membership`, { method: 'DELETE' }),
 archive: (id: string, signal?: AbortSignal) => request<{ entries: ShoppingEntry[] }>(`${path(id)}/archive`, { signal }),
 clearArchive: (id: string) => request<void>(`${path(id)}/archive`, { method: 'DELETE' }),
 catalogue: (signal?: AbortSignal) => request<{ lists: ShoppingList[] }>('/api/lists', { signal }),
 create: (name: string, key: string) => request<ShoppingList>('/api/lists', { method: 'POST', body: { name }, idempotencyKey: key }),
 detail: (id: string, signal?: AbortSignal) => request<ShoppingList>(path(id), { signal }),
 entries: (id: string, signal?: AbortSignal) => request<{ entries: ShoppingEntry[] }>(`${path(id)}/entries`, { signal }),
 add: (id: string, input: Addition, key: string) => request<ShoppingEntry>(`${path(id)}/entries`, { method: 'POST', body: input, idempotencyKey: key }),
 bought: (id: string, entry: ShoppingEntry, bought: boolean) => request<ShoppingEntry>(`${path(id)}/entries/${entry.id}`, { method: 'PATCH', body: { bought }, version: entry.version }),
 remove: (id: string, entry: ShoppingEntry) => request<void>(`${path(id)}/entries/${entry.id}`, { method: 'DELETE', version: entry.version }),
};
