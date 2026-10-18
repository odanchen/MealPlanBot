import { useId } from "react";
export function ShoppingViewControls({
  sort,
  groupBought,
  onSort,
  onGroup,
}: {
  sort: "alphabetical" | "chronological";
  groupBought: boolean;
  onSort: (value: "alphabetical" | "chronological") => void;
  onGroup: (value: boolean) => void;
}) {
  const id = useId();
  return (
    <section aria-label="Shopping view">
      <label htmlFor={id}>Sort purchases</label>
      <select
        id={id}
        value={sort}
        onChange={(event) => onSort(event.target.value as "alphabetical" | "chronological")}
      >
        <option value="alphabetical">Alphabetical</option>
        <option value="chronological">Oldest first</option>
      </select>
      <label>
        <input
          type="checkbox"
          checked={groupBought}
          onChange={(event) => onGroup(event.target.checked)}
        />{" "}
        Separate bought items
      </label>
    </section>
  );
}
