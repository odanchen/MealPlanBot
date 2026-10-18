export type Session = { authenticated: boolean; userId?: string; displayName?: string | null; email?: string | null; authMode: 'google' | 'local' };
export type ApiErrorBody = { code: string; message: string; fieldErrors?: Record<string, string>; currentVersion?: number };
export class ApiError extends Error {
  readonly status: number;
  readonly details: ApiErrorBody;
  constructor(status: number, details: ApiErrorBody) { super(details.message); this.status = status; this.details = details; }
}
export type RequestOptions = { method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'; body?: unknown; version?: number; idempotencyKey?: string; signal?: AbortSignal };
function apiUrl(path: string): string {
  const url = new URL(path, window.location.origin);
  if (!path.startsWith('/api/') || url.origin !== window.location.origin || url.username || url.password) throw new Error('Expected a same-origin API path');
  return url.pathname + url.search;
}
export const newIdempotencyKey = (): string => crypto.randomUUID();
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const target = apiUrl(path);
  const method = options.method ?? 'GET';
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (method !== 'GET') {
    // Obtain current session CSRF state for each write, including after login/logout.
    const csrf = await request<{ headerName: string; token: string }>('/api/csrf', { signal: options.signal });
    headers[csrf.headerName] = csrf.token;
  }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';
  if (options.version !== undefined) {
    if (!Number.isSafeInteger(options.version) || options.version < 0) throw new Error('Invalid entity version');
    headers['If-Match'] = `"${options.version}"`;
  }
  if (options.idempotencyKey) headers['Idempotency-Key'] = options.idempotencyKey;
  const response = await fetch(target, { method, headers, body: options.body === undefined ? undefined : JSON.stringify(options.body), signal: options.signal, credentials: 'same-origin', cache: 'no-store', redirect: 'error' });
  if (!response.ok) {
    let details: ApiErrorBody;
    try { details = await response.json() as ApiErrorBody; }
    catch { details = { code: 'request_failed', message: 'Request could not be completed' }; }
    throw new ApiError(response.status, details);
  }
  if (response.status === 204) return undefined as T;
  return await response.json() as T;
}
export const api = {
  session: (signal?: AbortSignal) => request<Session>('/api/session', { signal }),
  logout: () => request<void>('/api/logout', { method: 'POST' }),
  localSignIn: () => request<void>('/api/local-session', { method: 'POST' }),
};
export type ViewerDate = { timeZone: string; today: string };
export function datedPath(path: string, viewer: ViewerDate, from: string, to: string): string {
  const url = new URL(apiUrl(path), window.location.origin);
  url.searchParams.set('timeZone', viewer.timeZone); url.searchParams.set('from', from); url.searchParams.set('to', to);
  return url.pathname + url.search;
}
export function datedCacheKey(resource: string, viewer: ViewerDate, from: string, to: string): string {
  return JSON.stringify([resource, viewer.timeZone, viewer.today, from, to]);
}
