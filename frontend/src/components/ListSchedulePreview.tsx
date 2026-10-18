import { useCallback, useState } from 'react';
import { listSchedules, type ScheduleState } from '../api/listSchedules';
import { nextDate } from '../api/schedules';
import { useViewerTimeZone } from '../hooks/useViewerTimeZone';
import { useLiveShoppingList } from '../hooks/useLiveShoppingList';
export function ListSchedulePreview({ id, state, owner, disabled, onSaved }: { id: string; state: ScheduleState; owner: boolean; disabled: boolean; onSaved: () => Promise<void> }) {
 const viewer = useViewerTimeZone(); const [chosen, setChosen] = useState(''); const date = chosen || viewer.today; const [error, setError] = useState(''); const [busy, setBusy] = useState(false);
 const load = useCallback(async (signal: AbortSignal) => { const [preview, meals] = await Promise.all([listSchedules.preview(id, viewer, signal), listSchedules.meals(id, viewer, date, nextDate(date), signal)]); return { preview, meals: meals.meals }; }, [id, viewer.timeZone, viewer.today, date]);
 const live = useLiveShoppingList(load); const a = state.attachment;
 async function retry() { if (!state.latest || disabled || busy || !navigator.onLine) return; setBusy(true); try { await listSchedules.retry(id, state.latest.id); await onSaved(); await live.refresh(); } catch (e) { setError((e as Error).message); } finally { setBusy(false); } }
 if (!a) return null;
 return <section aria-label="Effective schedule"><h3>Linked meal schedule</h3><p>Start date: {state.anchorDate}. Additions {a.enabled ? 'enabled' : 'paused'} in {a.addition.timeZone}.</p><p>Next addition: {new Date(a.nextDueAt).toLocaleString(undefined, { timeZone: viewer.timeZone })} ({viewer.label}).</p>
 {state.latest && <p>Latest batch: {state.latest.status} · {state.latest.coverageStartDate} through {state.latest.coverageEndDateExclusive} (end excluded).</p>}{state.latest?.status === 'failed' && <><p role="alert">Ingredient generation failed. No partial batch was added.</p>{owner && <button disabled={disabled || busy} onClick={() => { void retry(); }}>Retry failed addition</button>}</>}
 {(error || live.error) && <p role="alert">{error || live.error}</p>}<label>List meal date<input type="date" value={date} onChange={event => setChosen(event.target.value)} /></label><button onClick={() => setChosen('')}>Today’s list meal</button>
 {live.data?.meals.map(meal => <p key={meal.date}>{meal.date} · {meal.status === 'meal' ? <a href={`/recipes/${meal.recipeId}`}>View scheduled recipe</a> : meal.status === 'rest' ? 'Rest day' : 'Not started'}</p>)}
 {live.data && <><h3>Next addition preview</h3>{live.data.preview.beyondHorizon ? <p>The next period is beyond your one-month viewing horizon.</p> : <><p>Full candidate period: {live.data.preview.coverageStartDate} through {live.data.preview.coverageEndDateExclusive} (end excluded).</p>{live.data.preview.previouslyCoveredEndDate && <p>Already covered through {live.data.preview.previouslyCoveredEndDate} (end excluded); those dates will not be added again.</p>}<p>{live.data.preview.isPartial ? 'Partial preview totals' : 'Preview totals'} · displayed {live.data.preview.displayedFrom} through {live.data.preview.displayedTo} (end excluded).</p><ul>{live.data.preview.plan?.ingredients.map((item,index) => <li key={`${item.ingredientId}-${index}`}>{item.name} · {item.quantity} {item.unit}</li>)}</ul></>}</>}
 </section>;
}
