import { request } from './client';
export type Invitation = { id: string; listId: string; createdAt: string; expiresAt: string; withdrawnAt: string | null };
export const invitations = {
 list: (id: string) => request<{ invitations: Invitation[] }>(`/api/lists/${encodeURIComponent(id)}/invitations`),
 create: (id: string) => request<{ invitation: Invitation; link: string }>(`/api/lists/${encodeURIComponent(id)}/invitations`, { method: 'POST' }),
 withdraw: (id: string, invite: string) => request<void>(`/api/lists/${encodeURIComponent(id)}/invitations/${encodeURIComponent(invite)}`, { method: 'DELETE' }),
 accept: (token: string) => request<{ listId: string }>('/api/invitations/accept', { method: 'POST', body: { token } }),
};
export const pendingInvitationKey = 'mealplan.pending-invitation';
// Run before session bootstrap so even an anonymous sign-in shell removes the secret fragment.
export function captureInvitation() {
 if (window.location.pathname !== '/join') return;
 const token = new URLSearchParams(window.location.hash.slice(1)).get('token');
 if (token) sessionStorage.setItem(pendingInvitationKey, token);
 if (window.location.hash) window.history.replaceState(null, '', window.location.pathname + window.location.search);
}
