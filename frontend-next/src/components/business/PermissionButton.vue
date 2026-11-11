<script setup lang="ts">
import { computed } from 'vue'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/stores/auth'
import { resolveIcon } from '@/lib/icons'
import AppIcon from './AppIcon.vue'
defineOptions({ inheritAttrs: false })
const props = defineProps<{ permission: string | string[] }>()
const auth = useAuth()
const configured = computed(() => {
  const keys = Array.isArray(props.permission) ? props.permission : [props.permission]
  for (const key of keys) {
    if (!auth.hasPermission(key)) continue
    const name = auth.user?.permissionIcons?.[key]
    if (name === 'none' || resolveIcon(name)) return name
  }
  return undefined
})
</script>
<template>
  <Button v-if="auth.hasPermission(permission)" v-bind="$attrs">
    <AppIcon v-if="configured && configured !== 'none'" :name="configured" :size="18" />
    <span class="inline-flex min-w-0 items-center justify-center gap-2" :class="{ '[&>svg]:hidden': configured }"><slot /></span>
  </Button>
</template>
