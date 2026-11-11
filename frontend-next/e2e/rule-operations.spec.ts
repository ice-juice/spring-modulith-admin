import { test, expect } from "@playwright/test";
import { ruleFixture } from "./rule-fixture";

test('an interrupted migration keeps the original key and displays partial evidence without another write', async ({ page }) => {
  await ruleFixture(page); let writes = 0, key = '', complete = false
  await page.route('**/api/rule/migration-batches', route => {
    writes++; const input = route.request().postDataJSON(); key = input.operationKey
    expect(route.request().headers()['idempotency-key']).toBe(key)
    expect(input.items.map((item: { instanceId: string }) => item.instanceId)).toEqual(['123'])
    return route.fulfill({ status: 503, json: { code: 503, message: 'Lost batch response' } })
  })
  await page.route('**/api/rule/migration-batches/operations/*', route => {
    expect(new URL(route.request().url()).pathname.split('/').at(-1)).toBe(key)
    return route.fulfill({ json: { code: 0, data: { operationKey: key, report: { id: '9007199254740993201', state: complete ? 'COMPLETED_WITH_ERRORS' : 'RECOVERY_REQUIRED', total: 1, allPassed: false, results: complete ? [{ instanceId: '123', passed: false, error: 'rule.error.conflict' }] : [], pendingInstanceIds: complete ? [] : ['123'] } } } })
  })
  await page.goto('/rule/operations'); await page.getByRole('button', { name: '批量迁移', exact: true }).click()
  await page.getByRole('button', { name: '迁移项目', exact: true }).click()
  await page.getByLabel('实例标识', { exact: true }).fill('123')
  await page.getByLabel('草稿修订', { exact: true }).fill('9007199254740993999')
  await page.getByLabel('模板版本标识', { exact: true }).fill('9007199254740993103')
  await page.getByRole('button', { name: '保存迁移草稿', exact: true }).evaluate(button => { for (let i = 0; i < 20; i++) (button as HTMLButtonElement).click() })
  await expect(page.getByRole('alertdialog')).toHaveCount(1)
  await page.getByRole('alertdialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.getByRole('button', { name: '查询原操作结果', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '查询原操作结果', exact: true }).click()
  await expect(page.getByRole('heading', { name: /RECOVERY_REQUIRED/ })).toBeVisible()
  await expect(page.getByText('待核对项目 · 123', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '保存迁移草稿', exact: true })).toBeDisabled()
  complete = true; await page.getByRole('button', { name: '查询原操作结果', exact: true }).click()
  await expect(page.getByRole('heading', { name: /COMPLETED_WITH_ERRORS/ })).toBeVisible()
  await expect(page.getByRole('button', { name: '查询原操作结果', exact: true })).toHaveCount(0)
  expect(writes).toBe(1)
})
test("professional users maintain assertions and read a failed fixed-version report", async ({
  page,
}) => {
  const fixture = await ruleFixture(page);
  await page.goto("/rule/templates/" + fixture.template.id);
  await page.getByText("测试用例", { exact: true }).click();
  await page.getByRole("button", { name: "新增用例" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("用例名称", { exact: true }).fill("折扣断言");
  await dialog.getByLabel("输入数据", { exact: true }).fill('{"amount":100}');
  await dialog.getByLabel("期望输出", { exact: true }).fill("90");
  await dialog.getByRole("button", { name: "保存", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await page
    .getByRole("combobox", { name: "版本", exact: true })
    .selectOption(fixture.version.id);
  await page.getByRole("button", { name: "运行用例集", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "失败 · 1", exact: true }),
  ).toBeVisible();
  expect(
    fixture.writes.find((row) => row.path.endsWith("/test-runs"))?.body
      .versionId,
  ).toBe(fixture.version.id);
});
test("operations show execution trace and interrupted batch receipts without exposing payloads", async ({
  page,
}) => {
  const fixture = await ruleFixture(page);
  await page.goto("/rule/operations");
  await page.getByRole("button", { name: "查询", exact: true }).click();
  await page.getByRole("button", { name: "详情", exact: true }).click();
  await expect(page.getByLabel("详情", { exact: true })).toHaveValue(
    /EXECUTED/,
  );
  await expect(page.getByLabel("详情", { exact: true })).not.toHaveValue(
    /"input"|"output"/,
  );
  await page.getByRole("button", { name: "批量迁移", exact: true }).click();
  await page
    .getByLabel("回执标识", { exact: true })
    .fill("9007199254740993201");
  await page.getByRole("button", { name: "查询", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: /RECOVERY_REQUIRED/ }),
  ).toBeVisible();
  await expect(
    page.getByText("待核对项目 · 124", { exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "运行资源", exact: true }).click();
  await page.getByRole("button", { name: "清理过期记录", exact: true }).click();
  await expect(page.getByRole("alertdialog")).toBeVisible();
  await page
    .getByRole("alertdialog")
    .getByRole("button", { name: "取消", exact: true })
    .click();
  expect(fixture.writes.some((row) => row.path === "/retention/cleanup")).toBe(
    false,
  );
  await page.getByRole("button", { name: "清理过期记录", exact: true }).click();
  await page
    .getByRole("alertdialog")
    .getByRole("button", { name: "确认", exact: true })
    .click();
  await expect(page.getByLabel("执行结果", { exact: true })).toHaveValue(
    /"executions": 1/,
  );
});
