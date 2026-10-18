import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./tests/e2e",
  // Controlled-clock acceptance journeys share one disposable backend.
  workers: 1,
  use: { baseURL: "http://127.0.0.1:4173" },
  webServer: [
    {
      command: "../scripts/run-browser-backend.sh",
      url: "http://127.0.0.1:8000/health",
      reuseExistingServer: false,
      timeout: 60_000,
    },
    {
      command: "npm run dev -- --port 4173 --strictPort",
      url: "http://127.0.0.1:4173",
      reuseExistingServer: false,
    },
  ],
});
