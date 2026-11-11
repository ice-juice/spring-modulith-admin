<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ShieldAlert } from '@lucide/vue'
import { Button } from '@/components/ui/button'
const route = useRoute(), { t } = useI18n(), forbidden = computed(() => route.path === '/403'), statusCode = computed(() => forbidden.value ? '403' : '404')
</script>
<template><div class="softlight-surface flex min-h-[60vh] flex-col items-center justify-center gap-5 p-6 text-center"><ShieldAlert class="size-16 rounded-2xl bg-accent p-4 text-primary" /><p class="text-5xl font-semibold tracking-tight text-primary/80">{{ statusCode }}</p><h1 class="text-2xl font-semibold">{{ t(forbidden ? 'common.forbidden' : 'common.notFound') }}</h1><p v-if="forbidden" class="text-muted-foreground">{{ t('common.forbiddenHint') }}</p><Button as-child><RouterLink to="/">{{ t('common.backHome') }}</RouterLink></Button></div></template>
