import { describe, expect, it } from 'vitest'
import { checkedState, filterTree, toggleTree, type TreeNode } from '../tree'
const nodes: TreeNode[] = [{ id: 'menu', name: 'Organization', children: [{ id: 'read', name: 'Read users' }, { id: 'write', name: 'Edit users' }, { id: 'locked', name: 'Protected grant', disabled: true }] }, { id: 'files', name: 'Files' }]
describe('authorization tree state', () => {
  it('keeps checked nodes outside the search and disabled grants intact', () => {
    const filtered = filterTree(nodes, 'Read')
    expect(filtered[0]?.children?.map(n => n.id)).toEqual(['read'])
    const selected = toggleTree(nodes, filtered[0]!.children![0]!, ['write', 'locked', 'files'], true)
    expect(new Set(selected)).toEqual(new Set(['menu', 'read', 'write', 'locked', 'files']))
    expect(toggleTree(nodes, nodes[0]!, selected, false)).toContain('locked')
    expect(toggleTree(nodes, nodes[0]!, selected, false)).toContain('files')
  })
  it('preserves big string IDs and computes partial ancestors', () => {
    const root = { id: '9007199254740993123', name: 'Root', children: [{ id: '9007199254740993124', name: 'Child' }] }
    expect(checkedState(root, new Set([root.children[0]!.id]))).toBe('indeterminate')
    expect(toggleTree([root], root.children[0]!, [], true)).toEqual([root.children[0]!.id, root.id])
  })
})
