import { describe, expect, it } from "vitest";
import {
  cloneExact,
  exactNumber,
  objectJson,
  parseExact,
  printExact,
} from "../exact";
describe("rule exact wire values", () => {
  it("round trips long ids and decimal lexemes without JS number conversion", () => {
    const text =
      '{"id":"9007199254740993123","amount":12345678901234567890.12345678,"max":9007199254740993}';
    expect(printExact(cloneExact(parseExact(text)))).toBe(text);
    expect(printExact({ ratio: exactNumber("0.100000000000000001") })).toBe(
      '{"ratio":0.100000000000000001}',
    );
  });
  it("rejects invalid numbers, arrays as input objects, and oversized input", () => {
    expect(() => exactNumber("NaN")).toThrow();
    expect(() => exactNumber("01")).toThrow();
    expect(() => objectJson("[]")).toThrow();
    expect(() => parseExact(" ".repeat(1048577))).toThrow();
    expect(objectJson('{"amount":1}')).toEqual({ amount: 1 });
  });
});
