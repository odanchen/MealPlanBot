import { useEffect, useState } from "react";
export type ViewerTime = { timeZone: string; today: string; isFallback: boolean; label: string };
export function readViewerTime(now = new Date()): ViewerTime {
  let timeZone: string;
  let isFallback = false;
  try {
    timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
    if (!timeZone) throw new Error("Zone unavailable");
    new Intl.DateTimeFormat("en", { timeZone }).format(now);
  } catch {
    timeZone = "UTC";
    isFallback = true;
  }
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(now);
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((p) => p.type === type)!.value;
  return {
    timeZone,
    today: `${part("year")}-${part("month")}-${part("day")}`,
    isFallback,
    label: isFallback ? "UTC (browser time zone unavailable)" : timeZone,
  };
}
export function useViewerTimeZone(): ViewerTime {
  const [viewer, setViewer] = useState(readViewerTime);
  useEffect(() => {
    const refresh = () => {
      if (document.visibilityState === "hidden") return;
      const next = readViewerTime();
      setViewer((previous) =>
        previous.timeZone === next.timeZone &&
        previous.today === next.today &&
        previous.isFallback === next.isFallback
          ? previous
          : next,
      );
    };
    const interval = window.setInterval(refresh, 30_000);
    document.addEventListener("visibilitychange", refresh);
    window.addEventListener("focus", refresh);
    refresh();
    return () => {
      window.clearInterval(interval);
      document.removeEventListener("visibilitychange", refresh);
      window.removeEventListener("focus", refresh);
    };
  }, []);
  return viewer;
}
