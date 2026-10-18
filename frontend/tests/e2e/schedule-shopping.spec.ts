import { test, expect, type BrowserContext, type Page } from "@playwright/test";
async function api(
  context: BrowserContext,
  path: string,
  method = "POST",
  data?: unknown,
  version?: number,
) {
  const csrf = await (await context.request.get("/api/csrf")).json();
  return context.request.fetch(path.startsWith("/__test") ? `http://127.0.0.1:8000${path}` : path, {
    method,
    data,
    headers: {
      [csrf.headerName]: csrf.token,
      ...(version === undefined ? {} : { "If-Match": `"${version}"` }),
      "Idempotency-Key": crypto.randomUUID(),
    },
  });
}
async function createSource(context: BrowserContext, anchorDate: string) {
  const response = await api(context, "/api/user-schedules", "POST", {
    templateId: "three-week",
    anchorDate,
  });
  expect(response.status()).toBe(201);
  return (await response.json()).id as string;
}
async function createList(context: BrowserContext, name: string) {
  const response = await api(context, "/api/lists", "POST", { name });
  expect(response.status()).toBe(201);
  return (await response.json()).id as string;
}
async function state(context: BrowserContext, list: string) {
  return (await context.request.get(`/api/lists/${list}/schedule`)).json();
}
async function items(context: BrowserContext, list: string) {
  return (await (await context.request.get(`/api/lists/${list}/entries`)).json()).entries as {
    id: string;
    name: string;
    occurrenceId: string | null;
    createdAt: string;
  }[];
}
async function add(page: Page, name: string) {
  await page.getByLabel("Item name", { exact: true }).fill(name);
  await page.getByRole("button", { name: "Add item", exact: true }).click();
  await expect(page.getByRole("checkbox", { name, exact: true })).toBeVisible();
}
test("live import, repeat-safe generation, changed anchor, replacement and multi-list source deletion", async ({
  browser,
}) => {
  const context = await browser.newContext({
    extraHTTPHeaders: { "X-Test-Principal": `automation-${crypto.randomUUID()}` },
    timezoneId: "Pacific/Honolulu",
  });
  const guest = await browser.newContext({
    extraHTTPHeaders: { "X-Test-Principal": `automation-member-${crypto.randomUUID()}` },
    timezoneId: "Pacific/Kiritimati",
  });
  try {
    expect((await api(context, "/__test/clock?instant=2026-10-20T16:00:00Z")).status()).toBe(200);
    const source = await createSource(context, "2026-10-20"),
      list = await createList(context, "Live meal purchases"),
      page = await context.newPage();
    await page.goto(`/lists/${list}`);
    await expect(
      page.getByText("No meal schedule attached. Automatic additions are off."),
    ).toBeVisible();
    await page
      .getByRole("combobox", { name: "Personal schedule", exact: true })
      .selectOption(source);
    await page.getByLabel("Recurrence start date").fill("2026-10-20");
    await page.getByRole("button", { name: "Import schedule", exact: true }).click();
    await expect(page.getByRole("dialog")).toContainText("immediate initial batch");
    await page.getByRole("button", { name: "Confirm schedule", exact: true }).click();
    await expect(page.getByText(/Latest batch: completed/)).toBeVisible();
    const first = await state(context, list);
    expect(first.attachment.lastCoveredEndDateExclusive).toBe("2026-10-24");
    const original = await items(context, list);
    expect(original.length).toBeGreaterThan(0);
    await page.getByLabel("List meal date").fill("2026-10-19");
    await expect(page.getByText("2026-10-19 · Not started")).toBeVisible();
    const invitation = await api(context, `/api/lists/${list}/invitations`);
    const link = (await invitation.json()).link as string;
    expect(
      (
        await api(guest, "/api/invitations/accept", "POST", { token: link.split("token=")[1] })
      ).status(),
    ).toBe(200);
    const other = await guest.newPage();
    await other.goto(`/lists/${list}`);
    await other.getByLabel("List meal date").fill("2026-10-20");
    await page.getByLabel("List meal date").fill("2026-10-20");
    await expect(other.getByRole("link", { name: "View scheduled recipe" })).toHaveAttribute(
      "href",
      (await page.getByRole("link", { name: "View scheduled recipe" }).getAttribute("href")) ?? "",
    );
    expect((await guest.request.get(`/api/user-schedules/${source}`)).status()).toBe(404);
    expect((await api(context, "/__test/clock?instant=2026-10-24T13:00:00Z")).status()).toBe(200);
    expect((await api(context, "/__test/run-due")).status()).toBe(200);
    const once = (await items(context, list)).length;
    expect(once).toBeGreaterThan(original.length);
    await api(context, "/__test/run-due");
    expect((await items(context, list)).length).toBe(once);
    expect(
      (
        await api(
          context,
          `/api/user-schedules/${source}`,
          "PATCH",
          { anchorDate: "2026-10-21" },
          0,
        )
      ).status(),
    ).toBe(200);
    await expect
      .poll(async () => (await state(context, list)).attachment.lastCoveredEndDateExclusive)
      .toBeNull();
    await expect(other.getByText(/Start date: 2026-10-21/)).toBeVisible({ timeout: 5000 });
    expect((await items(context, list)).length).toBe(once);
    const secondList = await createList(context, "Second linked household");
    expect(
      (
        await api(
          context,
          `/api/lists/${secondList}/schedule`,
          "PUT",
          { userScheduleId: source },
          0,
        )
      ).status(),
    ).toBe(200);
    const replacement = await createSource(context, "2026-10-22");
    await page
      .getByRole("combobox", { name: "Personal schedule", exact: true })
      .selectOption(source);
    await page.getByRole("button", { name: "Save addition settings", exact: true }).click();
    await page.getByRole("button", { name: "Confirm schedule", exact: true }).click();
    await page.goto(`/lists/${list}`);
    await page
      .getByRole("combobox", { name: "Personal schedule", exact: true })
      .selectOption(replacement);
    await page.getByRole("button", { name: "Save addition settings", exact: true }).click();
    await page.getByRole("button", { name: "Confirm schedule", exact: true }).click();
    await expect(page.getByText(/Start date: 2026-10-22/)).toBeVisible();
    const version = (await (await context.request.get(`/api/lists/${list}`)).json()).version;
    expect(
      (
        await api(
          context,
          `/api/lists/${list}/schedule`,
          "PUT",
          { userScheduleId: source },
          version,
        )
      ).status(),
    ).toBe(200);
    await page.goto(`/schedules/${source}`);
    await page.getByRole("button", { name: "Delete personal schedule", exact: true }).click();
    await expect(page.getByRole("dialog")).toContainText("Live meal purchases");
    await expect(page.getByRole("dialog")).toContainText("Second linked household");
    await page.getByRole("button", { name: "Delete schedule", exact: true }).click();
    await expect(page).toHaveURL("/schedules");
    expect((await state(context, list)).attachment).toBeNull();
    expect((await state(context, secondList)).attachment).toBeNull();
    expect((await items(context, list)).length).toBeGreaterThan(0);
    await api(context, "/__test/run-due");
    expect((await state(context, list)).attachment).toBeNull();
  } finally {
    await context.close();
    await guest.close();
  }
});
test("failed initial batch retries once, bounded preview and independent item expiry", async ({
  browser,
}) => {
  const context = await browser.newContext({
    extraHTTPHeaders: { "X-Test-Principal": `failure-age-${crypto.randomUUID()}` },
  });
  try {
    await api(context, "/__test/clock?instant=2026-10-20T16:00:00Z");
    const source = await createSource(context, "2026-10-20"),
      list = await createList(context, "Retry and expiry");
    await api(context, `/__test/sources/${source}/template?templateId=missing-template`);
    const failed = await api(
      context,
      `/api/lists/${list}/schedule`,
      "PUT",
      { userScheduleId: source },
      0,
    );
    expect(failed.status()).toBe(200);
    expect((await failed.json()).latest.status).toBe("failed");
    expect(await items(context, list)).toHaveLength(0);
    await api(context, `/__test/sources/${source}/template?templateId=three-week`);
    const page = await context.newPage();
    await page.goto(`/lists/${list}`);
    await page.getByRole("button", { name: "Retry failed addition", exact: true }).click();
    await expect(page.getByText(/Latest batch: completed/)).toBeVisible();
    expect((await items(context, list)).length).toBeGreaterThan(0);
    const detail = await (await context.request.get(`/api/lists/${list}`)).json();
    const addition = {
      unit: "week",
      count: 8,
      weekday: 6,
      localTime: "09:00",
      timeZone: "America/Toronto",
      startDate: "2026-10-20",
    };
    expect(
      (
        await api(
          context,
          `/api/lists/${list}/schedule`,
          "PUT",
          { userScheduleId: source, addition },
          detail.version,
        )
      ).status(),
    ).toBe(200);
    const preview = await (
      await context.request.get(`/api/lists/${list}/schedule/preview?timeZone=UTC`)
    ).json();
    expect(preview.isPartial).toBe(true);
    expect(preview.displayedTo).not.toBe(preview.coverageEndDateExclusive);
    expect(
      (
        await context.request.get(
          `/api/lists/${list}/schedule/meals?from=2026-10-20&to=2026-12-20&timeZone=UTC`,
        )
      ).status(),
    ).toBe(422);
    await page.reload();
    await expect(page.getByText(/Partial preview totals/)).toBeVisible();
    await add(page, "Older manual need");
    await api(context, "/__test/advance?hours=48");
    await add(page, "Newer manual need");
    await api(context, "/__test/advance?hours=120");
    await page.reload();
    await expect(
      page.getByRole("checkbox", { name: "Older manual need", exact: true }),
    ).toHaveCount(0);
    await expect(
      page.getByRole("checkbox", { name: "Newer manual need", exact: true }),
    ).toBeVisible();
    await page.getByLabel("Item age limit", { exact: true }).fill("14");
    await page.getByRole("button", { name: "Save archive policy", exact: true }).click();
    await page.getByRole("link", { name: "View archive", exact: true }).click();
    await expect(
      page.getByRole("button", { name: "Clear Older manual need", exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Clear Newer manual need", exact: true }),
    ).toHaveCount(0);
  } finally {
    await context.close();
  }
});
