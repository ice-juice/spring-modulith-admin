import { expect, it } from 'vitest'
import { taskForm, taskInput, activeTask, type Task } from '../tasks'
it('round-trips the user-zone deadline and retains external identifiers as strings', () => {
  const row = {
    id: '9007199254740993',
    userId: '9007199254740995',
    title: 'Task',
    type: 4,
    priority: 2,
    dueAt: '2026-07-01T16:00:00Z',
    content: 'Details',
    url: '/iam/users',
    source: {
      system: 'oa',
      messageId: '9007199254740997',
      requestId: 'flow',
      url: 'https://example.test',
      senderKey: 'a',
      receiverKey: 'b',
    },
  } as Task
  const form = taskForm(row, 'America/New_York')
  expect(form.dueAt).toBe('2026-07-01T12:00:00')
  const input = taskInput(form, 'America/New_York')
  expect(input.dueAt).toBe('2026-07-01T16:00:00.000Z')
  expect(input.userId).toBe(row.userId)
  expect(input.source?.messageId).toBe('9007199254740997')
  form.source.messageId = 'changed'
  expect(row.source.messageId).toBe('9007199254740997')
  form.type = 1
  expect(taskInput(form, 'America/New_York')).toMatchObject({ content: null, url: null })
  expect([1, 2, 5].every(activeTask)).toBe(true)
  expect([3, 4].some(activeTask)).toBe(false)
})
