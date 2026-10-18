import { listSchedules } from '../api/listSchedules';
import { ListScheduleSettings } from '../components/ListScheduleSettings';
import { ListSchedulePreview } from '../components/ListSchedulePreview';
import { ArchivePolicySettings } from '../components/ArchivePolicySettings';
import { useLiveShoppingList } from '../hooks/useLiveShoppingList';
import { ListSharingPanel } from '../components/ListSharingPanel';
import { ListMembershipActions } from '../components/ListMembershipActions';
import { useCallback, useEffect, useRef, useState } from 'react';
import { shopping, type ShoppingEntry, type Addition } from '../api/shopping';
import { ApiError, newIdempotencyKey } from '../api/client';
import { useSession } from '../auth/SessionProvider';
import { FormField } from '../components/FormField';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { ShoppingItemRow } from '../components/ShoppingItemRow';
const units = ['g', 'kg', 'oz', 'lb', 'ml', 'l', 'us-cup', 'us-tbsp', 'us-tsp', 'us-fl-oz', 'count', 'clove', 'tin-156ml'];
export function ShoppingListPage({ id }: { id: string }) {
 const { refresh } = useSession();
 const load = useCallback(async (signal: AbortSignal) => { const [list, result, schedule] = await Promise.all([shopping.detail(id, signal), shopping.entries(id, signal), listSchedules.state(id, signal)]); return { list, entries: result.entries, schedule }; }, [id]);
 const live = useLiveShoppingList(load); const list = live.data?.list; const entries = live.data?.entries ?? []; const [name, setName] = useState(''); const [quantity, setQuantity] = useState(''); const [unit, setUnit] = useState('');
 const [error, setError] = useState(''); const [busy, setBusy] = useState(false); const [removing, setRemoving] = useState<ShoppingEntry | null>(null);
 const focusAfterRemoval = useRef(false); const nameInput = useRef<HTMLInputElement>(null); const pending = useRef<{ payload: string; key: string } | null>(null);
 useEffect(() => { if (!removing && focusAfterRemoval.current) { focusAfterRemoval.current = false; nameInput.current?.focus(); } }, [removing]);
 async function mutate(action: () => Promise<unknown>, after?: () => void) {
  if (busy || !live.connected || !navigator.onLine) return; setBusy(true); setError('');
  try { await action(); after?.(); await live.refresh(); }
  catch (e) {
   setError((e as Error).message);
   if (e instanceof ApiError && e.status === 401) await refresh();
   if (e instanceof ApiError && e.status === 409) { try { await live.refresh(); } catch { /* Preserve the conflict message until the next attempt. */ } }
  } finally { setBusy(false); }
 }
 function add() {
  const input: Addition = { name, ...(quantity ? { quantity } : {}), ...(unit ? { unit } : {}) }; const payload = JSON.stringify(input);
  if (!pending.current || pending.current.payload !== payload) pending.current = { payload, key: newIdempotencyKey() };
  const key = pending.current.key;
  void mutate(() => shopping.add(id, input, key), () => { setName(''); setQuantity(''); setUnit(''); pending.current = null; nameInput.current?.focus(); });
 }
 return <><a href="/lists">← Shopping lists</a><h2>{list?.name || 'Shopping list'}</h2>{(error || live.error) && !removing && <p role="alert">{error || live.error}</p>}{!live.connected && !live.inaccessible && <p role="status">Disconnected or outdated. Changes are disabled until refreshed. <button onClick={() => { void live.refresh(); }}>Retry connection</button></p>}
 {list ? <>{!live.data?.schedule.attachment && <p>No meal schedule attached. Automatic additions are off.</p>}<form onSubmit={event => { event.preventDefault(); add(); }}>
 <FormField label="Item name">{field => <input id={field} ref={nameInput} required maxLength={200} value={name} onChange={event => setName(event.target.value)} />}</FormField>
 <FormField label="Quantity (optional)" hint="Supply both quantity and unit, or leave both empty.">{(field, hint) => <input id={field} aria-describedby={hint} inputMode="decimal" value={quantity} onChange={event => setQuantity(event.target.value)} />}</FormField>
 <FormField label="Unit (optional)">{field => <select id={field} value={unit} onChange={event => setUnit(event.target.value)}><option value="">No unit</option>{units.map(value => <option key={value} value={value}>{value}</option>)}</select>}</FormField><button disabled={busy || !live.connected}>Add item</button></form>
 <p><a href={`/lists/${id}/archive`}>View archive</a></p><h3>Purchases</h3>{entries.length === 0 ? <p>Your list is empty. Add your first item above.</p> : <ul className="shopping-items">{entries.map(entry => <ShoppingItemRow key={entry.id} entry={entry} disabled={busy || !live.connected} onBought={bought => { void mutate(() => shopping.bought(id, entry, bought)); }} onRemove={() => { setError(''); setRemoving(entry); }} />)}</ul>}
 {live.data?.schedule.attachment && <ListSchedulePreview key={live.data.schedule.attachment.id} id={id} state={live.data.schedule} owner={list.owner} disabled={!live.connected || busy} onSaved={live.refresh} />}
 {list.owner && live.data && <><ListScheduleSettings list={list} state={live.data.schedule} disabled={!live.connected || busy} onSaved={live.refresh} /><ArchivePolicySettings list={list} disabled={!live.connected || busy} onSaved={live.refresh} /></>}
 {list.owner && <ListSharingPanel id={id} disabled={!live.connected || busy} />}<ListMembershipActions list={list} disabled={!live.connected || busy} />
 {removing && <ConfirmDialog title={`Remove ${removing.name}?`} description="This purchase will be permanently removed from this list." error={error} busy={busy} onCancel={() => setRemoving(null)} onConfirm={() => { void mutate(() => shopping.remove(id, removing), () => { focusAfterRemoval.current = true; setRemoving(null); }); }} />}</> : !error && !live.error && <p role="status">Loading list…</p>}</>;
}
