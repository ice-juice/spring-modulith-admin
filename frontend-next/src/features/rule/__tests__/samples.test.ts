import { describe, expect, it } from "vitest";
import {
  sampleIds,
  sampleScene,
  sampleDocument,
  matchingSamples,
} from "../samples";
import { cloneExact, printExact } from "../exact";
const t = (key: string) => key;
describe("optional rule authoring samples", () => {
  it("provides four editing kinds, defaults, help and twelve executable assertions", () => {
    const documents = sampleIds.map((id) => {
      const scene = sampleScene(id, t);
      const document = sampleDocument(id, scene, t);
      expect(matchingSamples(scene)).toContain(id);
      expect(document.examples).toHaveLength(3);
      expect(Object.keys(document.parameterUi!)).toEqual(
        Object.keys(document.parameterSchema.fields),
      );
      expect(
        document.examples.every((example) =>
          Object.keys(document.parameterSchema.fields).every(
            (key) => key in example.params,
          ),
        ),
      ).toBe(true);
      return document;
    });
    expect(
      new Set(documents.map((document) => document.definition.kind)).size,
    ).toBe(4);
    expect(printExact(documents[0]!.examples[2])).toContain("19.99");
    expect(printExact(documents[0]!.examples[2])).toContain("16.99");
    expect(documents[3]!.definition.table!.defaultOutput).toEqual({
      payable: null,
      rate: 1,
    });
    expect(documents[3]!.outputSchema.properties!.payable!.required).toBe(
      false,
    );
  });
  it("accepts server-normalized fields but excludes changed contracts and unsupported kinds", () => {
    const scene = sampleScene("ratio", t);
    scene.inputSchema.fields.amount!.sensitive = false;
    scene.inputSchema.fields.amount!.properties = null;
    scene.inputSchema.fields.amount!.items = null;
    scene.inputSchema.fields.amount!.defaultValue = null;
    expect(matchingSamples(scene)).toEqual(["ratio"]);
    scene.inputSchema.fields.otherRequired = { type: "STRING", required: true };
    expect(matchingSamples(scene)).toEqual([]);
    expect(() => sampleDocument("ratio", scene, t)).toThrow(
      "SAMPLE_CONTRACT_MISMATCH",
    );
    const restricted = sampleScene("ratio", t);
    restricted.allowedKinds = ["FLOW"];
    expect(matchingSamples(restricted)).toEqual([]);
  });
  it("does not alias shared schemas, examples or another author's drafts", () => {
    const scene = sampleScene("ratio", t),
      original = cloneExact(scene);
    const one = sampleDocument("ratio", scene, t),
      two = sampleDocument("ratio", scene, t);
    one.parameterSchema.fields.ratio!.defaultValue = 0.5;
    one.examples[0]!.input.amount = 500;
    expect(printExact(scene)).toBe(printExact(original));
    expect(two.examples[0]!.input.amount).toBe(100);
    expect(printExact(two.parameterSchema.fields.ratio!.defaultValue)).toBe(
      "0.9",
    );
  });
});
