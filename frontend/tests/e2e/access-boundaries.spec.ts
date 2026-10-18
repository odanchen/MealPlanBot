import { test, expect } from "@playwright/test";

test("anonymous private routes deny access and invitation fragments stay out of requests", async ({
  browser,
}) => {
  const context = await browser.newContext();
  try {
    for (const path of ["/api/lists", "/api/user-schedules", "/api/schedule-templates"]) {
      const response = await context.request.get(path);
      expect(response.status()).toBe(401);
      expect(response.headers()["cache-control"]).toBe("no-store");
    }
    const page = await context.newPage();
    const token = "boundary-secret-" + crypto.randomUUID();
    const urls: string[] = [];
    page.on("request", (request) => urls.push(request.url()));
    await page.goto(`/join#token=${token}`);
    await expect(page).toHaveURL(/\/join$/);
    await expect(page.getByRole("button", { name: "Accept invitation", exact: true })).toHaveCount(
      0,
    );
    expect(urls.every((url) => !url.includes(token))).toBe(true);
    expect(await page.evaluate(() => sessionStorage.getItem("mealplan.pending-invitation"))).toBe(
      token,
    );
    expect(
      (await context.request.post("/api/lists", { data: { name: "Forbidden" } })).status(),
    ).toBe(403);
  } finally {
    await context.close();
  }
});
