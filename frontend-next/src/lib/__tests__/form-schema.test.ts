import { expect, it } from 'vitest'
import { z } from 'zod'
import { formSchema } from '../form-schema'
it('maps nested Zod 4 issues to VeeValidate field paths and returns transformed valid data', async () => {
  const schema = formSchema<{ user: { name: string }; ids: string[] }>(z.object({ user: z.object({ name: z.string().trim().min(1, 'Required') }), ids: z.array(z.string()) }))
  expect((await schema.parse({ user: { name: ' ' }, ids: [] })).errors).toEqual([{ path: 'user.name', errors: ['Required'] }])
  expect(await schema.parse({ user: { name: ' Alice ' }, ids: ['9007199254740993123'] })).toEqual({ value: { user: { name: 'Alice' }, ids: ['9007199254740993123'] }, errors: [] })
})
