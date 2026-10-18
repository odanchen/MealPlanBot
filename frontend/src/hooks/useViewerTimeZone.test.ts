import { act, renderHook } from "@testing-library/react";
import { afterEach, expect, test, vi } from "vitest";
import { readViewerTime, useViewerTimeZone } from "./useViewerTimeZone";
afterEach(() => {
  vi.restoreAllMocks();
  vi.useRealTimers();
});
test("labels UTC fallback when browser zone is unavailable", () => {
  const original = Intl.DateTimeFormat.prototype.resolvedOptions;
  vi.spyOn(Intl.DateTimeFormat.prototype, "resolvedOptions").mockImplementation(function (
    this: Intl.DateTimeFormat,
  ) {
    return { ...original.call(this), timeZone: "" };
  });
  expect(readViewerTime(new Date("2026-10-18T23:59:59Z"))).toMatchObject({
    timeZone: "UTC",
    today: "2026-10-18",
    isFallback: true,
    label: "UTC (browser time zone unavailable)",
  });
});
test("refreshes zone on visibility restoration", () => {
  vi.spyOn(document, "visibilityState", "get").mockReturnValue("visible");
  const original = Intl.DateTimeFormat.prototype.resolvedOptions;
  let zone = "America/Toronto";
  vi.spyOn(Intl.DateTimeFormat.prototype, "resolvedOptions").mockImplementation(function (
    this: Intl.DateTimeFormat,
  ) {
    return { ...original.call(this), timeZone: zone };
  });
  const { result } = renderHook(useViewerTimeZone);
  expect(result.current.timeZone).toBe("America/Toronto");
  act(() => {
    zone = "Asia/Tokyo";
    document.dispatchEvent(new Event("visibilitychange"));
  });
  expect(result.current.timeZone).toBe("Asia/Tokyo");
});
test("refreshes local date after midnight while visible", () => {
  vi.spyOn(document, "visibilityState", "get").mockReturnValue("visible");
  vi.useFakeTimers();
  vi.setSystemTime(new Date("2026-10-18T12:00:00Z"));
  const { result } = renderHook(useViewerTimeZone);
  expect(result.current.today).toBe("2026-10-18");
  act(() => {
    vi.advanceTimersByTime(24 * 60 * 60 * 1000);
  });
  expect(result.current.today).toBe("2026-10-19");
});
