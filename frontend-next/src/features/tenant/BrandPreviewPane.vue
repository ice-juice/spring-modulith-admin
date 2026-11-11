<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import type { BrandPreview } from './brands'
const props = defineProps<{ preview?: BrandPreview }>()
const frame = ref<HTMLIFrameElement>(), mobile = ref(false), dark = ref(false), english = ref(false), { t } = useI18n()
const width = computed(() => mobile.value ? 390 : 1280)
const source = import.meta.env.BASE_URL + 'brand-preview'
function send() {
  frame.value?.contentWindow?.postMessage(props.preview ? { type: 'sma:brand-preview', preview: JSON.parse(JSON.stringify(props.preview)), dark: dark.value, locale: english.value ? 'en-US' : 'zh-CN' } : { type: 'sma:brand-preview:clear' }, location.origin)
}
function ready(event: MessageEvent) {
  if (event.origin === location.origin && event.source === frame.value?.contentWindow && event.data?.type === 'sma:brand-preview:ready') send()
}
watch([() => props.preview, dark, english], send, { flush: 'post' })
onMounted(() => window.addEventListener('message', ready))
onUnmounted(() => window.removeEventListener('message', ready))
</script>
<template>
  <section class="space-y-3 rounded-xl border bg-card p-4" :aria-label="t('tenant.preview')">
    <div class="flex flex-wrap items-center gap-2"><Button variant="outline" :aria-pressed="mobile" @click="mobile = !mobile">{{ t(mobile ? 'tenant.mobile' : 'tenant.desktop') }}</Button><Button variant="outline" :aria-pressed="dark" @click="dark = !dark">{{ t(dark ? 'common.dark' : 'common.light') }}</Button><Button variant="outline" :aria-pressed="english" @click="english = !english">{{ t(english ? 'tenant.enUS' : 'tenant.zhCN') }}</Button></div>
    <p class="text-xs text-muted-foreground">{{ t('tenant.previewSize', { width }) }}</p>
    <div v-if="preview" class="max-w-full overflow-auto rounded-lg border bg-muted"><iframe ref="frame" :src="source" :title="t('tenant.preview')" :width="width" height="800" class="block max-w-none border-0" sandbox="allow-scripts allow-same-origin" referrerpolicy="no-referrer" /></div>
    <p v-else class="rounded-lg border border-dashed p-6 text-sm text-muted-foreground">{{ t('tenant.previewEmpty') }}</p>
  </section>
</template>
