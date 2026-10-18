import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "../api/client";
/** A single request at a time, with one trailing refresh for concurrent triggers. */
export function useLiveShoppingList<T>(load: (signal: AbortSignal) => Promise<T>) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState("");
  const [connected, setConnected] = useState(navigator.onLine);
  const [inaccessible, setInaccessible] = useState(false);
  const refreshRef = useRef<() => Promise<void>>(() => Promise.resolve());
  const refresh = useCallback(() => refreshRef.current(), []);
  useEffect(() => {
    let disposed = false,
      stopped = false,
      again = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let flight: Promise<void> | null = null;
    let controller: AbortController | null = null;
    const allowed = () => !disposed && !stopped && !document.hidden && navigator.onLine;
    function run(): Promise<void> {
      clearTimeout(timer);
      if (!allowed()) return Promise.resolve();
      if (flight) {
        again = true;
        return flight;
      }
      flight = (async () => {
        do {
          again = false;
          controller = new AbortController();
          try {
            const result = await load(
              AbortSignal.any([controller.signal, AbortSignal.timeout(8000)]),
            );
            if (!disposed && !controller.signal.aborted) {
              setData(result);
              setConnected(true);
              setError("");
            }
          } catch (e) {
            if (disposed || controller.signal.aborted) continue;
            if (e instanceof ApiError && (e.status === 401 || e.status === 404)) {
              stopped = true;
              controller.abort();
              setData(null);
              setInaccessible(true);
            }
            setConnected(false);
            setError((e as Error).message || "Cannot connect. Your view may be outdated.");
          }
        } while (again && allowed());
      })().finally(() => {
        flight = null;
        if (allowed())
          timer = setTimeout(() => {
            void run();
          }, 2000);
      });
      return flight;
    }
    function availability() {
      clearTimeout(timer);
      if (!navigator.onLine) {
        setConnected(false);
        setError("Disconnected. Reconnect to make changes.");
      }
      if (!allowed()) controller?.abort();
      else void run();
    }
    setInaccessible(false);
    refreshRef.current = run;
    void run();
    document.addEventListener("visibilitychange", availability);
    window.addEventListener("focus", availability);
    window.addEventListener("online", availability);
    window.addEventListener("offline", availability);
    return () => {
      disposed = true;
      clearTimeout(timer);
      controller?.abort();
      refreshRef.current = () => Promise.resolve();
      document.removeEventListener("visibilitychange", availability);
      window.removeEventListener("focus", availability);
      window.removeEventListener("online", availability);
      window.removeEventListener("offline", availability);
    };
  }, [load]);
  return { data, error, connected, inaccessible, refresh };
}
