import { useEffect, useRef, useState } from "react";
import { listSchedules, type Addition, type ScheduleState } from "../api/listSchedules";
import { schedules, type UserSchedule } from "../api/schedules";
import { newIdempotencyKey } from "../api/client";
import type { ShoppingList } from "../api/shopping";
import { ConfirmDialog } from "./ConfirmDialog";
export function ListScheduleSettings({
  list,
  state,
  disabled,
  onSaved,
}: {
  list: ShoppingList;
  state: ScheduleState;
  disabled: boolean;
  onSaved: () => Promise<void>;
}) {
  const [sources, setSources] = useState<UserSchedule[]>([]);
  const [source, setSource] = useState(state.attachment?.sourceScheduleId || "");
  const [addition, setAddition] = useState<Addition>(
    () =>
      state.attachment?.addition || {
        unit: "week",
        count: 1,
        weekday: 6,
        localTime: "09:00",
        timeZone: "America/Toronto",
        startDate: new Intl.DateTimeFormat("en-CA", {
          timeZone: "America/Toronto",
          year: "numeric",
          month: "2-digit",
          day: "2-digit",
        }).format(new Date()),
      },
  );
  const [enabled, setEnabled] = useState(state.attachment?.enabled ?? true);
  const [confirmation, setConfirmation] = useState<"save" | "detach" | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const pending = useRef<{ payload: string; key: string } | null>(null);
  useEffect(() => {
    let active = true;
    void schedules
      .list()
      .then((result) => {
        if (active) {
          setSources(result.schedules);
          setSource((current) => current || result.schedules[0]?.id || "");
        }
      })
      .catch((e: Error) => {
        if (active) setError(e.message);
      });
    return () => {
      active = false;
    };
  }, []);
  async function save() {
    if (busy || disabled || !navigator.onLine) return;
    setBusy(true);
    setError("");
    try {
      if (confirmation === "detach") await listSchedules.detach(list);
      else {
        const input = { userScheduleId: source, addition, enabled };
        const payload = JSON.stringify(input);
        if (!pending.current || pending.current.payload !== payload)
          pending.current = { payload, key: newIdempotencyKey() };
        await listSchedules.configure(list, input, pending.current.key);
        pending.current = null;
      }
      setConfirmation(null);
      await onSaved();
    } catch (e) {
      setError((e as Error).message);
      await onSaved();
    } finally {
      setBusy(false);
    }
  }
  return (
    <section aria-label="Schedule settings">
      <h3>Automatic ingredient additions</h3>
      {error && !confirmation && <p role="alert">{error}</p>}
      {sources.length === 0 ? (
        <p>
          <a href="/schedules">Create a personal schedule</a> before importing it here.
        </p>
      ) : (
        <form
          onSubmit={(event) => {
            event.preventDefault();
            setConfirmation("save");
          }}
        >
          <label>
            Personal schedule
            <select value={source} onChange={(event) => setSource(event.target.value)}>
              {sources.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.templateId} · starts {item.anchorDate}
                </option>
              ))}
            </select>
          </label>
          <p>
            This is a live link. Changes to your personal start date affect future additions. An
            import immediately adds meals up to the next addition date; replacing a link keeps
            existing purchases.
          </p>
          <label>
            Repeat every
            <input
              type="number"
              min="1"
              required
              value={addition.count}
              onChange={(event) => setAddition({ ...addition, count: Number(event.target.value) })}
            />
          </label>
          <label>
            Repeat unit
            <select
              value={addition.unit}
              onChange={(event) =>
                setAddition({
                  ...addition,
                  unit: event.target.value as "day" | "week",
                  weekday: event.target.value === "week" ? 6 : null,
                })
              }
            >
              <option value="day">Days</option>
              <option value="week">Weeks</option>
            </select>
          </label>
          {addition.unit === "week" && (
            <label>
              Addition weekday
              <select
                value={addition.weekday ?? 6}
                onChange={(event) =>
                  setAddition({ ...addition, weekday: Number(event.target.value) })
                }
              >
                {["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"].map(
                  (day, index) => (
                    <option key={day} value={index + 1}>
                      {day}
                    </option>
                  ),
                )}
              </select>
            </label>
          )}
          <label>
            Addition time
            <input
              type="time"
              required
              value={addition.localTime.slice(0, 5)}
              onChange={(event) => setAddition({ ...addition, localTime: event.target.value })}
            />
          </label>
          <label>
            Addition time zone
            <input
              required
              value={addition.timeZone}
              onChange={(event) => setAddition({ ...addition, timeZone: event.target.value })}
            />
          </label>
          <label>
            Recurrence start date
            <input
              type="date"
              required
              value={addition.startDate}
              onChange={(event) => setAddition({ ...addition, startDate: event.target.value })}
            />
          </label>
          <label>
            <input
              type="checkbox"
              checked={enabled}
              onChange={(event) => setEnabled(event.target.checked)}
            />{" "}
            Enable future additions
          </label>
          <button disabled={disabled || busy}>
            {state.attachment ? "Save addition settings" : "Import schedule"}
          </button>
        </form>
      )}
      {state.attachment && (
        <button disabled={disabled || busy} onClick={() => setConfirmation("detach")}>
          Detach schedule
        </button>
      )}
      {confirmation && (
        <ConfirmDialog
          title={
            confirmation === "detach"
              ? "Detach schedule?"
              : state.attachment
                ? "Update schedule link?"
                : "Import live schedule?"
          }
          description={
            confirmation === "detach"
              ? "Future additions will stop. Existing purchases and their origin will remain."
              : "A new or replacement link creates one immediate initial batch. Changing addition settings keeps previous coverage and creates no extra initial batch."
          }
          confirmLabel={confirmation === "detach" ? "Detach" : "Confirm schedule"}
          busy={busy}
          error={error}
          onCancel={() => setConfirmation(null)}
          onConfirm={() => {
            void save();
          }}
        />
      )}
    </section>
  );
}
