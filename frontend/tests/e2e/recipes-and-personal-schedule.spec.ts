import { test, expect } from "@playwright/test";
test("public tutorials and private personal schedule work against the local SQLite backend", async ({
  page,
}) => {
  expect((await page.request.get("/api/user-schedules")).status()).toBe(401);
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Recipes for everyday cooking" })).toBeVisible();
  const entries = page.locator(".recipe-grid a");
  await expect(entries).toHaveCount(16);
  const catalogue = await entries.evaluateAll((links) =>
    links.map((link) => ({ url: link.getAttribute("href")!, title: link.textContent! })),
  );
  for (const recipe of catalogue) {
    await page.goto(recipe.url);
    await expect(page.getByRole("heading", { name: recipe.title, exact: true })).toBeVisible();
    await expect(page.locator(".steps li").first()).toBeVisible();
    await expect(page.locator("img")).toHaveCount(0);
  }
  await page.setViewportSize({ width: 320, height: 720 });
  await page.screenshot({ path: "test-results/us1-recipe-mobile.png", fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.goto(catalogue.filter((recipe) => !recipe.url.endsWith("/simple-rice")).at(-1)!.url);
  await page.getByRole("link", { name: "Next recipe →" }).click();
  await expect(page.getByRole("heading", { name: catalogue[0].title, exact: true })).toBeVisible();
  await page.goto("/schedules");
  await expect(page.getByRole("heading", { name: "Your planner" })).toBeVisible();
  await page.getByLabel("Start date", { exact: true }).fill("2026-01-07");
  await page.getByRole("button", { name: "Create schedule", exact: true }).click();
  await expect(page).toHaveURL(/\/schedules\/[0-9a-f-]+/);
  const personalUrl = page.url();
  await page.getByLabel("Meal date").fill("2026-01-06");
  await expect(page.getByText("Not started — your first meal is on 2026-01-07.")).toBeVisible();
  await page.getByLabel("Meal date").fill("2026-01-07");
  await expect(page.getByRole("link", { name: "View recipe", exact: true })).toHaveAttribute(
    "href",
    catalogue[0].url,
  );
  await page.getByLabel("Meal date").fill("2026-01-12");
  await expect(page.getByText("Rest day · cycle day 6")).toBeVisible();
  await page.getByLabel("Meal date").fill("2999-01-01");
  await expect(page.getByRole("alert")).toContainText("one calendar month ahead");
  await page.getByLabel("Start date", { exact: true }).fill("2026-01-08");
  const saved = page.waitForResponse(
    (response) =>
      response.url().includes("/api/user-schedules/") && response.request().method() === "PATCH",
  );
  await page.getByRole("button", { name: "Save start date" }).click();
  expect((await saved).status()).toBe(200);
  await expect(page.getByLabel("Start date", { exact: true })).toHaveValue("2026-01-08");
  await page.reload();
  await page.getByLabel("Meal date").fill("2026-01-08");
  await expect(page.getByRole("link", { name: "View recipe", exact: true })).toHaveAttribute(
    "href",
    catalogue[0].url,
  );
  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Sign in to continue" })).toBeVisible();
  expect(
    (await page.request.get("/api/user-schedules/" + personalUrl.split("/").pop())).status(),
  ).toBe(401);
});
