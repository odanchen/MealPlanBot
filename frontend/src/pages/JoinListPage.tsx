import { useState } from "react";
import { invitations, pendingInvitationKey } from "../api/invitations";
export function JoinListPage() {
  const [token] = useState(() => sessionStorage.getItem(pendingInvitationKey));
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  async function accept() {
    if (!token || busy || !navigator.onLine) return;
    setBusy(true);
    setError("");
    try {
      const result = await invitations.accept(token);
      sessionStorage.removeItem(pendingInvitationKey);
      window.location.assign(`/lists/${result.listId}`);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <h2>Join a shopping list</h2>
      <p>
        Accept this invitation to share purchases with the household. List contents are available
        after you join.
      </p>
      {error && <p role="alert">{error}</p>}
      {token ? (
        <button
          disabled={busy}
          onClick={() => {
            void accept();
          }}
        >
          Accept invitation
        </button>
      ) : (
        <p>No invitation is pending. Ask the owner for a new link.</p>
      )}
    </>
  );
}
