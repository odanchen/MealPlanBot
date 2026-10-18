import { useCallback, useState } from 'react';
import { shopping, type ShoppingEntry } from '../api/shopping';
import { useLiveShoppingList } from '../hooks/useLiveShoppingList';
import { ConfirmDialog } from '../components/ConfirmDialog';
export function ShoppingArchivePage({ id }: { id: string }) {
 const load = useCallback(async (signal: AbortSignal) => { const [list, result] = await Promise.all([shopping.detail(id, signal), shopping.archive(id, signal)]); return { list, entries: result.entries }; }, [id]);
 const live = useLiveShoppingList(load); const [selection, setSelection] = useState<ShoppingEntry | 'all' | null>(null); const [error, setError] = useState(''); const [busy, setBusy] = useState(false);
 async function clear() { if (!selection || busy || !live.connected || !navigator.onLine) return; setBusy(true); setError(''); try { if (selection === 'all') await shopping.clearArchive(id); else await shopping.remove(id, selection); setSelection(null); await live.refresh(); } catch (e) { setError((e as Error).message); await live.refresh(); } finally { setBusy(false); } }
 return <><a href={`/lists/${id}`}>← Back to list</a><h2>{live.data ? `${live.data.list.name} archive` : 'Shopping archive'}</h2>{(live.error || error) && !selection && <p role="alert">{error || live.error}</p>}{!live.connected && !live.inaccessible && <p role="status">Disconnected or outdated. Clearing is disabled until reconnected.</p>}
 {live.data && <>{live.data.entries.length === 0 ? <p>The archive is empty.</p> : <><button disabled={busy || !live.connected} onClick={() => setSelection('all')}>Clear archive</button><ul>{live.data.entries.map(entry => <li key={entry.id}>{entry.name}{entry.quantity && ` · ${entry.quantity} ${entry.unit}`} · {entry.boughtAt ? 'Bought' : 'Not bought'} <button disabled={busy || !live.connected} onClick={() => setSelection(entry)} aria-label={`Clear ${entry.name}`}>Clear</button></li>)}</ul></>}</>}
 {selection && <ConfirmDialog title={selection === 'all' ? 'Clear archive?' : `Clear ${selection.name}?`} description="Permanently remove the selected archived purchases. Active purchases will stay on the list." confirmLabel={selection === 'all' ? 'Clear all archived items' : 'Clear archived item'} error={error} busy={busy} onCancel={() => setSelection(null)} onConfirm={() => { void clear(); }} />}</>;
}
