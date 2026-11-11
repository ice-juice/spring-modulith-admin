import { expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { createHash } from 'node:crypto'
import { iconCatalog, resolveIcon, searchIcons } from '../icons'
import AppIcon from '@/components/business/AppIcon.vue'

it('resolves old names without interpreting arbitrary markup, paths or URLs', () => {
  expect(resolveIcon('User')?.id).toBe('lucide:users')
  expect(resolveIcon('Setting')?.id).toBe('lucide:settings')
  expect(resolveIcon('Lock')?.id).toBe('lucide:shield')
  for (const name of ['https://example.com/a.png', '../../a', '<svg onload=alert(1)>', '3d:missing']) expect(resolveIcon(name)).toBeUndefined()
  expect(searchIcons('用户', 'lucide').length).toBeGreaterThan(3)
  expect(searchIcons('folder', '3d', 'files').length).toBeGreaterThan(1)
})

it('keeps the catalog unique, within the storage contract and bundled assets verified', () => {
  expect(new Set(iconCatalog.map(x => x.id)).size).toBe(iconCatalog.length)
  const manifest = JSON.parse(readFileSync('public/icons/3d/manifest.json', 'utf8'))
  expect(manifest.license).toBe('CC0-1.0')
  for (const entry of iconCatalog) {
    expect(entry.id.length).toBeLessThanOrEqual(64)
    expect(entry.id).toMatch(/^(lucide|3d):[a-z0-9-]+$/)
    if (entry.file) {
      const bytes = readFileSync('public/' + entry.file)
      expect(bytes.toString('ascii', 8, 12)).toBe('WEBP')
      expect(createHash('sha256').update(bytes).digest('hex')).toBe(manifest.icons.find((x: {id:string}) => x.id === entry.id).sha256)
    }
  }
})

it('falls back on missing images and restores the next valid selection', async () => {
  const wrapper = mount(AppIcon, { props: { name: '3d:folder' } })
  expect(wrapper.find('img').attributes('src')).toBe('/icons/3d/folder.webp')
  await wrapper.find('img').trigger('error')
  expect(wrapper.find('svg').exists()).toBe(true)
  await wrapper.setProps({ name: '3d:rocket' })
  expect(wrapper.find('img').exists()).toBe(true)
  await wrapper.setProps({ name: '<svg/>' })
  expect(wrapper.find('img').exists()).toBe(false)
  expect(wrapper.attributes('data-icon')).toBe('fallback')
})
