import { useId } from "react";
export function MeasurementSystemSelector({
  value,
  onChange,
}: {
  value: "metric" | "us";
  onChange: (value: "metric" | "us") => void;
}) {
  const id = useId();
  return (
    <>
      <label htmlFor={id}>Measurement system</label>
      <select
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value as "metric" | "us")}
      >
        <option value="metric">Metric</option>
        <option value="us">US customary</option>
      </select>
      <p>
        US cups, tablespoons, teaspoons and fluid ounces use US measures; oz is weight. Counts and
        package sizes stay unchanged.
      </p>
    </>
  );
}
