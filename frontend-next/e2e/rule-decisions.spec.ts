import { test, expect } from "@playwright/test";
import { ruleFixture } from "./rule-fixture";
test("decision table supports explicit cells and preserves FIRST order in saved drafts", async ({
  page,
}) => {
  const fixture = await ruleFixture(page);
  Object.assign(fixture.doc, {
    outputSchema: {
      type: "OBJECT",
      required: true,
      properties: { price: { type: "DECIMAL", required: true } },
    },
    definition: {
      kind: "DECISION_TABLE",
      table: {
        hitPolicy: "UNIQUE",
        inputs: [],
        rows: [],
        defaultOutput: { price: 0 },
      },
    },
  });
  await page.goto("/rule/templates/" + fixture.template.id);
  await page.getByRole("combobox", { name: "命中策略" }).selectOption("FIRST");
  await page.getByRole("button", { name: "添加输入列", exact: true }).click();
  await page.getByRole("button", { name: "添加决策行", exact: true }).click();
  await page.getByRole("button", { name: "添加决策行", exact: true }).click();
  await page
    .getByRole("combobox", { name: "column1 · 运算符" })
    .selectOption("GE");
  await page.getByLabel("column1 · 右值 · 值", { exact: true }).fill("100");
  await page.getByRole("button", { name: "row2 · 上移", exact: true }).click();
  await page.getByRole("button", { name: "保存草稿", exact: true }).click();
  await expect(
    page.getByRole("status").filter({ hasText: "草稿已保存" }),
  ).toBeVisible();
  const wire =
    fixture.writes.find((item) => item.path.endsWith("/draft"))?.raw || "";
  expect(wire.indexOf('"id":"row2"')).toBeLessThan(wire.indexOf('"id":"row1"'));
  expect(wire).toContain('"operator":"GE"');
  expect(wire).toContain('"value":100');
});
test("upgrade preview binds samples and creates only a new draft", async ({
  page,
}) => {
  const fixture = await ruleFixture(page);
  fixture.instances.push({
    id: "instance1",
    kind: "INSTANCE",
    name: "活动折扣",
    code: "active",
    revision: "1",
    releaseRevision: "1",
    state: "PUBLISHED",
    activeVersionId: "active1",
    draft: { templateVersionId: fixture.version.id, params: { ratio: 0.9 } },
  });
  await page.goto("/rule/instances");
  await page.getByRole("button", { name: "详情", exact: true }).click();
  await page.getByText("升级预览", { exact: true }).click();
  await page.getByLabel("目标版本", { exact: true }).fill(fixture.version.id);
  await page
    .getByLabel("代表性输入集合", { exact: true })
    .fill('[{"amount":100}]');
  await page.getByRole("button", { name: "对比预览", exact: true }).click();
  await expect(page.getByText("输出有变化", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "保存迁移草稿", exact: true }).click();
  await page
    .getByRole("alertdialog")
    .getByRole("button", { name: "确认", exact: true })
    .click();
  await expect(page.getByText("2", { exact: true }).first()).toBeVisible();
  expect(
    fixture.writes.some((item) => item.path.endsWith("/upgrade-draft")),
  ).toBe(true);
  expect(fixture.writes.some((item) => item.path.endsWith("/publish"))).toBe(
    false,
  );
});
