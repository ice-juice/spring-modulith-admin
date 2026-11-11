import { test, expect } from "@playwright/test";
import { ruleFixture } from "./rule-fixture";

test("flow component library and structured condition inspector fit desktop and English dark mobile", async ({
  page,
}, info) => {
  const fixture = await ruleFixture(page);
  fixture.doc.definition = {
    kind: "FLOW",
    flow: {
      startNode: "start",
      nodes: [
        { id: "start", kind: "START", next: "branch" },
        {
          id: "branch",
          kind: "CONDITION",
          condition: {
            id: "check",
            operator: "GE",
            left: { source: "input", path: "amount" },
            right: { source: "literal", value: 100 },
          },
          onTrue: "result",
          onFalse: "result",
        },
        { id: "result", kind: "RESULT", expression: "input.amount" },
      ],
    },
  } as unknown as typeof fixture.doc.definition;
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.setViewportSize({ width: 1680, height: 1050 });
  await page.goto("/rule/templates/" + fixture.template.id);
  await page
    .getByRole("combobox", { name: "节点", exact: true })
    .selectOption("branch");
  await expect(page.locator(".vue-flow")).toBeVisible();
  await page.screenshot({
    path: info.outputPath("visual-flow-desktop-light.png"),
    animations: "disabled",
    fullPage: true,
  });
  fixture.host.preferences.locale = "en-US";
  fixture.host.preferences.theme = "dark";
  await page.setViewportSize({ width: 390, height: 844 });
  await page.reload();
  await page
    .getByRole("combobox", { name: "Node", exact: true })
    .selectOption("branch");
  await expect(
    page.getByRole("combobox", { name: "Condition mode", exact: true }),
  ).toHaveValue("structured");
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth),
  ).toBeLessThanOrEqual(390);
  await page.screenshot({
    path: info.outputPath("visual-flow-mobile-dark.png"),
    animations: "disabled",
    fullPage: true,
  });
  expect(errors).toEqual([]);
});

test("editing aids fit dark English mobile and remain keyboard reachable", async ({
  page,
}, info) => {
  const fixture = await ruleFixture(page);
  fixture.host.preferences.locale = "en-US";
  fixture.host.preferences.theme = "dark";
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/rule/templates/" + fixture.template.id);
  await page.getByText("Field and function reference", { exact: true }).focus();
  await page.keyboard.press("Enter");
  await expect(
    page
      .getByTestId("rule-references")
      .getByText("input.amount", { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Undo", exact: true }),
  ).toBeDisabled();
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth),
  ).toBeLessThanOrEqual(390);
  await page.screenshot({
    path: info.outputPath("rule-editing-aids-dark-mobile.png"),
    animations: "disabled",
    fullPage: true,
  });
  await page.goto("/rule/scenes");
  await page.getByRole("button", { name: "New scene", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog
    .getByRole("combobox", { name: "Sample scene", exact: true })
    .selectOption("ratio");
  await expect(dialog.getByLabel("Name", { exact: true })).toHaveValue(
    "Percentage discount (expression)",
  );
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth),
  ).toBeLessThanOrEqual(390);
  await page.screenshot({
    path: info.outputPath("rule-sample-scene-dark-mobile.png"),
    animations: "disabled",
  });
});
test("compact rule workspaces support light desktop and dark English mobile", async ({
  page,
}, info) => {
  const fixture = await ruleFixture(page),
    errors: string[] = [];
  page.on("pageerror", (err) => errors.push(err.message));
  page.on("console", (msg) => {
    if (msg.text().includes("[intlify]")) errors.push(msg.text());
  });
  await page.setViewportSize({ width: 1440, height: 960 });
  await page.goto("/rule/templates/" + fixture.template.id);
  await expect(
    page.getByRole("heading", { name: "会员折扣", exact: true }),
  ).toBeVisible();
  await expect(page.locator(".monaco-editor")).toBeVisible();
  expect(
    (await page.locator(".page-heading").boundingBox())?.height,
  ).toBeLessThan(70);
  await page.screenshot({
    path: info.outputPath("rule-template-light-desktop.png"),
    animations: "disabled",
  });
  fixture.host.preferences.locale = "en-US";
  fixture.host.preferences.theme = "dark";
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/rule/catalog");
  await expect(
    page.getByRole("heading", { name: "Template catalog", exact: true }),
  ).toBeVisible();
  await expect(page.locator("html")).toHaveClass(/dark/);
  await page
    .getByRole("combobox", { name: "Scene", exact: true })
    .selectOption(fixture.scene.id);
  await expect(
    page.getByRole("button", { name: "Use this version", exact: true }),
  ).toBeVisible();
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth),
  ).toBeLessThanOrEqual(390);
  await page.screenshot({
    path: info.outputPath("rule-catalog-dark-mobile.png"),
    animations: "disabled",
  });
  expect(errors).toEqual([]);
});
