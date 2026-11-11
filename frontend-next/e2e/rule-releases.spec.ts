import { test, expect } from "@playwright/test";
import { ruleFixture } from "./rule-fixture";
test("publication uses a confirmed saved revision and history rollback records a new release", async ({
  page,
}) => {
  const fixture = await ruleFixture(page);
  await page.goto("/rule/templates/" + fixture.template.id);
  await page.getByRole("button", { name: "发布", exact: true }).click();
  await page
    .getByRole("alertdialog")
    .getByRole("button", { name: "确认", exact: true })
    .click();
  await expect(page.getByText("发布修订 · 1", { exact: true })).toBeVisible();
  const publish = fixture.writes.find((row) => row.path.endsWith("/publish"));
  expect(publish?.body.expectedRevision).toBe("1");
  expect(String(publish?.body.operationKey)).toMatch(/^[a-zA-Z0-9_-]{8,64}$/);
  await page.getByRole("button", { name: "版本历史", exact: true }).click();
  await page
    .getByRole("combobox", { name: "版本", exact: true })
    .selectOption(fixture.version.id);
  await page.getByRole("button", { name: "回滚", exact: true }).click();
  await page
    .getByRole("alertdialog")
    .getByRole("button", { name: "确认", exact: true })
    .click();
  expect(
    fixture.writes.find((row) => row.path.endsWith("/rollback"))?.body
      .versionId,
  ).toBe(fixture.version.id);
});
test("instance publication requires a trial and disable is confirmed", async ({
  page,
}) => {
  const fixture = await ruleFixture(page);
  fixture.instances.push({
    id: "instance1",
    kind: "INSTANCE",
    name: "待发布实例",
    code: "one",
    revision: "1",
    releaseRevision: "0",
    state: "DRAFT",
    activeVersionId: null,
    draft: {
      code: "one",
      name: "待发布实例",
      templateVersionId: fixture.version.id,
      params: { ratio: 0.9 },
      objectType: "PRODUCT",
      objectId: "product1",
      environment: "default",
      startAt: null,
      endAt: null,
    },
  });
  await page.goto("/rule/instances");
  await page.getByRole("button", { name: "详情", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "发布", exact: true }),
  ).toBeDisabled();
  await page.getByLabel("输入数据", { exact: true }).fill('{"amount":100}');
  await page.getByRole("button", { name: "试算", exact: true }).click();
  await expect(page.getByText("SUCCESS", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "发布", exact: true }).click();
  await page
    .getByRole("alertdialog")
    .getByRole("button", { name: "确认", exact: true })
    .click();
  await page.getByRole("button", { name: "停用", exact: true }).click();
  await page
    .getByRole("alertdialog")
    .getByRole("button", { name: "确认", exact: true })
    .click();
  await expect(
    page.getByRole("cell", { name: "已停用", exact: true }),
  ).toBeVisible();
  expect(
    fixture.writes.filter((row) => row.path.endsWith("/disable")),
  ).toHaveLength(1);
});
test("unknown publication outcomes block retries and missing permissions hide writes", async ({
  page,
}) => {
  const fixture = await ruleFixture(page);
  let count = 0;
  await page.route("**/api/rule/templates/*/publish", (route) => {
    count++;
    return route.fulfill({
      status: 504,
      json: { code: 504, message: "Gateway timeout", data: null },
    });
  });
  await page.goto("/rule/templates/" + fixture.template.id);
  await page.getByRole("button", { name: "发布", exact: true }).click();
  await page
    .getByRole("alertdialog")
    .getByRole("button", { name: "确认", exact: true })
    .click();
  await expect(
    page.getByText("提交结果尚未确认", { exact: false }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "发布", exact: true }),
  ).toBeDisabled();
  expect(count).toBe(1);
  fixture.host.me.permissions = fixture.host.me.permissions.filter(
    (permission) =>
      !permission.startsWith("rule:template:") ||
      permission === "rule:template:view",
  );
  await page.reload();
  await expect(
    page.getByRole("button", { name: "发布", exact: true }),
  ).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: "保存草稿", exact: true }),
  ).toHaveCount(0);
});

test('publication recovery after reload queries the original operation key and never republishes', async ({ page }) => {
  const fixture = await ruleFixture(page); let writes = 0, key = '', observed = false
  await page.route('**/api/rule/templates/*/publish', route => {
    writes++; key = route.request().postDataJSON().operationKey
    expect(route.request().headers()['idempotency-key']).toBe(key)
    fixture.template.revision = '2'; fixture.template.releaseRevision = '1'; fixture.template.activeVersionId = fixture.version.id
    return route.fulfill({ status: 503, json: { code: 503, message: 'Lost publication response' } })
  })
  await page.route('**/api/rule/templates/*/publications/*', route => {
    expect(new URL(route.request().url()).pathname.split('/').at(-1)).toBe(key)
    return observed ? route.fulfill({ json: { code: 0, data: { operationKey: key, release: { id: 'release-proof', assetId: fixture.template.id, action: 'PUBLISH', versionId: fixture.version.id, releaseRevision: '1' } } } })
      : route.fulfill({ status: 404, json: { code: 404, message: 'Not observed' } })
  })
  await page.goto('/rule/templates/' + fixture.template.id)
  await page.getByRole('button', { name: '发布', exact: true }).evaluate(button => { for (let i = 0; i < 20; i++) (button as HTMLButtonElement).click() })
  await expect(page.getByRole('alertdialog')).toHaveCount(1)
  await page.getByRole('alertdialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.getByRole('button', { name: '查询原操作结果', exact: true })).toHaveCount(1)
  await page.reload()
  await page.getByRole('button', { name: '查询原操作结果', exact: true }).click()
  await expect(page.getByRole('button', { name: '发布', exact: true })).toBeDisabled()
  expect(writes).toBe(1)
  observed = true; await page.getByRole('button', { name: '查询原操作结果', exact: true }).click()
  await expect(page.getByRole('button', { name: '查询原操作结果', exact: true })).toHaveCount(0)
  await expect(page.getByText('发布修订 · 1', { exact: true })).toBeVisible(); expect(writes).toBe(1)
})
