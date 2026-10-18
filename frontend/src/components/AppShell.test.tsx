import { render, screen } from "@testing-library/react";
import { afterEach, expect, test, vi } from "vitest";
import axe from "axe-core";
import { AppShell } from "./AppShell";
import { SessionProvider } from "../auth/SessionProvider";
afterEach(() => vi.unstubAllGlobals());
test("public content is accessible anonymously and private content explains Google sign-in", async () => {
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockResolvedValue(
        new Response(JSON.stringify({ authenticated: false, authMode: "google" })),
      ),
  );
  const { rerender } = render(
    <SessionProvider>
      <AppShell>
        <h2>Public recipes</h2>
      </AppShell>
    </SessionProvider>,
  );
  expect(screen.getByRole("heading", { name: "Public recipes" })).toBeVisible();
  await screen.findByRole("link", { name: "Sign in with Google" });
  expect(
    (await axe.run(document.body, { rules: { "color-contrast": { enabled: false } } })).violations,
  ).toEqual([]);
  rerender(
    <SessionProvider>
      <AppShell protectedPage>
        <h2>Private schedules</h2>
      </AppShell>
    </SessionProvider>,
  );
  expect(screen.queryByRole("heading", { name: "Private schedules" })).not.toBeInTheDocument();
  expect(await screen.findByRole("heading", { name: "Sign in to continue" })).toBeVisible();
});
