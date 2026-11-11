export interface FloatRect { x: number; y: number; width: number; height: number; collapsed: boolean }
export function clampRect(value: Partial<FloatRect>, width: number, height: number): FloatRect {
  const bound = (v: unknown, fallback: number, min: number, max: number) => Math.min(Math.max(typeof v === 'number' && Number.isFinite(v) ? v : fallback, min), max)
  const w = bound(value.width, 340, Math.min(280, width - 16), Math.max(1, width - 16))
  const h = bound(value.height, 440, Math.min(180, height - 16), Math.max(1, height - 16))
  return { width: w, height: h, x: bound(value.x, width - w - 16, 8, Math.max(8, width - w - 8)), y: bound(value.y, 96, 8, Math.max(8, height - (value.collapsed ? 48 : h) - 8)), collapsed: value.collapsed === true }
}
