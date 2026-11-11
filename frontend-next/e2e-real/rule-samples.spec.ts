import { expect, test } from "@playwright/test";
import { sampleIds } from "../src/features/rule/samples";

for (const sample of sampleIds)
  test(`sample ${sample} saves, simulates three cases and publishes with real assertion gates`, async ({
    page,
  }) => {
    test.setTimeout(60000);
    const password = process.env.SMA_E2E_ADMIN_PASSWORD;
    if (!password)
      throw new Error("Run through the isolated real E2E launcher.");
    const titles = {
      ratio: "比例折扣（表达式）",
      eligibility: "会员资格（条件）",
      reduction: "满额减免（流程）",
      tiers: "阶梯优惠（决策表）",
    };
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
      .selectOption(sample);
    await dialog.getByRole("button", { name: "保存", exact: true }).click();
    await expect(dialog).toHaveCount(0);
    await expect(
      page.getByRole("cell", { name: titles[sample], exact: true }),
    ).toBeVisible();
    await page.goto("/rule/templates");
    await page.getByRole("button", { name: "新增模板", exact: true }).click();
    await dialog
      .getByRole("combobox", { name: "所属场景", exact: true })
      .selectOption({ label: titles[sample] });
    await dialog
      .getByRole("combobox", { name: "示例模板", exact: true })
      .selectOption(sample);
    await dialog.getByLabel("编码", { exact: true }).fill("verified_" + sample);
    await dialog.getByLabel("名称", { exact: true }).fill(titles[sample]);
    await dialog.getByRole("button", { name: "保存", exact: true }).click();
    const creation = page.waitForResponse(
      (response) =>
        response.url().endsWith("/api/rule/templates") &&
        response.request().method() === "POST",
      { timeout: 15000 },
    );
    await page.getByRole("button", { name: "保存草稿", exact: true }).click();
    const created = await (await creation).json();
    expect(created.code, created.message).toBe(0);
    await page.waitForURL(/\/rule\/templates\/\d+$/);
    await expect(page.getByText("状态 · 草稿", { exact: true })).toBeVisible();
    await page.getByRole("button", { name: "模拟与解释", exact: true }).click();
    for (let index = 0; index < 3; index++) {
      await page
        .getByRole("combobox", { name: "示例输入", exact: true })
        .selectOption(String(index));
      await page
        .getByRole("button", { name: "载入示例数据", exact: true })
        .click();
      await page.getByRole("button", { name: "模拟执行", exact: true }).click();
      await expect(
        page.getByText(
          sample === "eligibility" && index > 0 ? "NOT_MATCHED" : "SUCCESS",
          { exact: true },
        ),
      ).toBeVisible();
    }
    await page.getByRole("button", { name: "发布", exact: true }).click();
    await page
      .getByRole("alertdialog")
      .getByRole("button", { name: "确认", exact: true })
      .click();
    // Publishing runs every saved assertion on the server, including exact output values.
    await expect(
      page.getByText("状态 · 已发布", { exact: true }),
    ).toBeVisible();
  });
