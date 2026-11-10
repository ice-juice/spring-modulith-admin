import { describe, expect, it } from 'vitest'
import { editorFor, payloadFor, resources, tableTree } from '../resources'
describe('management forms', () => {
  it('preserves snowflake ids through user relationships and keeps password outside user fields', () => {
    const resource = resources.users!
    const editor = editorFor(resource)
    editor.text.username = 'alice'
    editor.text.password = 'Secure@2026Password'
    editor.singles.departmentId = '9007199254740993'
    editor.multiples.roleIds = ['9007199254740995']
    const payload = payloadFor(resource, editor, false)
    expect(payload).toMatchObject({
      password: 'Secure@2026Password',
      user: { departmentId: '9007199254740993', roleIds: ['9007199254740995'] },
    })
    expect((payload.user as Record<string, unknown>).password).toBeUndefined()
    expect(payloadFor(resource, editor, true).password).toBeUndefined()
  })
  it('edits source names while keeping display translations separate', () => {
    const resource = resources.permissions!
    const editor = editorFor(resource, {
      id: '1',
      name: 'Translated',
      defaultName: 'Original',
      translations: { 'en-US': 'English' },
    })
    expect(editor.text.name).toBe('Original')
    expect(payloadFor(resource, editor, true).translations).toEqual({ 'en-US': 'English' })
  })
  it('keeps matches visible when their parents were filtered from search results', () => {
    expect(tableTree([{ id: '2', parentId: '1', name: 'Match' }])).toHaveLength(1)
    const tree = tableTree([
      { id: '1', parentId: '0' },
      { id: '2', parentId: '1' },
    ])
    expect(tree).toHaveLength(1)
    expect(tree[0]?.children).toHaveLength(1)
  })
})
