import { expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { i18n, setLocale } from '..'

it.each(['zh-CN', 'en-US'] as const)(
  'renders shell labels on the first %s frame without an async locale load',
  (locale) => {
    setLocale(locale)
    const shell = defineComponent({
      setup() {
        return useI18n()
      },
      template: `<div>{{ t('auth.lock') }} | {{ t('auth.changePassword') }} | {{ t('message.center') }}</div>`,
    })
    const wrapper = mount(shell, { global: { plugins: [i18n] } })
    expect(wrapper.text()).toBe(
      locale === 'zh-CN'
        ? '锁屏 | 修改密码 | 消息中心'
        : 'Lock screen | Change password | Message center',
    )
    wrapper.unmount()
  },
)
