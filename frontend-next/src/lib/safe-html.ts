import DOMPurify from 'dompurify'
import { safeBrowserDestination } from './web-addresses'

const purifier = DOMPurify(window)
export function sanitizeRichHtml(value?: string | null): string {
  if (!purifier.isSupported) {
    const text = document.createElement('div')
    text.textContent = value || ''
    return text.innerHTML
  }
  const cleaned = purifier.sanitize(value || '', {
    USE_PROFILES: { html: true },
    FORBID_TAGS: ['form', 'input', 'button', 'iframe', 'style', 'img', 'video', 'audio', 'source'],
    FORBID_ATTR: ['style'],
  })
  const parsed = new DOMParser().parseFromString(cleaned, 'text/html')
  for (const link of parsed.querySelectorAll('a')) {
    if (!safeBrowserDestination(link.getAttribute('href'))) link.removeAttribute('href')
    link.setAttribute('rel', 'noopener noreferrer')
  }
  return parsed.body.innerHTML
}
