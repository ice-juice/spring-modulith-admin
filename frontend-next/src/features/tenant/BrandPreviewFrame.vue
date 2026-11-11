<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import TenantLoginLayout from '@/components/tenant/TenantLoginLayout.vue'
import TenantBrandFooter from '@/components/tenant/TenantBrandFooter.vue'
import { localizedBrandText } from '@/lib/tenant-branding'
import { checkedPreviewMessage, type PreviewMessage } from './preview-protocol'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'
const value = ref<PreviewMessage>(), { t, locale } = useI18n()
const brand = computed(() => value.value?.preview.content)
const name = computed(() => value.value?.locale === 'en-US' && brand.value?.identity.englishName?.trim() ? brand.value.identity.englishName : brand.value?.identity.systemName || '')
function receive(event: MessageEvent) {
  if (event.source !== window.parent || event.origin !== location.origin) return
  if (event.data?.type === 'sma:brand-preview:clear') { value.value = undefined; return }
  const message = checkedPreviewMessage(event.data)
  if (!message) return
  value.value = message
  locale.value = message.locale
  document.documentElement.lang = message.locale
  document.documentElement.classList.toggle('dark', message.dark)
  document.documentElement.dataset.skin = message.preview.content.appearance.defaultSkin
}
onMounted(() => { window.addEventListener('message', receive); window.parent.postMessage({ type: 'sma:brand-preview:ready' }, location.origin) })
onUnmounted(() => window.removeEventListener('message', receive))
</script>
<template>
  <TenantLoginLayout v-if="brand && value" :template="brand.login.templateId" :name="name" :title="localizedBrandText(brand.login.title, value.locale)" :description="localizedBrandText(brand.login.description, value.locale)" :logo="value.preview.images[value.dark ? 'LOGO_DARK' : 'LOGO_LIGHT']" :background="value.preview.images.LOGIN_BACKGROUND || value.preview.legacyBackgroundUrl || undefined" :blur="brand.login.backgroundBlur" :accent="brand.appearance.primaryColor || undefined">
    <h2 class="text-3xl font-semibold tracking-tight">{{ t('auth.loginTitle') }}</h2><p class="mt-3 mb-8 text-sm text-muted-foreground">{{ t('tenant.previewOnly') }}</p>
    <div class="space-y-5"><div class="space-y-2"><Label for="preview-username">{{ t('auth.username') }}</Label><Input id="preview-username" disabled autocomplete="off" /></div><div class="space-y-2"><Label for="preview-password">{{ t('auth.password') }}</Label><Input id="preview-password" disabled type="password" autocomplete="off" /></div><Button class="w-full" disabled>{{ t('auth.signIn') }}</Button></div>
    <template #footer><TenantBrandFooter class="justify-center" :name="name" :legal="brand.legal" :links="brand.links" /></template>
  </TenantLoginLayout>
  <p v-else class="p-8 text-sm text-muted-foreground">{{ t('tenant.previewEmpty') }}</p>
</template>
