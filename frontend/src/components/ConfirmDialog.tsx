import { useEffect, useId, useRef } from "react";
export function ConfirmDialog({
  title,
  description,
  busy,
  error,
  confirmLabel = "Remove item",
  onConfirm,
  onCancel,
}: {
  title: string;
  description: string;
  busy: boolean;
  error?: string;
  confirmLabel?: string;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const descriptionId = useId();
  useEffect(() => {
    const prior = document.activeElement;
    const dialog = ref.current!;
    dialog.showModal();
    return () => {
      const restore =
        dialog.contains(document.activeElement) || document.activeElement === document.body;
      dialog.close();
      if (restore && prior instanceof HTMLElement && prior.isConnected) prior.focus();
    };
  }, []);
  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      aria-describedby={descriptionId}
      onCancel={(event) => {
        event.preventDefault();
        if (!busy) onCancel();
      }}
    >
      <h2 id={titleId}>{title}</h2>
      <p id={descriptionId}>{description}</p>
      {error && <p role="alert">{error}</p>}
      <div className="actions">
        <button autoFocus disabled={busy} onClick={onCancel}>
          Cancel
        </button>
        <button disabled={busy} onClick={onConfirm}>
          {confirmLabel}
        </button>
      </div>
    </dialog>
  );
}
