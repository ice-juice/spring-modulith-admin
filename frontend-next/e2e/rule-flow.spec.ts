import { test, expect } from "@playwright/test";
import { ruleFixture } from "./rule-fixture";

test("changing selected nodes discards late child-version responses and read-only users cannot add components", async ({
  page,
}) => {
  const fixture = await ruleFixture(page);
  fixture.doc.definition = {
    kind: "FLOW",
    flow: {
      startNode: "start",
      nodes: [
        { id: "start", kind: "START", next: "child" },
        {
          id: "child",
          kind: "SUBRULE",
          subRuleVersionId: "9007199254740993999",
          next: "result",
          inputBindings: { amount: "input.amount" },
          parameterBindings: { ratio: "params.ratio" },
          outputSchema: { type: "DECIMAL" },
        },
        { id: "result", kind: "RESULT", expression: "context.steps.child" },
      ],
    },
  } as unknown as typeof fixture.doc.definition;
  let release: () => void = () => {},
    requested = false;
  const pending = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route("**/api/rule/assets/*/versions", async (route) => {
    requested = true;
    await pending;
    await route.fulfill({ json: { code: 0, data: [] } });
  });
  await page.goto("/rule/templates/" + fixture.template.id);
  await page
    .getByRole("combobox", { name: "节点", exact: true })
    .selectOption("child");
  await page
    .getByRole("button", { name: "浏览模板 / 加载更多", exact: true })
    .click();
  await page
    .getByRole("combobox", { name: "子规则模板", exact: true })
    .selectOption(fixture.template.id);
  await page.getByRole("button", { name: "读取模板版本", exact: true }).click();
  await expect.poll(() => requested).toBe(true);
  await page
    .getByRole("combobox", { name: "节点", exact: true })
    .selectOption("result");
  release();
  await expect(
    page.getByRole("textbox", { name: "节点取值", exact: true }),
  ).toHaveValue("context.steps.child");
  await page
    .getByRole("combobox", { name: "节点", exact: true })
    .selectOption("child");
  await expect(
    page.getByText("已固定版本 · 9007199254740993999", { exact: true }),
  ).toBeVisible();
  fixture.host.me.permissions = fixture.host.me.permissions.filter(
    (key) => key !== "rule:template:edit",
  );
  await page.reload();
  await expect(page.locator('[data-node-kind="EXPRESSION"]')).toBeDisabled();
  await expect(
    page.getByRole("button", { name: "保存草稿", exact: true }),
  ).toHaveCount(0);
  expect(fixture.writes).toHaveLength(0);
});

test("conditions, switch ports and output contracts are configured with forms", async ({
  page,
}) => {
  const fixture = await ruleFixture(page);
  fixture.doc.definition = {
    kind: "FLOW",
    flow: {
      startNode: "start",
      nodes: [
        { id: "start", kind: "START", next: "result" },
        { id: "result", kind: "RESULT", expression: "input.amount" },
      ],
    },
  } as unknown as typeof fixture.doc.definition;
  await page.goto("/rule/templates/" + fixture.template.id);
  await page.locator('[data-node-kind="CONDITION"]').click();
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
  await page
    .getByRole("combobox", { name: "node1 · 成立出口", exact: true })
    .selectOption("result");
  await page
    .getByRole("combobox", { name: "node1 · 不成立出口", exact: true })
    .selectOption("result");
  await page
    .getByRole("combobox", { name: "节点", exact: true })
    .selectOption("start");
  await page
    .getByRole("combobox", { name: "start · 下一节点 / 默认出口", exact: true })
    .selectOption("node1");
  await page.locator('[data-node-kind="SWITCH"]').click();
  await page
    .getByRole("textbox", { name: "分支 option · 匹配值", exact: true })
    .fill("gold");
  await page
    .getByRole("textbox", { name: "分支 option · 匹配值", exact: true })
    .press("Tab");
  await page
    .getByRole("combobox", { name: "分支 gold · 出口", exact: true })
    .selectOption("result");
  await page.getByRole("button", { name: "添加选择分支", exact: true }).click();
  await expect(
    page.getByRole("textbox", { name: "分支 option1 · 匹配值", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "保存草稿", exact: true }).click();
  await expect(
    page.getByRole("status").filter({ hasText: "草稿已保存" }),
  ).toBeVisible();
  const document = (
    fixture.writes.find((write) => write.path.endsWith("/draft"))!.body
      .template as {
      document: {
        definition: {
          flow: {
            nodes: Array<{
              id: string;
              expression: unknown;
              condition: {
                children: Array<{
                  operator: string;
                  left: { path: string };
                  right: { value: unknown };
                }>;
              };
              cases?: Record<string, string>;
            }>;
          };
        };
      };
    }
  ).document;
  const condition = document.definition.flow.nodes.find(
    (node) => node.id === "node1",
  )!;
  expect(condition.expression).toBeNull();
  expect(condition.condition.children[0]!.operator).toBe("GE");
  expect(condition.condition.children[0]!.left.path).toBe("amount");
  expect(condition.condition.children[0]!.right.value).toBe(100);
  expect(
    document.definition.flow.nodes.find((node) => node.id === "node2")!.cases,
  ).toEqual({ gold: "result", option1: "" });
});

test("a pinned child version provides field mappings and output contract without JSON editing", async ({
  page,
}) => {
  const fixture = await ruleFixture(page),
    childDocument = structuredClone(fixture.doc);
  fixture.doc.definition = {
    kind: "FLOW",
    flow: {
      startNode: "start",
      nodes: [
        { id: "start", kind: "START", next: "result" },
        { id: "result", kind: "RESULT", expression: "input.amount" },
      ],
    },
  } as unknown as typeof fixture.doc.definition;
  await page.route("**/api/rule/assets/*/versions", (route) =>
    route.fulfill({
      json: {
        code: 0,
        data: [
          {
            id: "9007199254740993999",
            assetId: fixture.template.id,
            revision: "2",
            document: childDocument,
          },
        ],
      },
    }),
  );
  await page.goto("/rule/templates/" + fixture.template.id);
  await page.locator('[data-node-kind="SUBRULE"]').click();
  await page
    .getByRole("button", { name: "浏览模板 / 加载更多", exact: true })
    .click();
  await page
    .getByRole("combobox", { name: "子规则模板", exact: true })
    .selectOption(fixture.template.id);
  await page.getByRole("button", { name: "读取模板版本", exact: true }).click();
  await page
    .getByRole("combobox", { name: "子规则版本", exact: true })
    .selectOption("9007199254740993999");
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
  await page
    .getByRole("combobox", { name: "node1 · 下一节点 / 默认出口", exact: true })
    .selectOption("result");
  await page
    .getByRole("combobox", { name: "节点", exact: true })
    .selectOption("start");
  await page
    .getByRole("combobox", { name: "start · 下一节点 / 默认出口", exact: true })
    .selectOption("node1");
  await page
    .getByRole("combobox", { name: "节点", exact: true })
    .selectOption("result");
  await page
    .getByRole("combobox", { name: "节点取值 · 选择字段来源", exact: true })
    .selectOption("context.steps.node1");
  await page.getByRole("button", { name: "保存草稿", exact: true }).click();
  await expect(
    page.getByRole("status").filter({ hasText: "草稿已保存" }),
  ).toBeVisible();
  const draft = (
    fixture.writes.find((write) => write.path.endsWith("/draft"))!.body
      .template as {
      document: {
        definition: { flow: { nodes: Array<Record<string, unknown>> } };
      };
    }
  ).document;
  const child = draft.definition.flow.nodes.find(
    (node) => node.id === "node1",
  )!;
  expect(child.subRuleVersionId).toBe("9007199254740993999");
  expect(child.inputBindings).toEqual({ amount: "input.amount" });
  expect(child.parameterBindings).toEqual({ ratio: "params.ratio" });
  expect((child.outputSchema as { type: string }).type).toBe("DECIMAL");
  expect(
    draft.definition.flow.nodes.find((node) => node.id === "result")!
      .expression,
  ).toBe("context.steps.node1");
});

test("a component can be dragged to the zoomed canvas and its edge disconnected and restored", async ({
  page,
}) => {
  const fixture = await ruleFixture(page);
  fixture.doc.definition = {
    kind: "FLOW",
    flow: {
      startNode: "start",
      nodes: [
        { id: "start", kind: "START", next: "result" },
        { id: "result", kind: "RESULT", expression: "input.amount" },
      ],
    },
  } as unknown as typeof fixture.doc.definition;
  await page.setViewportSize({ width: 1680, height: 1100 });
  await page.goto("/rule/templates/" + fixture.template.id);
  const canvas = page.locator(".rule-canvas");
  await expect(page.locator(".vue-flow")).toBeVisible();
  await page.getByRole("button", { name: "缩小", exact: true }).click();
  await page
    .locator('[data-node-kind="EXPRESSION"]')
    .dragTo(canvas, { targetPosition: { x: 140, y: 140 } });
  const created = page.locator('.vue-flow__node[data-id="node1"]');
  await expect(created).toBeVisible();
  const box = await canvas.boundingBox(),
    nodeBox = await created.boundingBox();
  expect(Math.abs(nodeBox!.x - (box!.x + 140))).toBeLessThan(8);
  expect(Math.abs(nodeBox!.y - (box!.y + 140))).toBeLessThan(8);
  await page
    .getByRole("combobox", { name: "选择连线", exact: true })
    .selectOption("start:next");
  await page.getByRole("button", { name: "断开选中连线", exact: true }).click();
  await expect(page.locator(".vue-flow__edge")).toHaveCount(0);
  await page.getByRole("button", { name: "撤销", exact: true }).click();
  await expect(page.locator(".vue-flow__edge")).toHaveCount(1);
  await page
    .getByRole("combobox", { name: "节点", exact: true })
    .selectOption("node1");
  await page.getByRole("button", { name: "移除", exact: true }).click();
  await page
    .getByRole("alertdialog")
    .getByRole("button", { name: "确认", exact: true })
    .click();
  await expect(created).toHaveCount(0);
  await page.getByRole("button", { name: "撤销", exact: true }).click();
  await expect(created).toBeVisible();
  expect(fixture.writes).toHaveLength(0);
});

test("flow authoring history restores nodes and connections without publishing and resets after refresh", async ({
  page,
}) => {
  const fixture = await ruleFixture(page);
  fixture.doc.definition = {
    kind: "FLOW",
    flow: {
      startNode: "start",
      nodes: [
        { id: "start", kind: "START", next: "result" },
        { id: "result", kind: "RESULT", expression: "input.amount" },
      ],
    },
  } as unknown as typeof fixture.doc.definition;
  await page.goto("/rule/templates/" + fixture.template.id);
  const undo = page.getByRole("button", { name: "撤销", exact: true });
  const redo = page.getByRole("button", { name: "重做", exact: true });
  await expect(undo).toBeDisabled();
  await page.getByRole("button", { name: "添加节点", exact: true }).click();
  await expect(page.locator('.vue-flow__node[data-id="node1"]')).toBeVisible();
  await undo.click();
  await expect(page.locator('.vue-flow__node[data-id="node1"]')).toHaveCount(0);
  await redo.click();
  await expect(page.locator('.vue-flow__node[data-id="node1"]')).toBeVisible();
  await page
    .getByRole("combobox", { name: "节点", exact: true })
    .selectOption("start");
  const exit = page.getByRole("combobox", {
    name: "start · 下一节点 / 默认出口",
    exact: true,
  });
  await exit.selectOption("node1");
  await undo.click();
  await expect(exit).toHaveValue("result");
  await redo.click();
  await expect(exit).toHaveValue("node1");
  await page.getByRole("button", { name: "保存草稿", exact: true }).click();
  await expect(
    page.getByRole("status").filter({ hasText: "草稿已保存" }),
  ).toBeVisible();
  expect(fixture.writes.some((row) => row.path.endsWith("/publish"))).toBe(
    false,
  );
  await page.reload();
  await expect(undo).toBeDisabled();
  await expect(redo).toBeDisabled();
  await expect(page.locator('.vue-flow__node[data-id="node1"]')).toBeVisible();
});
test("flow nodes have connectable handles, editable exits and save a domain graph without canvas state", async ({
  page,
}) => {
  const fixture = await ruleFixture(page);
  fixture.doc.definition = {
    kind: "FLOW",
    flow: {
      startNode: "start",
      nodes: [
        { id: "start", kind: "START", next: "result" },
        { id: "result", kind: "RESULT", expression: "input.amount" },
      ],
    },
  } as unknown as typeof fixture.doc.definition;
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto("/rule/templates/" + fixture.template.id);
  await expect(page.locator(".vue-flow")).toBeVisible();
  await expect(page.locator(".vue-flow__handle.source")).toHaveCount(1);
  await page.getByRole("button", { name: "添加节点", exact: true }).click();
  await expect(
    page.getByRole("textbox", { name: "node1 · 规则表达式" }),
  ).toBeVisible();
  await page.getByRole("textbox", { name: "node1 · 规则表达式" }).focus();
  await page.keyboard.press("ControlOrMeta+a");
  await page.keyboard.type("input.amount");
  await page
    .getByRole("combobox", { name: "node1 · 下一节点 / 默认出口", exact: true })
    .selectOption("result");
  await page
    .getByRole("combobox", { name: "node1 · 输出契约 · 类型", exact: true })
    .selectOption("DECIMAL");
  await page
    .getByRole("combobox", { name: "节点", exact: true })
    .selectOption("start");
  await page.getByRole("button", { name: "整理布局", exact: true }).click();
  await page.locator(".rule-canvas").scrollIntoViewIfNeeded();
  const source = await page
      .locator('.vue-flow__node[data-id="start"] .source')
      .boundingBox(),
    target = await page
      .locator('.vue-flow__node[data-id="node1"] .target')
      .boundingBox();
  expect(source).toBeTruthy();
  expect(target).toBeTruthy();
  await page.mouse.move(
    source!.x + source!.width / 2,
    source!.y + source!.height / 2,
  );
  await page.mouse.down();
  await page.mouse.move(
    target!.x + target!.width / 2,
    target!.y + target!.height / 2,
    { steps: 12 },
  );
  await page.mouse.up();
  await expect(
    page.getByRole("combobox", {
      name: "start · 下一节点 / 默认出口",
      exact: true,
    }),
  ).toHaveValue("node1");
  await page.getByRole("button", { name: "保存草稿", exact: true }).click();
  await expect(
    page.getByRole("status").filter({ hasText: "草稿已保存" }),
  ).toBeVisible();
  const body = fixture.writes.find((row) => row.path.endsWith("/draft"))?.body;
  const definition = (
    body?.template as {
      document: {
        definition: { flow: { nodes: { id: string; next?: string }[] } };
      };
    }
  ).document.definition;
  expect(
    definition.flow.nodes.find((node: { id: string }) => node.id === "start")
      ?.next,
  ).toBe("node1");
  expect(JSON.stringify(definition)).not.toMatch(
    /"position"|sourceHandle|vue-flow/,
  );
  expect(errors).toEqual([]);
});
test("simulation trace selects a graph node and keeps the backend draft recoverable", async ({
  page,
}) => {
  const fixture = await ruleFixture(page);
  fixture.doc.definition = {
    kind: "FLOW",
    flow: {
      startNode: "start",
      nodes: [
        { id: "start", kind: "START", next: "result" },
        { id: "result", kind: "RESULT", expression: "input.amount" },
      ],
    },
  } as unknown as typeof fixture.doc.definition;
  await page.route("**/api/rule/simulations", (route) =>
    route.fulfill({
      json: {
        code: 0,
        data: {
          executionId: "sim",
          status: "SUCCESS",
          output: 100,
          problems: [],
          trace: [
            {
              id: "result",
              kind: "FLOW",
              evaluated: true,
              outcome: "EXECUTED",
            },
          ],
        },
      },
    }),
  );
  await page.goto("/rule/templates/" + fixture.template.id);
  await page.getByRole("button", { name: "模拟与解释", exact: true }).click();
  await page.getByLabel("输入数据", { exact: true }).fill('{"amount":100}');
  await page.getByLabel("实例参数", { exact: true }).fill('{"ratio":0.9}');
  await page.getByRole("button", { name: "模拟执行", exact: true }).click();
  await page.getByText("执行路径", { exact: true }).click();
  await page
    .getByRole("button", { name: "result · EXECUTED", exact: true })
    .click();
  await expect(
    page.getByRole("textbox", { name: "result · 规则表达式" }),
  ).toBeVisible();
  await page.reload();
  await expect(
    page.getByRole("button", { name: "result · 返回结果", exact: true }),
  ).toBeVisible();
});
