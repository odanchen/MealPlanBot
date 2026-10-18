import { useState } from "react";
import { shopping, type ShoppingList } from "../api/shopping";
import { ConfirmDialog } from "./ConfirmDialog";
export function ListMembershipActions({
  list,
  disabled,
}: {
  list: ShoppingList;
  disabled: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const label = list.owner ? "Delete list" : "Leave list";
  async function confirm() {
    if (busy || disabled || !navigator.onLine) return;
    setBusy(true);
    setError("");
    try {
      if (list.owner) await shopping.delete(list);
      else await shopping.leave(list.id);
      window.location.assign("/lists");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section aria-label="Membership">
      <button disabled={disabled} onClick={() => setOpen(true)}>
        {label}
      </button>
      {open && (
        <ConfirmDialog
          title={`${label}?`}
          description={
            list.owner
              ? "This permanently deletes the list and its purchases for everyone. All invitation links will stop working."
              : "You will lose access to this list. Your contributions will remain for the other members."
          }
          confirmLabel={label}
          busy={busy}
          error={error}
          onCancel={() => setOpen(false)}
          onConfirm={() => {
            void confirm();
          }}
        />
      )}
    </section>
  );
}
