import type { ShoppingEntry } from "../api/shopping";
export function ShoppingItemRow({
  entry,
  disabled,
  onBought,
  onRemove,
}: {
  entry: ShoppingEntry;
  disabled: boolean;
  onBought: (bought: boolean) => void;
  onRemove: () => void;
}) {
  return (
    <li className="shopping-row">
      <label className={entry.boughtAt ? "bought" : ""}>
        <input
          type="checkbox"
          checked={entry.boughtAt !== null}
          disabled={disabled}
          onChange={(event) => onBought(event.target.checked)}
        />{" "}
        <span>
          {entry.name}
          {entry.quantity && (
            <small>
              {" "}
              · {entry.quantity} {entry.unit}
            </small>
          )}
        </span>
      </label>
      <button disabled={disabled} aria-label={`Remove ${entry.name}`} onClick={onRemove}>
        Remove
      </button>
    </li>
  );
}
