import { afterEach, describe, expect, it, vi } from "vitest";
import { datedCacheKey, datedPath, request } from "./client";
afterEach(() => vi.unstubAllGlobals());
describe("same-origin client", () => {
  it("sends current CSRF, version and retry identity without retrying writes", async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ headerName: "X-CSRF-TOKEN", token: "csrf-token" })),
      )
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetcher);
    await request("/api/lists/uuid", {
      method: "PATCH",
      body: { name: "Groceries" },
      version: 4,
      idempotencyKey: "request-uuid",
    });
    expect(fetcher).toHaveBeenCalledTimes(2);
    expect(fetcher.mock.calls[1][1]).toMatchObject({
      credentials: "same-origin",
      cache: "no-store",
      redirect: "error",
      headers: {
        "X-CSRF-TOKEN": "csrf-token",
        "If-Match": '"4"',
        "Idempotency-Key": "request-uuid",
      },
    });
  });
  it("rejects foreign origins and preserves server conflict details", async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValue(
        new Response(
          JSON.stringify({ code: "version_conflict", message: "Refresh", currentVersion: 8 }),
          { status: 409 },
        ),
      );
    vi.stubGlobal("fetch", fetcher);
    await expect(request("https://evil.test/api/lists")).rejects.toThrow("same-origin");
    expect(fetcher).not.toHaveBeenCalled();
    await expect(request("/api/lists/uuid")).rejects.toMatchObject({
      status: 409,
      details: { currentVersion: 8 },
    });
  });
  it("separates viewer zone and current date in dated queries/cache keys", () => {
    const first = { timeZone: "America/Toronto", today: "2026-10-18" };
    const second = { timeZone: "Asia/Tokyo", today: "2026-10-19" };
    expect(
      datedPath("/api/user-schedules/uuid/meals", first, "2026-10-18", "2026-10-19"),
    ).toContain("timeZone=America%2FToronto");
    expect(datedCacheKey("schedule", first, "a", "b")).not.toBe(
      datedCacheKey("schedule", second, "a", "b"),
    );
  });
});
