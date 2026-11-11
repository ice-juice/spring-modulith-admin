import { describe, expect, it } from "vitest";
import { documentFor, valuesFor, validInstant } from "../model";
import type { Scene } from "../types";
describe("rule template and parameter drafts", () => {
  it("copies scene contracts without sharing mutable authoring state or auto-publishing", () => {
    const scene: Scene = {
      code: "generic",
      name: "Generic",
      inputSchema: { fields: {} },
      parameterPolicy: {
        fields: { flag: { type: "BOOLEAN", required: true } },
      },
      outputSchema: { type: "BOOLEAN", required: true },
      allowedKinds: ["CONDITION"],
    };
    const doc = documentFor(scene);
    doc.parameterSchema.fields.flag!.required = false;
    expect(scene.parameterPolicy.fields.flag!.required).toBe(true);
    expect(doc.examples).toEqual([]);
    expect(doc.definition.kind).toBe("CONDITION");
  });
  it("retains explicit false defaults and requires an offset for effective times", () => {
    expect(
      valuesFor({
        fields: {
          flag: { type: "BOOLEAN", defaultValue: false },
          items: { type: "LIST" },
          nested: {
            type: "OBJECT",
            properties: { value: { type: "STRING", defaultValue: "default" } },
          },
        },
      }),
    ).toEqual({ flag: false, items: [], nested: { value: "default" } });
    expect(validInstant("2026-11-11T12:00:00+08:00")).toBe(true);
    expect(validInstant("2026-11-11T12:00:00")).toBe(false);
    expect(validInstant("")).toBe(true);
  });
});
