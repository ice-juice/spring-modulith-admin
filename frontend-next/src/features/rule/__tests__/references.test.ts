import { describe, expect, it } from "vitest";
import { referencesFor } from "../references";
import { documentFor } from "../model";

function document() {
  return documentFor({
    code: "test",
    name: "test",
    allowedKinds: ["EXPRESSION"],
    inputSchema: {
      fields: {
        account: {
          type: "OBJECT",
          properties: {
            amount: {
              type: "DECIMAL",
              sensitive: true,
              defaultValue: "private-value",
            },
          },
        },
      },
    },
    parameterPolicy: { fields: { ratio: { type: "DECIMAL" } } },
    outputSchema: { type: "DECIMAL" },
  });
}
describe("rule contract reference", () => {
  it("includes nested contract paths and only server-advertised functions, without exposing defaults", () => {
    const result = referencesFor(document(), [
      "round",
      "round",
      "not-a-function()",
    ]);
    expect(result.map((entry) => entry.label)).toEqual([
      "input.account",
      "input.account.amount",
      "params.ratio",
      "round",
    ]);
    expect(result.at(-1)?.insertText).toBe("round(${1:value}, ${2:scale})");
    expect(JSON.stringify(result)).not.toContain("private-value");
  });
  it("reflects schema changes without retaining the previous document's fields", () => {
    const draft = document();
    referencesFor(draft, ["round"]);
    draft.inputSchema.fields = { total: { type: "INTEGER" } };
    expect(referencesFor(draft, []).map((entry) => entry.label)).toEqual([
      "input.total",
      "params.ratio",
    ]);
  });
  it("bounds reference size and does not invent paths into list elements", () => {
    const draft = document();
    draft.inputSchema.fields = {
      list: {
        type: "LIST",
        items: { type: "OBJECT", properties: { secret: { type: "STRING" } } },
      },
    };
    expect(
      referencesFor(draft, []).some((entry) => entry.label.includes("secret")),
    ).toBe(false);
    draft.inputSchema.fields = Object.fromEntries(
      Array.from({ length: 1000 }, (_, i) => [
        "field" + i,
        { type: "STRING" as const },
      ]),
    );
    expect(referencesFor(draft, [])).toHaveLength(256);
  });
});
