import { test, expect } from "@playwright/test";
import { ruleFixture } from "./rule-fixture";
test("operators choose a named pinned template and configure exact parameters without loading editors", async ({
  page,
}) => {
  const fixture = await ruleFixture(page),
    requests: string[] = [];
  page.on("request", (request) => requests.push(request.url()));
  await page.goto("/rule/catalog");
  await page
    .getByRole("combobox", { name: "所属场景", exact: true })
    .selectOption(fixture.scene.id);
  await expect(page.getByRole("heading", { name: "会员折扣" })).toBeVisible();
  await page.getByRole("button", { name: "选用此版本" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("编码", { exact: true }).fill("operator_discount");
  await dialog.getByLabel("名称", { exact: true }).fill("运营折扣实例");
  await dialog.getByLabel("业务对象类型", { exact: true }).fill("PRODUCT");
  await dialog
    .getByLabel("业务对象标识", { exact: true })
    .fill("9007199254740993123");
  await dialog.getByLabel("折后比例", { exact: false }).fill("0.9001");
  await dialog.getByRole("button", { name: "保存草稿", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  const write = fixture.writes.find((row) => row.path === "/instances");
  expect(write?.body.templateVersionId).toBe(fixture.version.id);
  expect(write?.body.objectId).toBe("9007199254740993123");
  expect(write?.raw).toContain('"ratio":0.9001');
  expect(
    requests.some((url) => /monaco|editor\.worker|FlowCanvas/.test(url)),
  ).toBe(false);
  await page.goto("/rule/instances");
  await expect(page.getByRole("cell", { name: "运营折扣实例" })).toBeVisible();
  expect(fixture.writes.some((row) => row.path.endsWith("/publish"))).toBe(
    false,
  );
});
test("business binding rejection preserves instance form and blocks no independent work", async ({
  page,
}) => {
  const fixture = await ruleFixture(page);
  await page.route("**/api/rule/instances", (route) =>
    route.request().method() === "POST"
      ? route.fulfill({
          status: 403,
          json: { code: 403, message: "业务对象无权绑定", data: null },
        })
      : route.fallback(),
  );
  await page.goto("/rule/catalog");
  await page
    .getByRole("combobox", { name: "所属场景", exact: true })
    .selectOption(fixture.scene.id);
  await page.getByRole("button", { name: "选用此版本" }).click();
  const dialog = page.getByRole("dialog");
  for (const [label, value] of [
    ["编码", "denied_instance"],
    ["名称", "被拒绝实例"],
    ["业务对象类型", "PRODUCT"],
    ["业务对象标识", "private_product"],
    ["折后比例", "0.9"],
  ])
    await dialog
      .getByLabel(label!, { exact: label !== "折后比例" })
      .fill(value!);
  await dialog.getByRole("button", { name: "保存草稿", exact: true }).click();
  await expect(dialog.getByRole("alert")).toContainText("业务对象无权绑定");
  await expect(dialog.getByLabel("名称", { exact: true })).toHaveValue(
    "被拒绝实例",
  );
});
