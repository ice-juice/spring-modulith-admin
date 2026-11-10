import { readFileSync, readdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { join } from 'node:path'
const root = fileURLToPath(new URL('../src/locales/', import.meta.url))
const locales = ['zh-CN', 'en-US']
const modules = readdirSync(join(root, locales[0]))
  .filter((file) => file.endsWith('.json'))
  .sort()
const errors = []
function flatten(value, prefix = '') {
  return Object.entries(value).flatMap(([key, value]) =>
    typeof value === 'object' && value !== null
      ? flatten(value, prefix + key + '.')
      : [[prefix + key, String(value)]],
  )
}
for (const locale of locales) {
  const files = readdirSync(join(root, locale))
    .filter((file) => file.endsWith('.json'))
    .sort()
  if (files.join() !== modules.join()) errors.push(locale + ': module files differ')
}
for (const module of modules) {
  const reference = new Map(
    flatten(JSON.parse(readFileSync(join(root, locales[0], module), 'utf8'))),
  )
  for (const locale of locales.slice(1)) {
    const translated = new Map(
      flatten(JSON.parse(readFileSync(join(root, locale, module), 'utf8'))),
    )
    if ([...reference.keys()].sort().join() !== [...translated.keys()].sort().join())
      errors.push(locale + '/' + module + ': keys differ')
    for (const [key, value] of reference) {
      const placeholders = (text) =>
        [...text.matchAll(/\{([^}]+)\}/g)]
          .map((match) => match[1])
          .sort()
          .join()
      if (placeholders(value) !== placeholders(translated.get(key) || ''))
        errors.push(locale + '/' + module + ': placeholders differ at ' + key)
    }
  }
}
if (errors.length) {
  console.error(errors.join('\n'))
  process.exit(1)
}
console.log(
  'i18n: ' +
    modules.length +
    ' modules, ' +
    locales.length +
    ' languages, matching keys and placeholders',
)
