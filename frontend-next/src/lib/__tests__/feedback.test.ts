import { describe, expect, it } from 'vitest'
import { confirmAction, settleConfirmation, confirmation } from '../feedback'
describe('confirmation ownership', () => {
  it('cancels a replaced confirmation and settles each request exactly once', async () => {
    const first = confirmAction('first', 'title'), second = confirmAction('second', 'title', true)
    expect(await first).toBe(false)
    expect(confirmation.value?.description).toBe('second')
    settleConfirmation(true)
    expect(await second).toBe(true)
    settleConfirmation(false)
    expect(confirmation.value).toBeUndefined()
  })
})
