import { OperationRegistry } from '@/lib/operation-registry';
import { effectScope } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
vi.mock("vue-i18n", async (importOriginal) => ({
  ...await importOriginal<typeof import("vue-i18n")>(),
  useI18n: () => ({ t: (key: string) => key }),
}));
import { useRuleTask } from "../useRuleTask";
import { ApiError } from "@/api/http";
import * as api from '../api';
import * as http from '@/api/http';
import { exactNumber, printExact } from '../exact';

beforeEach(() => localStorage.clear());
afterEach(() => vi.restoreAllMocks());
const owner = { scope: ['https://rule.test/api', '1', 'admin', '1'] as const, session: 'rule-test-session' };
const governed = () => new OperationRegistry(() => owner, () => localStorage);

describe("rule request generation guards", () => {
  it("ignores a success for a changed simulation snapshot and releases its busy state", async () => {
    const scope = effectScope(),
      task = scope.run(() => useRuleTask(new OperationRegistry(() => null, () => localStorage)))!;
    let current = true,
      complete!: (value: number) => void;
    const apply = vi.fn();
    const pending = task.run(
      () =>
        new Promise<number>((resolve) => {
          complete = resolve;
        }),
      apply,
      false,
      () => current,
    );
    current = false;
    complete(90);
    expect(await pending).toBe(false);
    expect(apply).not.toHaveBeenCalled();
    expect(task.busy.value).toBe(false);
    scope.stop();
  });
  it("does not display a late error or mark a new snapshot uncertain", async () => {
    const scope = effectScope(),
      task = scope.run(() => useRuleTask(new OperationRegistry(() => null, () => localStorage)))!;
    let current = true,
      fail!: (cause: unknown) => void;
    const pending = task.run(
      () =>
        new Promise((_, reject) => {
          fail = reject;
        }),
      vi.fn(),
      true,
      () => current,
    );
    current = false;
    fail(new ApiError(504, null, "obsolete failure"));
    expect(await pending).toBe(false);
    expect(task.error.value).toBe("");
    expect(task.uncertain.value).toBe(false);
    expect(task.busy.value).toBe(false);
    scope.stop();
  });
});

it('reserves a rule asset before confirmation and captures exact numbers and the original revision', async () => {
  const registry = governed(), scope = effectScope(), first = scope.run(() => useRuleTask(registry))!, second = scope.run(() => useRuleTask(registry))!
  let confirm!: () => void
  const gate = new Promise<void>(resolve => { confirm = resolve }), body = { expectedRevision: '9007199254740993999', amount: exactNumber('9007199254740993.12345') }
  const send = vi.spyOn(api, 'ruleWrite').mockResolvedValue({ id: 'asset' })
  const pending = first.command({ operation: 'rule.template-save', resource: 'rule.assets.1', path: '/templates/1/draft', body, confirm: async () => { await gate; return true } }, vi.fn())
  body.expectedRevision = 'next-version'; body.amount = exactNumber('2')
  expect(await second.command({ operation: 'rule.publish', resource: 'rule.assets.1', path: '/templates/1/publish', body: {} }, vi.fn())).toBe(false)
  expect(send).not.toHaveBeenCalled(); confirm(); expect(await pending).toBe(true)
  expect(printExact(send.mock.calls[0]![1])).toBe('{"expectedRevision":"9007199254740993999","amount":9007199254740993.12345}')
  expect(send.mock.calls[0]![3]?.idempotencyKey).toMatch(/^[a-f0-9-]{36}$/); scope.stop()
})

it('keeps uncertainty after an ordinary data refresh and only releases on the original LOCAL receipt', async () => {
  const registry = governed(), scope = effectScope(), task = scope.run(() => useRuleTask(registry))!
  vi.spyOn(api, 'ruleWrite').mockRejectedValue(new ApiError(503, null, 'lost'))
  await task.command({ operation: 'rule.template-save', resource: 'rule.assets.1', path: '/templates/1/draft', body: { content: 'private rule input' } }, vi.fn())
  const key = registry.list()[0]!.key
  await task.run(async () => 'current data', () => { task.uncertain.value = false })
  expect(task.uncertain.value).toBe(true)
  expect(JSON.stringify({ ...localStorage })).not.toContain('private rule input')
  vi.spyOn(http, 'post').mockImplementation(async (path, body) => {
    expect(path).toBe('/common/requests/lookup'); expect(body).toEqual({ key })
    return { operationId: 'rule-local-receipt', operation: 'rule.template-save', state: 'SUCCEEDED', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:01Z' }
  })
  expect(await task.check()).toBe('SUCCEEDED'); expect(task.uncertain.value).toBe(false); scope.stop()
})

it('does not pass a minimal completion receipt to a rule DTO consumer', async () => {
  const registry = governed(), scope = effectScope(), task = scope.run(() => useRuleTask(registry))!
  const apply = vi.fn(), completed = vi.fn()
  vi.spyOn(api, 'ruleWrite').mockRejectedValue(new ApiError(409, 'common.request.completed', 'completed', { operationId: 'rule-receipt', operation: 'rule.create', state: 'SUCCEEDED', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:01Z' }))
  expect(await task.command({ operation: 'rule.create', resource: 'rule.templates.new', path: '/templates', body: {}, completed }, apply)).toBe(false)
  expect(apply).not.toHaveBeenCalled(); expect(completed).toHaveBeenCalledOnce(); expect(registry.list()).toEqual([]); scope.stop()
})
