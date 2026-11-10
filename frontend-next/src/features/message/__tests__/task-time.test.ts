import { expect, it } from 'vitest'
import { taskForm, taskInput, type Task } from '../tasks'
it('preserves an existing exact instant during title-only edits in a repeated DST hour', () => {
  const row = { id: '1', title: 'Original', dueAt: '2026-11-01T06:30:00Z', type: 1, priority: 1, userId: '101', source: {} } as Task
  const form = taskForm(row, 'America/New_York')
  form.title = 'Renamed'
  const input = taskInput(form, 'America/New_York')
  expect(input.dueAt).toBe(row.dueAt)
  expect(input).not.toHaveProperty('originalDueAt')
  expect(input).not.toHaveProperty('originalLocalDueAt')
})
