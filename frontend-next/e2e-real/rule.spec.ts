import { expect, test } from "@playwright/test";
test("rule scenes and publication from the real admin shell", async ({
  page,
}) => {
  const password = process.env.SMA_E2E_ADMIN_PASSWORD;
  if (!password) throw new Error("Run through the isolated real E2E launcher.");
  await page.goto("/login");
  await page
    .getByRole("textbox", { name: "用户名", exact: true })
    .fill("admin");
  await page.getByLabel("密码", { exact: true }).fill(password);
  await page.getByRole("button", { name: "登录", exact: true }).click();
  await page.waitForURL((url) => url.pathname === "/");
  await page.locator('a[href="/rule/scenes"]').click();
  await page.getByRole("button", { name: "新增场景", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("编码", { exact: true }).fill("real_rule_scene");
  await dialog.getByLabel("名称", { exact: true }).fill("规则验收场景");
  await dialog.getByRole("button", { name: "保存", exact: true }).click();
  await expect(
    page.getByRole("cell", { name: "规则验收场景", exact: true }),
  ).toBeVisible();
  await page.locator('a[href="/rule/templates"]').click();
  await page.getByRole("button", { name: "新增模板", exact: true }).click();
  await dialog
    .getByRole("combobox", { name: "所属场景" })
    .selectOption({ label: "规则验收场景" });
  await dialog.getByLabel("编码", { exact: true }).fill("real_rule_template");
  await dialog.getByLabel("名称", { exact: true }).fill("规则验收模板");
  await dialog.getByRole("button", { name: "保存", exact: true }).click();
  await page.getByRole("textbox", { name: "规则表达式" }).focus();
  await page.keyboard.press("ControlOrMeta+a");
  await page.keyboard.type("true");
  await page.getByRole("tab", { name: "发布断言", exact: true }).click();
  await page
    .getByLabel("发布断言", { exact: true })
    .fill('[{"name":"accept","input":{},"params":{},"expectedOutput":true}]');
  await page.getByRole("button", { name: "保存草稿", exact: true }).click();
  await page.waitForURL(/\/rule\/templates\/\d+$/);
  await page.getByRole("button", { name: "模拟与解释", exact: true }).click();
  await page.getByRole("button", { name: "模拟执行", exact: true }).click();
  await expect(page.getByText("SUCCESS", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "发布", exact: true }).click();
  await page
    .getByRole("alertdialog")
    .getByRole("button", { name: "确认", exact: true })
    .click();
  await expect(page.getByText("状态 · 已发布", { exact: true })).toBeVisible();
  await page.locator('a[href="/rule/catalog"]').click();
  await page
    .getByRole("combobox", { name: "所属场景", exact: true })
    .selectOption({ label: "规则验收场景" });
  await expect(
    page.getByRole("heading", { name: "规则验收模板", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "选用此版本", exact: true }).click();
  await dialog.getByLabel("编码", { exact: true }).fill("unbound_instance");
  await dialog.getByLabel("名称", { exact: true }).fill("待绑定实例");
  await dialog.getByLabel("业务对象类型", { exact: true }).fill("UNINTEGRATED");
  await dialog.getByLabel("业务对象标识", { exact: true }).fill("sample");
  await dialog.getByRole("button", { name: "保存草稿", exact: true }).click();
  await expect(dialog.getByRole("alert")).toBeVisible();
  await expect(dialog.getByLabel("名称", { exact: true })).toHaveValue(
    "待绑定实例",
  );
});
