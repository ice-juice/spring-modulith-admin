import { test, expect } from "@playwright/test";
import { ruleFixture } from "./rule-fixture";
import { sampleIds, sampleScene } from "../src/features/rule/samples";

test("an in-flight draft save protects the submitted edit and unlocks it after a conflict", async ({
  page,
}) => {
  const fixture = await ruleFixture(page);
  let release: () => void = () => {},
    started = false;
  const delayed = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route("**/api/rule/templates/*/draft", async (route) => {
    started = true;
    await delayed;
    await route.fulfill({
      json: {
        code: 409,
        error: "rule.error.conflict",
        message: "修订冲突",
        data: null,
      },
    });
  });
  await page.goto("/rule/templates/" + fixture.template.id);
  const name = page.getByRole("textbox", { name: "名称", exact: true });
  await name.fill("保留当前编辑");
  await page.getByRole("button", { name: "保存草稿", exact: true }).click();
  await expect.poll(() => started).toBe(true);
  await expect(name).toHaveAttribute("readonly");
  const editor = page.getByRole("textbox", { name: "规则表达式", exact: true });
  await editor.focus();
  await page.keyboard.press("ControlOrMeta+a");
  await page.keyboard.type("should_not_replace_the_submitted_rule");
  await expect(page.locator(".monaco-editor .view-lines")).toContainText("round(input.amount * params.ratio, 2)");
  release();
  await expect(
    page.getByRole("alert").filter({ hasText: "修订冲突" }),
  ).toBeVisible();
  await expect(name).not.toHaveAttribute("readonly");
  await expect(name).toHaveValue("保留当前编辑");
  await expect(
    page.getByRole("button", { name: "保存草稿", exact: true }),
  ).toBeEnabled();
});

for (const sample of sampleIds)
  test(`optional ${sample} template loads its matching contract and assertions without auto-publishing`, async ({
    page,
  }) => {
    const fixture = await ruleFixture(page),
      scene = sampleScene(sample, (key) => key);
    Object.assign(fixture.scene.draft, scene);
    await page.goto("/rule/templates");
    await page.getByRole("button", { name: "新增模板", exact: true }).click();
    const dialog = page.getByRole("dialog");
    await dialog
      .getByRole("combobox", { name: "所属场景", exact: true })
      .selectOption(fixture.scene.id);
    await dialog
      .getByRole("combobox", { name: "示例模板", exact: true })
      .selectOption(sample);
    await dialog.getByLabel("编码", { exact: true }).fill("sample_" + sample);
    await dialog.getByLabel("名称", { exact: true }).fill("示例 " + sample);
    await dialog.getByRole("button", { name: "保存", exact: true }).click();
    // Selecting a starter opens the editor; creating the actual asset remains explicit.
    await expect(
      page.getByRole("button", { name: "保存草稿", exact: true }),
    ).toBeVisible();
    expect(
      fixture.writes.filter((write) => write.path === "/templates"),
    ).toHaveLength(0);
    await page.getByRole("button", { name: "保存草稿", exact: true }).click();
    await page.waitForURL(/\/rule\/templates\/new_template$/);
    await page.getByRole("button", { name: "模拟与解释", exact: true }).click();
    await page
      .getByRole("combobox", { name: "示例输入", exact: true })
      .selectOption("0");
    await page
      .getByRole("button", { name: "载入示例数据", exact: true })
      .click();
    await expect(page.getByLabel("输入数据", { exact: true })).toHaveValue(
      sample === "tiers" ? /"amount": 200/ : /"amount": 100/,
    );
    const saved = fixture.writes.find((write) => write.path === "/templates")!
      .body.document as { definition: { kind: string }; examples: unknown[] };
    expect(saved.definition.kind).toBe(scene.allowedKinds[0]);
    expect(saved.examples).toHaveLength(3);
    expect(
      fixture.writes.some((write) => write.path.endsWith("/publish")),
    ).toBe(false);
  });

test("sample scenes require an explicit save and replacing edited fields can be cancelled", async ({
  page,
}) => {
  const fixture = await ruleFixture(page);
  await page.goto("/rule/scenes");
  await page.getByRole("button", { name: "新增场景", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog
    .getByRole("combobox", { name: "示例场景", exact: true })
    .selectOption("ratio");
  await expect(dialog.getByLabel("名称", { exact: true })).toHaveValue(
    "比例折扣（表达式）",
  );
  expect(fixture.writes).toHaveLength(0);
  await dialog
    .getByRole("combobox", { name: "示例场景", exact: true })
    .selectOption("reduction");
  await page
    .getByRole("alertdialog")
    .getByRole("button", { name: "取消", exact: true })
    .click();
  await expect(
    dialog.getByRole("combobox", { name: "示例场景", exact: true }),
  ).toHaveValue("ratio");
  await expect(dialog.getByLabel("名称", { exact: true })).toHaveValue(
    "比例折扣（表达式）",
  );
  await dialog.getByRole("button", { name: "保存", exact: true }).click();
  await expect.poll(() => fixture.writes.find((write) => write.path === "/scenes")?.body.allowedKinds).toEqual(["EXPRESSION"]);
  expect(fixture.writes.some((write) => write.path === "/templates")).toBe(
    false,
  );
});

test("contract reference and model-scoped expression completion use the current fields and server functions", async ({
  page,
}) => {
  const fixture = await ruleFixture(page);
  await page.goto("/rule/templates/" + fixture.template.id);
  const references = page.getByTestId("rule-references");
  await references.locator("summary").click();
  await expect(
    references.getByText("input.amount", { exact: true }),
  ).toBeVisible();
  await expect(
    references.getByText("round(value, scale)", { exact: true }),
  ).toBeVisible();
  const editor = page.getByRole("textbox", { name: "规则表达式", exact: true });
  await editor.focus();
  await page.keyboard.press("ControlOrMeta+a");
  await page.keyboard.type("input.am");
  await page.keyboard.press("Control+Space");
  await expect(page.locator(".suggest-widget.visible")).toBeVisible();
  await page.keyboard.press("Enter");
  await expect(page.locator(".monaco-editor .view-lines")).toContainText(
    "input.amount",
  );
  await page.getByRole("button", { name: "保存草稿", exact: true }).click();
  await expect(
    page.getByRole("status").filter({ hasText: "草稿已保存" }),
  ).toBeVisible();
  const fields = fixture.template.draft.inputSchema.fields as Record<
    string,
    unknown
  >;
  delete fields.amount;
  fields.total = { type: "DECIMAL", required: true };
  await page.reload();
  await references.locator("summary").click();
  await expect(
    references.getByText("input.total", { exact: true }),
  ).toBeVisible();
  await expect(
    references.getByText("input.amount", { exact: true }),
  ).toHaveCount(0);
  await editor.focus();
  await page.keyboard.press("ControlOrMeta+a");
  await page.keyboard.type("input.to");
  await page.keyboard.press("Control+Space");
  await expect(page.locator(".suggest-widget.visible")).toBeVisible();
  await page.keyboard.press("Enter");
  await expect(page.locator(".monaco-editor .view-lines")).toContainText(
    "input.total",
  );
  expect(fixture.unmatched).toEqual([]);
});

test("simulation and validation feedback expires on raw edits and ignores late responses", async ({
  page,
}) => {
  const fixture = await ruleFixture(page);
  let release: () => void = () => {},
    started = false,
    count = 0;
  const delayed = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route("**/api/rule/simulations", async (route) => {
    count++;
    if (count === 1) {
      started = true;
      await delayed;
    }
    await route.fulfill({
      json: {
        code: 0,
        data: { status: "SUCCESS", output: 90, trace: [], problems: [] },
      },
    });
  });
  await page.goto("/rule/templates/" + fixture.template.id);
  await page.getByRole("button", { name: "模拟与解释", exact: true }).click();
  const input = page.getByLabel("输入数据", { exact: true });
  await input.fill('{"amount":100}');
  await page.getByLabel("实例参数", { exact: true }).fill('{"ratio":0.9}');
  const simulate = page.getByRole("button", { name: "模拟执行", exact: true });
  await simulate.click();
  await expect.poll(() => started).toBe(true);
  await input.fill('{"amount":200}');
  release();
  await expect(simulate).toBeEnabled();
  await expect(page.getByText("SUCCESS", { exact: true })).toHaveCount(0);
  await expect(
    page.getByText("规则或试算数据已修改，请重新校验或模拟。", { exact: true }),
  ).toBeVisible();
  await simulate.click();
  await expect(page.getByText("SUCCESS", { exact: true })).toBeVisible();
  // Invalid JSON does not update the model; its raw edit must still invalidate the old result.
  await input.fill('{"amount":');
  await expect(page.getByText("SUCCESS", { exact: true })).toHaveCount(0);
  await simulate.click();
  expect(count).toBe(2);
  await input.fill('{"amount":300}');
  await page.getByRole("button", { name: "检查规则", exact: true }).click();
  await expect(page.getByText("规则校验通过", { exact: true })).toBeVisible();
  await page.getByRole("textbox", { name: "规则表达式", exact: true }).focus();
  await page.keyboard.press("ControlOrMeta+a");
  await page.keyboard.type("input.amount");
  await expect(page.getByText("规则校验通过", { exact: true })).toHaveCount(0);
  expect(fixture.writes.some((item) => item.path.endsWith("/publish"))).toBe(
    false,
  );
});
test("scene fields, template expression and simulation use the exact contract", async ({
  page,
}) => {
  const fixture = await ruleFixture(page);
  await page.goto("/rule/scenes");
  await expect(
    page.getByRole("cell", { name: "商品折扣", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "新增场景" }).click();
  await page
    .getByRole("dialog")
    .getByLabel("编码", { exact: true })
    .fill("other_scene");
  await page
    .getByRole("dialog")
    .getByLabel("名称", { exact: true })
    .fill("独立场景");
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "保存", exact: true })
    .click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await page.goto("/rule/templates/" + fixture.template.id);
  await expect(page.locator(".monaco-editor")).toBeVisible();
  await page.getByRole("button", { name: "模拟与解释", exact: true }).click();
  await page
    .getByLabel("输入数据", { exact: true })
    .fill('{"amount":12345678901234567890.12345678}');
  await page.getByLabel("实例参数", { exact: true }).fill('{"ratio":0.9}');
  await page.getByRole("button", { name: "模拟执行", exact: true }).click();
  await expect(page.getByText("SUCCESS", { exact: true })).toBeVisible();
  expect(
    fixture.writes.find((row) => row.path === "/simulations")?.raw,
  ).toContain("12345678901234567890.12345678");
  await page.getByRole("textbox", { name: "规则表达式" }).focus();
  await page.keyboard.press("ControlOrMeta+a");
  await page.keyboard.type("input.amount");
  await page.getByRole("button", { name: "保存草稿", exact: true }).click();
  await expect(
    page.getByRole("status").filter({ hasText: "草稿已保存" }),
  ).toBeVisible();
  expect(fixture.writes.some((row) => row.path.endsWith("/publish"))).toBe(
    false,
  );
  expect(fixture.unmatched).toEqual([]);
});
test("condition groups provide editable operands and preserve unsaved work", async ({
  page,
}) => {
  const fixture = await ruleFixture(page);
  fixture.doc.definition = {
    kind: "CONDITION",
    condition: { id: "root", operator: "AND", children: [] },
    onMatch: 90,
    otherwise: 100,
  } as unknown as typeof fixture.doc.definition;
  await page.goto("/rule/templates/" + fixture.template.id);
  await page.getByRole("button", { name: "添加条件", exact: true }).click();
  await expect(page.getByLabel("左值 · 位置")).toBeVisible();
  await page.getByLabel("左值 · 位置").fill("amount");
  await page.getByRole("button", { name: "返回列表", exact: true }).click();
  await expect(page.getByRole("alertdialog")).toBeVisible();
  await page
    .getByRole("alertdialog")
    .getByRole("button", { name: "取消", exact: true })
    .click();
  await expect(page.getByLabel("左值 · 位置")).toHaveValue("amount");
});
test("revision conflicts keep the local expression and do not publish or retry", async ({
  page,
}) => {
  const fixture = await ruleFixture(page);
  let submissions = 0;
  await page.route("**/api/rule/templates/*/draft", async (route) => {
    submissions++;
    await route.fulfill({
      json: {
        code: 409,
        error: "rule.error.conflict",
        message: "修订冲突",
        data: null,
      },
    });
  });
  await page.goto("/rule/templates/" + fixture.template.id);
  await page.getByRole("textbox", { name: "规则表达式" }).focus();
  await page.keyboard.press("ControlOrMeta+a");
  await page.keyboard.type("input.amount + 10");
  await page.getByRole("button", { name: "保存草稿", exact: true }).click();
  await expect(
    page.getByRole("alert").filter({ hasText: "修订冲突" }),
  ).toBeVisible();
  await expect(page.locator(".monaco-editor")).toContainText(
    "input.amount + 10",
  );
  expect(submissions).toBe(1);
  expect(fixture.writes.some((row) => row.path.endsWith("/publish"))).toBe(
    false,
  );
});
