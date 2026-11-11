import { parse, stringify, LosslessNumber } from "lossless-json";
// Preserve decimal lexemes and integers beyond JS's safe range. IDs from the server stay strings.
export function parseExact<T = unknown>(text: string): T {
  if (text.length > 1048576) throw new Error("JSON_LIMIT");
  return parse(text, undefined, {
    parseNumber: (value) =>
      /^-?\d+$/.test(value) && Number.isSafeInteger(Number(value))
        ? Number(value)
        : new LosslessNumber(value),
  }) as T;
}
export function printExact(value: unknown, pretty = false): string {
  return stringify(value, undefined, pretty ? 2 : undefined) ?? "null";
}
export function cloneExact<T>(value: T): T {
  return parseExact<T>(printExact(value));
}
export function objectJson(text: string): Record<string, unknown> {
  const value = parseExact(text);
  if (
    !value ||
    typeof value !== "object" ||
    Array.isArray(value) ||
    value instanceof LosslessNumber
  )
    throw new Error("JSON_OBJECT_REQUIRED");
  return value as Record<string, unknown>;
}
export function exactNumber(value: string): LosslessNumber {
  if (!/^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?$/.test(value))
    throw new Error("INVALID_NUMBER");
  return new LosslessNumber(value);
}
