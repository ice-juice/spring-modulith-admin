import { defineConfig } from "@playwright/test";
import base from "./playwright.config";
const port = Number(process.env.SMA_RULE_TEST_PORT || 5187);
process.env.SMA_RULE_TEST_PORT = String(port);
const url = `http://127.0.0.1:${port}`;
const production = process.env.SMA_RULE_PRODUCTION === "1";
export default defineConfig(base, {
  testMatch: production
    ? /rule-(authoring|instances|releases|flow|decisions|operations|visual)\.spec\.ts/
    : /rule-.*\.spec\.ts/,
  outputDir: production
    ? ".e2e-runtime/rule-production"
    : ".e2e-runtime/rule-results",
  reporter: [
    ["list"],
    [
      "json",
      {
        outputFile: production
          ? ".e2e-runtime/rule-production.json"
          : ".e2e-runtime/rule-results.json",
      },
    ],
  ],
  use: { baseURL: url },
  webServer: {
    command: `npm run ${production ? "preview" : "dev"} -- --port ${port}`,
    url,
    reuseExistingServer: false,
    timeout: 60000,
  },
});
