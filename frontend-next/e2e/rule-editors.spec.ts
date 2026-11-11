import { test, expect } from "@playwright/test";
test("rule editors load locally, resize, switch theme and release on navigation", async ({
  page,
}) => {
  const errors: string[] = [],
    workerUrls: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("worker", (worker) => workerUrls.push(worker.url()));
  await page.goto("/dev/rule");
  await expect(page.locator(".monaco-editor")).toBeVisible();
  await expect(page.locator(".vue-flow")).toBeVisible();
  await page.getByRole("textbox", { name: "规则表达式" }).focus();
  await page.keyboard.press("ControlOrMeta+a");
  await page.keyboard.type("input.amount * 0.9");
  await page.setViewportSize({ width: 390, height: 844 });
  await page.evaluate(() => document.documentElement.classList.add("dark"));
  await expect(page.locator(".monaco-editor")).toBeVisible();
  await page.goto("/dev/components");
  expect(errors).toEqual([]);
  const origin = new URL(page.url()).origin;
  expect(
    workerUrls.every(
      (url) =>
        url.startsWith(origin + "/") || url.startsWith("blob:" + origin + "/"),
    ),
  ).toBe(true);
});
