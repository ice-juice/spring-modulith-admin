export interface DataColumn { key: string; title: string; width?: number; sortable?: boolean; align?: 'left' | 'right' }
export function positiveInteger(value: unknown, fallback: number) { const n = Number(value); return Number.isSafeInteger(n) && n > 0 ? n : fallback }
export function pageQuery(index: number, size: number) { return { page: positiveInteger(index + 1, 1), size: Math.min(200, positiveInteger(size, 20)) } }
