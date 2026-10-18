import { useEffect, useState } from 'react';
import { invitations, type Invitation } from '../api/invitations';
export function ListSharingPanel({ id, disabled }: { id: string; disabled: boolean }) {
 const [items, setItems] = useState<Invitation[]>([]); const [link, setLink] = useState(''); const [error, setError] = useState(''); const [busy, setBusy] = useState(false);
 useEffect(() => { let active = true; void invitations.list(id).then(result => { if (active) setItems(result.invitations); }).catch((e: Error) => { if (active) setError(e.message); }); return () => { active = false; }; }, [id]);
 async function perform(action: () => Promise<unknown>) { if (busy || disabled || !navigator.onLine) return; setBusy(true); setError(''); try { await action(); setItems((await invitations.list(id)).invitations); } catch (e) { setError((e as Error).message); } finally { setBusy(false); } }
 return <section aria-label="Share list"><h3>Share this list</h3><p>Anyone signed in with your link can join for 72 hours. Links can be used by more than one person.</p>{error && <p role="alert">{error}</p>}
 <button disabled={busy || disabled} onClick={() => { void perform(async () => { const result = await invitations.create(id); setLink(new URL(result.link, window.location.origin).href); }); }}>Create invitation</button>
 {link && <p><label>Invitation link<input readOnly value={link} onFocus={event => event.target.select()} /></label>Copy this link now. It is shown only when created.</p>}
 <ul>{items.map((invite, index) => <li key={invite.id}>Invitation {items.length - index} · {invite.withdrawnAt ? 'Withdrawn' : `Expires ${new Date(invite.expiresAt).toLocaleString()}`} {!invite.withdrawnAt && <button disabled={busy || disabled} onClick={() => { void perform(async () => { await invitations.withdraw(id, invite.id); setLink(''); }); }} aria-label={`Withdraw invitation ${items.length - index}`}>Withdraw</button>}</li>)}</ul></section>;
}
