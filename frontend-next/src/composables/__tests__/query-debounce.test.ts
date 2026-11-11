import { effectScope } from 'vue'
import { afterEach, expect, it, vi } from 'vitest'
import { useQueryDebounce } from '../useQueryDebounce'
afterEach(() => vi.useRealTimers())
it('waits for 300ms of idle text, coalesces input, and sends explicit search immediately without a trailing request', async () => {
  vi.useFakeTimers()
  const scope = effectScope(), search = vi.fn(), query = scope.run(() => useQueryDebounce(search))!
  query.schedule(); await vi.advanceTimersByTimeAsync(200); query.schedule(); await vi.advanceTimersByTimeAsync(299)
  expect(search).not.toHaveBeenCalled(); await vi.advanceTimersByTimeAsync(1); expect(search).toHaveBeenCalledTimes(1)
  query.schedule(); query.trigger(); expect(search).toHaveBeenCalledTimes(2)
  await vi.advanceTimersByTimeAsync(500); expect(search).toHaveBeenCalledTimes(2); scope.stop()
})
it('does not search during Chinese composition or submit on IME Enter, and cancels on disposal', async () => {
  vi.useFakeTimers()
  const scope = effectScope(), search = vi.fn(), query = scope.run(() => useQueryDebounce(search))!
  query.schedule(); query.compositionStart(); query.schedule(); query.trigger(); await vi.advanceTimersByTimeAsync(1000)
  const event = new KeyboardEvent('keydown', { key: 'Enter', isComposing: true, cancelable: true })
  query.keydown(event); expect(event.defaultPrevented).toBe(true); expect(search).not.toHaveBeenCalled()
  query.compositionEnd(); await vi.advanceTimersByTimeAsync(300); expect(search).toHaveBeenCalledOnce()
  query.schedule(); scope.stop(); await vi.advanceTimersByTimeAsync(300); expect(search).toHaveBeenCalledOnce()
})
