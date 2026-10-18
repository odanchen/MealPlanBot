import { useState } from 'react';
import type { ShoppingList } from '../api/shopping';
import { listSchedules } from '../api/listSchedules';
export function ArchivePolicySettings({ list, disabled, onSaved }: { list: ShoppingList; disabled: boolean; onSaved: () => Promise<void> }) {
 const [count, setCount] = useState(list.itemAgeLimitSeconds / 86400); const [unit, setUnit] = useState('day'); const [busy, setBusy] = useState(false); const [error, setError] = useState('');
 async function save() { if (disabled || busy || !navigator.onLine) return; setBusy(true); setError(''); try { await listSchedules.policy(list,count,unit); await onSaved(); } catch (e) { setError((e as Error).message); await onSaved(); } finally { setBusy(false); } }
 return <section><h3>Archive age limit</h3><p>Each item archives independently from its creation time, whether bought or not. Days are 24 hours. Extending the limit does not restore archived items.</p>{error && <p role="alert">{error}</p>}<form onSubmit={event => { event.preventDefault(); void save(); }}><label>Item age limit<input type="number" min="1" required value={count} onChange={event => setCount(Number(event.target.value))} /></label><label>Age unit<select value={unit} onChange={event => setUnit(event.target.value)}><option value="day">Days</option><option value="week">Weeks</option></select></label><button disabled={disabled || busy}>Save archive policy</button></form></section>;
}
