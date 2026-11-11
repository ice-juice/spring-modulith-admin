import { expect, test, type Page } from "@playwright/test";

test("dragged components, visual conditions and pinned input/output mappings execute on the real backend", async ({
  page,
}) => {
  test.setTimeout(150000);
  const password = process.env.SMA_E2E_ADMIN_PASSWORD;
  if (!password) throw new Error("Run with the isolated real E2E launcher.");
  await page.setViewportSize({ width: 1720, height: 1100 });
  await page.goto("/login");
  await page
    .getByRole("textbox", { name: "用户名", exact: true })
    .fill("admin");
  await page.getByLabel("密码", { exact: true }).fill(password);
  await page.getByRole("button", { name: "登录", exact: true }).click();
  await page.waitForURL((url) => url.pathname === "/");
  await page.goto("/rule/scenes");
  await page.getByRole("button", { name: "新增场景", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog
    .getByRole("combobox", { name: "示例场景", exact: true })
    .selectOption("ratio");
  await dialog.getByRole("checkbox", { name: "流程编排", exact: true }).check();
  await dialog.getByRole("button", { name: "保存", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  async function newTemplate(code: string, name: string, sample = "") {
    await page.goto("/rule/templates");
    await page.getByRole("button", { name: "新增模板", exact: true }).click();
    await dialog
      .getByRole("combobox", { name: "所属场景", exact: true })
      .selectOption({ label: "比例折扣（表达式）" });
    if (sample)
      await dialog
        .getByRole("combobox", { name: "示例模板", exact: true })
        .selectOption(sample);
    await dialog.getByLabel("编码", { exact: true }).fill(code);
    await dialog.getByLabel("名称", { exact: true }).fill(name);
    await dialog.getByRole("button", { name: "保存", exact: true }).click();
    await expect(
      page.getByRole("heading", { name: "新增模板", exact: true }),
    ).toBeVisible();
  }
  async function saveNew() {
    const pending = page.waitForResponse(
      (response) =>
        response.url().endsWith("/api/rule/templates") &&
        response.request().method() === "POST",
    );
    await page.getByRole("button", { name: "保存草稿", exact: true }).click();
    const body = await (await pending).json();
    expect(body.code, body.message).toBe(0);
    await page.waitForURL(/\/rule\/templates\/\d+$/);
    await expect(
      page.getByRole("status").filter({ hasText: "草稿已保存" }),
    ).toBeVisible();
    return page.url().split("/").at(-1)!;
  }
  async function publish() {
    await page.getByRole("button", { name: "发布", exact: true }).click();
    await page
      .getByRole("alertdialog")
      .getByRole("button", { name: "确认", exact: true })
      .click();
    await expect(
      page.getByText("状态 · 已发布", { exact: true }),
    ).toBeVisible();
  }
  await newTemplate("visual_child_discount", "可复用折扣子规则", "ratio");
  const childId = await saveNew();
  await publish();
  await newTemplate("visual_parent_flow", "拖拽资格与计算流程");
  await page
    .getByRole("combobox", { name: "规则类型", exact: true })
    .selectOption("FLOW");
  await page
    .getByRole("alertdialog")
    .getByRole("button", { name: "确认", exact: true })
    .click();
  await expect(page.locator(".vue-flow")).toBeVisible();
  await page
    .locator('[data-node-kind="CONDITION"]')
    .dragTo(page.locator(".rule-canvas"), {
      targetPosition: { x: 100, y: 100 },
    });
  await page
    .getByRole("combobox", { name: "判断方式", exact: true })
    .selectOption("structured");
  await page
    .getByRole("alertdialog")
    .getByRole("button", { name: "确认", exact: true })
    .click();
  await page.getByRole("button", { name: "添加条件", exact: true }).click();
  await page
    .getByRole("combobox", { name: "运算符", exact: true })
    .last()
    .selectOption("GE");
  await page
    .getByRole("combobox", { name: "左值 · 选择字段来源", exact: true })
    .selectOption("input.amount");
  await page.getByLabel("右值 · 值", { exact: true }).fill("100");
  await page.locator('[data-node-kind="SUBRULE"]').click();
  await page
    .getByRole("button", { name: "浏览模板 / 加载更多", exact: true })
    .click();
  await page
    .getByRole("combobox", { name: "子规则模板", exact: true })
    .selectOption(childId);
  await page.getByRole("button", { name: "读取模板版本", exact: true }).click();
  await expect(
    page
      .getByRole("combobox", { name: "子规则版本", exact: true })
      .locator("option"),
  ).toHaveCount(2);
  await page
    .getByRole("combobox", { name: "子规则版本", exact: true })
    .selectOption({ index: 1 });
  await page.getByRole("button", { name: "应用此版本", exact: true }).click();
  await page
    .getByRole("combobox", {
      name: "输入映射 · amount · 选择字段来源",
      exact: true,
    })
    .selectOption("input.amount");
  await page
    .getByRole("combobox", {
      name: "参数映射 · ratio · 选择字段来源",
      exact: true,
    })
    .selectOption("params.ratio");
  await page.locator('[data-node-kind="RESULT"]').click();
  await page
    .getByRole("combobox", { name: "节点", exact: true })
    .selectOption("start");
  await exit(page, "start", "next", "node1");
  await page
    .getByRole("combobox", { name: "节点", exact: true })
    .selectOption("node1");
  await exit(page, "node1", "true", "node2");
  await exit(page, "node1", "false", "result");
  await page
    .getByRole("combobox", { name: "节点", exact: true })
    .selectOption("node2");
  await exit(page, "node2", "next", "node3");
  await page
    .getByRole("combobox", { name: "节点", exact: true })
    .selectOption("node3");
  await page
    .getByRole("combobox", { name: "节点取值 · 选择字段来源", exact: true })
    .selectOption("context.steps.node2");
  await page
    .getByRole("combobox", { name: "节点", exact: true })
    .selectOption("result");
  await page
    .getByRole("combobox", { name: "节点取值 · 选择字段来源", exact: true })
    .selectOption("input.amount");
  const examples = [
    {
      name: "qualifying",
      input: { amount: 100 },
      params: { ratio: 0.9 },
      expectedOutput: 90,
    },
    {
      name: "below",
      input: { amount: 99.99 },
      params: { ratio: 0.9 },
      expectedOutput: 99.99,
    },
    {
      name: "zero",
      input: { amount: 0 },
      params: { ratio: 0.9 },
      expectedOutput: 0,
    },
  ];
  // Only test assertions use JSON. The graph, condition, bindings and result are authored through UI controls.
  await page.getByRole("tab", { name: "发布断言", exact: true }).click();
  await page
    .getByLabel("发布断言", { exact: true })
    .fill(JSON.stringify(examples));
  const parentId = await saveNew();
  await page.getByRole("button", { name: "模拟与解释", exact: true }).click();
  for (let index = 0; index < examples.length; index++) {
    await page
      .getByRole("combobox", { name: "示例输入", exact: true })
      .selectOption(String(index));
    await page
      .getByRole("button", { name: "载入示例数据", exact: true })
      .click();
    const pending = page.waitForResponse(
      (response) =>
        response.url().endsWith("/api/rule/simulations") &&
        response.request().method() === "POST",
    );
    await page.getByRole("button", { name: "模拟执行", exact: true }).click();
    const simulated = await (await pending).json();
    expect(simulated.code, simulated.message).toBe(0);
    expect(simulated.data.status, JSON.stringify(simulated.data.problems)).toBe(
      "SUCCESS",
    );
    await expect(page.getByText("SUCCESS", { exact: true })).toBeVisible();
    const output = page.locator('section[aria-live="polite"] pre');
    const expected = String(examples[index]!.expectedOutput).replace(
      ".",
      "\\.",
    );
    await expect(output).toHaveText(
      new RegExp("^" + expected + (index === 1 ? "" : "(?:\\.0+)?") + "$"),
    );
  }
  await publish();
  await page.reload();
  await expect(page.getByText("状态 · 已发布", { exact: true })).toBeVisible();
  await expect(page.locator(".vue-flow__node")).toHaveCount(5);
  await page
    .getByRole("combobox", { name: "节点", exact: true })
    .selectOption("node2");
  await expect(page.getByText(/已固定版本 · \d+/)).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "拖拽资格与计算流程", exact: true }),
  ).toBeVisible();
  expect(parentId).not.toBe(childId);
});

async function exit(
  page: Page,
  id: string,
  handle: "next" | "true" | "false",
  target: string,
) {
  const labels = {
    next: "下一节点 / 默认出口",
    true: "成立出口",
    false: "不成立出口",
  };
  await page
    .getByRole("combobox", { name: `${id} · ${labels[handle]}`, exact: true })
    .selectOption(target);
}
