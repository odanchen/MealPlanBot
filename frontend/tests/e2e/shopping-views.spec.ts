import { test, expect, type BrowserContext } from "@playwright/test";
async function mutate(
  context: BrowserContext,
  path: string,
  method: string,
  data?: unknown,
  version?: number,
) {
  const csrf = await (await context.request.get("/api/csrf")).json();
  return context.request.fetch(path.startsWith("/__test") ? `http://127.0.0.1:8000${path}` : path, {
    method,
    data,
    headers: {
      [csrf.headerName]: csrf.token,
      "Idempotency-Key": crypto.randomUUID(),
      ...(version === undefined ? {} : { "If-Match": `"${version}"` }),
    },
  });
}
test("recipe measurement changes preserve canonical quantities and count/package labels", async ({
  page,
}) => {
  const metric = await (
    await page.request.get(
      "/api/recipes/red-wine-beef-stew-with-mashed-potatoes?measurementSystem=metric",
    )
  ).json();
  await page.goto("/recipes/red-wine-beef-stew-with-mashed-potatoes");
  const beef = page.locator('[data-ingredient-id="boneless-beef"]');
  await expect(beef).toContainText("1.4 kg");
  await expect(page.locator("[data-ingredient-id]")).toHaveCount(metric.recipe.ingredients.length);
  await page.getByRole("combobox", { name: "Measurement system", exact: true }).selectOption("us");
  await expect(beef).toContainText("3.086 lb");
  await expect(beef).toHaveAttribute("data-canonical-quantity", "1.4");
  await expect(beef).toHaveAttribute("data-canonical-unit", "kg");
  await expect(page.locator('[data-ingredient-id="tomato-paste"]')).toContainText("tin (156 ml)");
  await expect(page.locator('[data-ingredient-id="onions"]')).toContainText("2 each");
  await expect(page.locator('[data-ingredient-id="dry-red-wine"]')).toContainText("US cup");
  const us = await (
    await page.request.get(
      "/api/recipes/red-wine-beef-stew-with-mashed-potatoes?measurementSystem=us",
    )
  ).json();
  expect(us.recipe.ingredients).toEqual(metric.recipe.ingredients);
  await page
    .getByRole("combobox", { name: "Measurement system", exact: true })
    .selectOption("metric");
  await expect(beef).toContainText("1.4 kg");
  await expect(page.locator("[data-ingredient-id]")).toHaveCount(metric.recipe.ingredients.length);
});
test("every sort and bought grouping view retains all quantities and states through live refresh", async ({
  browser,
}) => {
  const context = await browser.newContext({
    extraHTTPHeaders: { "X-Test-Principal": `views-${crypto.randomUUID()}` },
  });
  try {
    const created = await mutate(context, "/api/lists", "POST", { name: "View combinations" });
    expect(created.status()).toBe(201);
    const id = (await created.json()).id as string;
    const ids: string[] = [];
    for (const name of ["Zucchini", "apples", "Bananas"]) {
      const result = await mutate(context, `/api/lists/${id}/entries`, "POST", {
        name,
        quantity: "1",
        unit: "kg",
      });
      expect(result.status()).toBe(201);
      ids.push((await result.json()).id);
      await mutate(context, "/__test/advance?hours=1", "POST");
    }
    expect(
      (
        await mutate(context, `/api/lists/${id}/entries/${ids[1]}`, "PATCH", { bought: true }, 0)
      ).status(),
    ).toBe(200);
    const before = (await (await context.request.get(`/api/lists/${id}/entries`)).json()).entries;
    const page = await context.newPage();
    await page.goto(`/lists/${id}`);
    const rows = page.locator(".shopping-items > li");
    await expect(rows).toHaveCount(3);
    for (const sort of ["alphabetical", "chronological"])
      for (const group of [false, true]) {
        await page
          .getByRole("combobox", { name: "Sort purchases", exact: true })
          .selectOption(sort);
        const grouping = page.getByRole("checkbox", { name: "Separate bought items", exact: true });
        if ((await grouping.isChecked()) !== group) await grouping.click();
        await expect(page.getByRole("heading", { name: "Unbought", exact: true })).toHaveCount(
          group ? 1 : 0,
        );
        await expect(page.getByRole("heading", { name: "Bought", exact: true })).toHaveCount(
          group ? 1 : 0,
        );
        const expected =
          sort === "alphabetical"
            ? group
              ? ["Bananas", "Zucchini", "apples"]
              : ["apples", "Bananas", "Zucchini"]
            : group
              ? ["Zucchini", "Bananas", "apples"]
              : ["Zucchini", "apples", "Bananas"];
        await expect
          .poll(async () => rows.locator("label span").allTextContents())
          .toEqual(expected.map((name) => `${name} · 1 kg`));
        await expect(rows).toHaveCount(3);
        await expect(
          page.getByRole("checkbox", { name: "apples · 1 kg", exact: true }),
        ).toBeChecked();
        await page.getByRole("combobox", { name: "Sort purchases", exact: true }).focus();
        await page.evaluate(() => window.dispatchEvent(new Event("focus")));
        await expect(
          page.getByRole("combobox", { name: "Sort purchases", exact: true }),
        ).toBeFocused();
        await expect
          .poll(async () => rows.locator("label span").allTextContents())
          .toEqual(expected.map((name) => `${name} · 1 kg`));
        const checkbox = page.getByRole("checkbox", { name: "apples · 1 kg", exact: true });
        await checkbox.focus();
        await checkbox.press("Space");
        await expect(checkbox).not.toBeChecked();
        await expect(checkbox).toBeFocused();
        await expect(rows).toHaveCount(3);
        const current = (await (await context.request.get(`/api/lists/${id}/entries`)).json())
          .entries;
        const apple = current.find((entry: { id: string }) => entry.id === ids[1]);
        await mutate(
          context,
          `/api/lists/${id}/entries/${ids[1]}`,
          "PATCH",
          { bought: true },
          apple.version,
        );
        await expect(checkbox).toBeChecked({ timeout: 5000 });
        await expect(checkbox).toBeFocused();
      }
    const after = (await (await context.request.get(`/api/lists/${id}/entries`)).json()).entries;
    expect(after.map((entry: { version: number }) => ({ ...entry, version: 0 }))).toEqual(
      before.map((entry: { version: number }) => ({ ...entry, version: 0 })),
    );
    await expect(page.locator(".shopping-items time")).toHaveCount(0);
  } finally {
    await context.close();
  }
});

test("a member sees archive policy changes on an unattached list", async ({ browser }) => {
  const owner = await browser.newContext({
    extraHTTPHeaders: { "X-Test-Principal": `policy-owner-${crypto.randomUUID()}` },
  });
  const member = await browser.newContext({
    extraHTTPHeaders: { "X-Test-Principal": `policy-member-${crypto.randomUUID()}` },
  });
  try {
    const list = await (
      await mutate(owner, "/api/lists", "POST", { name: "Policy visibility" })
    ).json();
    const invitation = await (
      await mutate(owner, `/api/lists/${list.id}/invitations`, "POST")
    ).json();
    await mutate(member, "/api/invitations/accept", "POST", {
      token: invitation.link.split("token=")[1],
    });
    const page = await member.newPage();
    await page.goto(`/lists/${list.id}`);
    await expect(
      page.getByText("Items archive after 7 elapsed days from creation.", { exact: true }),
    ).toBeVisible();
    await expect(page.getByRole("button", { name: "Save archive policy" })).toHaveCount(0);
    expect(
      (
        await mutate(
          owner,
          `/api/lists/${list.id}/archive-policy`,
          "PUT",
          { ageLimit: { count: 2, unit: "week" } },
          list.version,
        )
      ).status(),
    ).toBe(200);
    await expect(
      page.getByText("Items archive after 14 elapsed days from creation.", { exact: true }),
    ).toBeVisible({ timeout: 5000 });
  } finally {
    await owner.close();
    await member.close();
  }
});
