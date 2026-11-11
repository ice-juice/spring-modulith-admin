import type { Page } from "@playwright/test";
import { fullApi } from "./full-api";
export async function ruleFixture(
  page: Page,
  origin = `http://127.0.0.1:${process.env.SMA_RULE_TEST_PORT || 5174}`,
) {
  const host = await fullApi(page, origin),
    writes: { path: string; body: Record<string, unknown>; raw: string }[] = [],
    unmatched: string[] = [];
  host.me.permissions.push(
    ...[
      "scene",
      "template",
      "catalog",
      "instance",
      "execution",
      "test",
      "batch",
      "maintenance",
      "release",
      "simulation",
    ].flatMap((resource) =>
      [
        "view",
        "edit",
        "publish",
        "disable",
        "run",
        "migrate",
        "clean",
        "lifecycle",
        "rollback",
      ].map((action) => `rule:${resource}:${action}`),
    ),
  );
  host.me.menus[0]!.children.push(
    ...[
      ["scenes", "规则场景", "rule:scene:view"],
      ["templates", "规则模板", "rule:template:view"],
      ["catalog", "模板目录", "rule:catalog:view"],
      ["instances", "使用实例", "rule:instance:view"],
      ["operations", "运行与治理", "rule:execution:view"],
    ].map(([id, name, permission]) => ({
      id: "rule-" + id,
      name: name!,
      path: "/rule/" + id,
      permission: permission!,
      parentId: "org",
      type: "MENU",
      icon: "Grid",
      openMode: "ROUTE",
      children: [],
    })),
  );
  const schema = {
      fields: { amount: { type: "DECIMAL", required: true } },
      additionalProperties: false,
    },
    params = {
      fields: {
        ratio: {
          type: "DECIMAL",
          required: true,
          minimum: 0,
          maximum: 1,
          scale: 4,
        },
      },
      additionalProperties: false,
    };
  const scene = {
    id: "9007199254740993101",
    kind: "SCENE",
    code: "discount",
    name: "商品折扣",
    revision: "1",
    releaseRevision: "0",
    state: "DRAFT",
    activeVersionId: null,
    draft: {
      code: "discount",
      name: "商品折扣",
      inputSchema: schema,
      parameterPolicy: params,
      outputSchema: { type: "DECIMAL", required: true },
      allowedKinds: ["EXPRESSION", "CONDITION", "FLOW", "DECISION_TABLE"],
    },
  };
  const doc = {
    formatVersion: 1,
    inputSchema: schema,
    parameterSchema: params,
    outputSchema: { type: "DECIMAL", required: true },
    definition: {
      kind: "EXPRESSION",
      expression: "round(input.amount * params.ratio, 2)",
    },
    parameterUi: {
      ratio: {
        label: "折后比例",
        help: "0.9 表示九折",
        group: "折扣",
        order: 1,
      },
    },
    examples: [
      {
        name: "九折",
        input: { amount: 100 },
        params: { ratio: 0.9 },
        expectedOutput: 90,
      },
    ],
    intervalConstraints: [],
  };
  const template = {
    id: "9007199254740993102",
    kind: "TEMPLATE",
    code: "discount_template",
    name: "会员折扣",
    revision: "1",
    releaseRevision: "0",
    state: "DRAFT",
    activeVersionId: null as string | null,
    draft: doc,
  };
  const templates = [template],
    scenes = [scene];
  const version = {
    id: "9007199254740993103",
    assetId: template.id,
    revision: "1",
    dependencyId: scene.id,
    contentHash: "fixed-hash",
    document: doc,
    testRunId: "report1",
    caseSetHash: "cases-hash",
  };
  const instances: {
    id: string;
    kind: string;
    code: string;
    name: string;
    revision: string;
    releaseRevision: string;
    state: string;
    activeVersionId: string | null;
    draft: Record<string, unknown>;
  }[] = [];
  const cases: {
    id: string;
    revision: string;
    testcase: Record<string, unknown>;
  }[] = [];
  const report = {
    id: "9007199254740993200",
    versionId: version.id,
    total: 1,
    passed: false,
    results: [
      {
        caseId: "1",
        name: "折扣断言",
        actualStatus: "SUCCESS",
        passed: false,
        changed: true,
        problems: [],
      },
    ],
  };
  await page.addInitScript(() => {
    sessionStorage.setItem("sma.access-token", "isolated-rule-test-token");
    localStorage.setItem("sma.locale", "zh-CN");
  });
  await page.route(origin + "/api/rule/**", async (route) => {
    const request = route.request(),
      url = new URL(request.url()),
      path = url.pathname.slice("/api/rule".length),
      method = request.method();
    const body = request.postDataJSON() || {},
      raw = request.postData() || "";
    if (method !== "GET") writes.push({ path, body, raw });
    const ok = (data: unknown) =>
      route.fulfill({ json: { code: 0, error: null, message: "ok", data } });
    if (path === "/scenes")
      return method === "GET"
        ? ok(scenes)
        : ok({ ...scene, id: "new_scene", ...body, draft: body });
    if (path === "/catalog/scenes") return ok(scenes);
    if (path === "/catalog/entries")
      return ok([{ code: template.code, name: template.name, version }]);
    if (path === "/catalog") return ok([version]);
    if (path.endsWith("/testcases")) {
      if (method === "GET") return ok(cases);
      const item = { id: "1", revision: "1", testcase: body.testcase };
      cases.push(item);
      return ok(item);
    }
    if (path.endsWith("/test-runs")) return ok(method === "GET" ? [] : report);
    if (path === "/migration-batches")
      return ok({
        id: "9007199254740993201",
        state: "COMPLETED_WITH_ERRORS",
        total: 2,
        allPassed: false,
        results: [
          { instanceId: "123", passed: true, newRevision: "2" },
          { instanceId: "124", passed: false, error: "rule.error.conflict" },
        ],
        pendingInstanceIds: [],
      });
    if (path.startsWith("/migration-batches/"))
      return ok({
        id: "9007199254740993201",
        state: "RECOVERY_REQUIRED",
        total: 2,
        allPassed: false,
        results: [{ instanceId: "123", passed: true, newRevision: "2" }],
        pendingInstanceIds: ["124"],
      });
    if (path === "/executions/search")
      return ok([
        {
          id: "9007199254740993202",
          instanceId: "123",
          templateVersionId: version.id,
          releaseRevision: "2",
          status: "SUCCESS",
          durationMicros: "234",
          createdAt: "2026-11-11T08:00:00Z",
        },
      ]);
    if (path.includes("/executions/"))
      return ok({
        id: "9007199254740993202",
        status: "SUCCESS",
        trace: [{ id: "result", outcome: "EXECUTED" }],
        problems: [],
      });
    if (path === "/functions")
      return ok([
        "min",
        "max",
        "abs",
        "round",
        "size",
        "contains",
        "intervalValue",
      ]);
    if (path === "/resources") return ok({ expressions: 2, plans: 1 });
    if (path === "/retention/cleanup")
      return ok({ executions: 1, testRuns: 0 });
    if (path.endsWith("/upgrade-preview"))
      return ok({
        instanceId: instances[0]?.id,
        baseRevision: body.expectedRevision,
        canMigrate: true,
        proposed: instances[0]?.draft,
        changes: [],
        comparisons: [
          {
            sample: 0,
            changed: true,
            before: { status: "SUCCESS", output: 90 },
            after: { status: "SUCCESS", output: 81 },
          },
        ],
        problems: [],
      });
    if (path.endsWith("/upgrade-draft")) {
      const row = instances[0]!;
      row.revision = "2";
      row.draft = { ...row.draft, templateVersionId: body.templateVersionId };
      return ok(row);
    }
    if (path.endsWith("/dependency-impact"))
      return ok({
        templateId: template.id,
        assets: [],
        scanned: 1,
        truncated: false,
      });
    if (path === "/instances") {
      if (method === "GET") return ok(instances);
      const instance = {
        id: "9007199254740993104",
        kind: "INSTANCE",
        code: body.code,
        name: body.name,
        revision: "1",
        releaseRevision: "0",
        state: "DRAFT",
        activeVersionId: null,
        draft: body,
      };
      instances.push(instance);
      return ok(instance);
    }
    if (path.startsWith("/instances/") && path.endsWith("/template"))
      return ok(version);
    const instanceRow = instances.find(
      (row) =>
        path === "/instances/" + row.id ||
        path === "/instances/" + row.id + "/draft",
    );
    if (instanceRow) {
      if (method === "PUT") {
        instanceRow.draft = body.instance;
        instanceRow.name = body.instance.name;
        instanceRow.revision = String(Number(instanceRow.revision) + 1);
      }
      return ok(instanceRow);
    }
    if (path.startsWith("/scenes/"))
      return ok({ ...scene, revision: "2", draft: body.scene });
    if (path === "/templates") {
      if (method === "GET") return ok(templates);
      const row = {
        ...template,
        id: "new_template",
        code: body.code,
        name: body.name,
        draft: body.document,
      };
      templates.push(row);
      return ok(row);
    }
    if (path.endsWith("/scene")) return ok(scene);
    const selected = templates.find(
      (row) =>
        path === "/templates/" + row.id ||
        path === "/templates/" + row.id + "/draft",
    );
    if (selected) {
      if (method === "PUT") {
        Object.assign(selected, {
          draft: body.template.document,
          name: body.template.name,
          revision: String(Number(selected.revision) + 1),
        });
      }
      return ok(selected);
    }
    if (path.startsWith("/assets/") && path.endsWith("/versions"))
      return ok(
        instances.some((row) => path.includes(row.id))
          ? [
              {
                ...version,
                id: "9007199254740993105",
                assetId: instances[0]?.id,
                document: instances[0]?.draft,
              },
            ]
          : [version],
      );
    if (path === "/validate") return ok({ valid: true, problems: [] });
    if (path === "/simulations")
      return ok({
        executionId: "simulation1",
        status: "SUCCESS",
        output: 90,
        trace: [
          {
            id: "expression:1",
            kind: "EXPRESSION",
            evaluated: true,
            line: 1,
            column: 1,
            outcome: "EVALUATED",
          },
        ],
        problems: [],
      });
    if (path.endsWith("/trial") || path.endsWith("/execute"))
      return ok({
        executionId: "exec1",
        status: "SUCCESS",
        output: 90,
        trace: [],
        problems: [],
      });
    if (
      path.endsWith("/publish") ||
      path.endsWith("/disable") ||
      path.endsWith("/rollback")
    ) {
      const instance = instances.find((row) => path.includes(row.id)),
        asset = instance || template;
      asset.activeVersionId = instance ? "9007199254740993105" : version.id;
      asset.state = path.endsWith("/disable") ? "DISABLED" : "PUBLISHED";
      asset.revision = String(Number(asset.revision) + 1);
      asset.releaseRevision = String(Number(asset.releaseRevision) + 1);
      return ok({
        id: "release1",
        assetId: asset.id,
        versionId: asset.activeVersionId,
        releaseRevision: asset.releaseRevision,
        action: "PUBLISH",
      });
    }
    unmatched.push(method + " " + path);
    return route.fulfill({
      status: 501,
      json: { code: 501, message: "Missing rule fixture", data: null },
    });
  });
  return { host, writes, unmatched, scene, template, version, doc, instances };
}
