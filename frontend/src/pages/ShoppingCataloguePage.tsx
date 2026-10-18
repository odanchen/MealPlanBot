import { useLiveShoppingList } from "../hooks/useLiveShoppingList";
import { useCallback, useRef, useState } from "react";
import { shopping } from "../api/shopping";
import { ApiError, newIdempotencyKey } from "../api/client";
import { useSession } from "../auth/SessionProvider";
import { FormField } from "../components/FormField";
export function ShoppingCataloguePage() {
  const { refresh } = useSession();
  const load = useCallback((signal: AbortSignal) => shopping.catalogue(signal), []);
  const live = useLiveShoppingList(load);
  const lists = live.data?.lists ?? null;
  const [name, setName] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const pending = useRef<{ name: string; key: string } | null>(null);
  async function create() {
    if (busy || !live.connected || !navigator.onLine) return;
    setBusy(true);
    setError("");
    if (!pending.current || pending.current.name !== name)
      pending.current = { name, key: newIdempotencyKey() };
    try {
      const list = await shopping.create(name, pending.current.key);
      window.location.assign(`/lists/${list.id}`);
    } catch (e) {
      setError((e as Error).message);
      if (e instanceof ApiError && e.status === 401) await refresh();
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <h2>Your shopping lists</h2>
      <p>Create a private list for your purchases.</p>
      {(error || live.error) && <p role="alert">{error || live.error}</p>}
      {!live.connected && !live.inaccessible && (
        <p role="status">
          Disconnected or outdated. Reconnect to make changes.{" "}
          <button
            onClick={() => {
              void live.refresh();
            }}
          >
            Retry connection
          </button>
        </p>
      )}
      <form
        onSubmit={(event) => {
          event.preventDefault();
          void create();
        }}
      >
        <FormField label="List name">
          {(id) => (
            <input
              id={id}
              required
              maxLength={200}
              value={name}
              onChange={(event) => setName(event.target.value)}
            />
          )}
        </FormField>
        <button disabled={busy || !live.connected}>Create list</button>
      </form>
      {lists === null ? (
        !live.error && <p role="status">Loading lists…</p>
      ) : lists.length === 0 ? (
        <p>You have no shopping lists yet.</p>
      ) : (
        <ul>
          {lists.map((list) => (
            <li key={list.id}>
              <a href={`/lists/${list.id}`}>{list.name}</a>
              {list.owner && " · Owner"}
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
