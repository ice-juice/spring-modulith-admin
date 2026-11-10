export interface TreeNode { id: string; name: string; disabled?: boolean; children?: TreeNode[] }
export function descendants(node: TreeNode): TreeNode[] { return [node, ...(node.children || []).flatMap(descendants)] }
export function checkedState(node: TreeNode, selected: ReadonlySet<string>): boolean | 'indeterminate' {
  const all = descendants(node), count = all.filter(child => selected.has(child.id)).length
  return count === all.length ? true : count ? 'indeterminate' : false
}
export function toggleTree(nodes: TreeNode[], node: TreeNode, selected: string[], checked: boolean, cascade = true): string[] {
  const result = new Set(selected)
  if (!cascade) { if (!node.disabled) { if (checked) result.add(node.id); else result.delete(node.id) }; return [...result] }
  for (const child of descendants(node)) if (!child.disabled) { if (checked) result.add(child.id); else result.delete(child.id) }
  function parents(items: TreeNode[]) { for (const parent of items) { if (parent.children?.length) { parents(parent.children); if (!parent.disabled) { if (parent.children.some(child => descendants(child).some(n => result.has(n.id)))) result.add(parent.id); else result.delete(parent.id) } } } }
  parents(nodes)
  return [...result]
}
export function filterTree(nodes: TreeNode[], keyword: string): TreeNode[] {
  const search = keyword.trim().toLocaleLowerCase()
  if (!search) return nodes
  return nodes.flatMap(node => { if (node.name.toLocaleLowerCase().includes(search)) return [node]; const children = filterTree(node.children || [], search); return children.length ? [{ ...node, children }] : [] })
}
