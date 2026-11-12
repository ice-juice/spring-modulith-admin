<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ListChecks, CircleAlert } from '@lucide/vue'
import { useAuth } from '@/stores/auth'
import { useOperationPanel } from '@/composables/useOperationPanel'
import { permittedRecoveryPath } from '@/lib/operation-recovery'
import type { OperationRecord } from '@/lib/operation-registry'
import { useShelf } from '@/features/workspace/shelf'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription, SheetTrigger } from '@/components/ui/sheet'
const { t } = useI18n(), auth = useAuth(), router = useRouter(), panel = useOperationPanel(), open = ref(false)
const { rows, count, attentionCount, error, result, querying, unavailable } = panel
const areas = new Set(['iam', 'config', 'message', 'file', 'workspace', 'tenant', 'monitor', 'rule', 'extension', 'openapi', 'auth'])
function area(resource: string) { const prefix = resource.split('.')[0] || ''; return areas.has(prefix) ? prefix : 'other' }
watch(() => auth.user, user => { if (!user || user.locked) open.value = false }, { deep: true, flush: 'sync' })
function destination(row: OperationRecord) { return auth.user && !auth.user.locked ? permittedRecoveryPath(row, path => router.resolve(path), permission => auth.hasPermission(permission)) : undefined }
async function recover(key: string) {
  const row = rows.value.find(row => row.key === key), path = row && destination(row)
  if (!path) return
  open.value = false
  try { await router.push(path) } catch { error.value = 'common.operationPanel.navigationFailed' }
}
function recoverShelf(key: string) {
  if (!auth.user || auth.user.locked || !rows.value.some(row => row.key === key && row.resource.startsWith('workspace.shelf.'))) return
  open.value = false; void useShelf().show()
}
defineExpose({ count, attentionCount, unavailable, show: () => { if (auth.user && !auth.user.locked) open.value = true } })
</script>

<template>
  <Sheet v-if="auth.user && !auth.user.locked" v-model:open="open">
    <SheetTrigger as-child><Button variant="ghost" size="icon" class="relative" :aria-label="t('common.operationPanel.title')" :title="t('common.operationPanel.title')"><ListChecks class="size-4" /><span v-if="count || unavailable" class="absolute top-0 right-0 min-w-4 rounded-full bg-primary px-1 text-[10px] leading-4 text-primary-foreground"><CircleAlert v-if="unavailable" class="size-3" /><template v-else>{{ count }}</template></span></Button></SheetTrigger>
    <SheetContent class="w-full max-w-[100vw] sm:max-w-lg" data-operation-panel>
      <SheetHeader class="shrink-0 pr-10"><SheetTitle>{{ t('common.operationPanel.title') }}</SheetTitle><SheetDescription>{{ t('common.operationPanel.description') }}</SheetDescription></SheetHeader>
      <div class="min-h-0 flex-1 overflow-y-auto px-4 pb-6">
        <p v-if="error" role="alert" class="mb-3 text-sm text-destructive">{{ t(error) }}</p>
        <p v-if="result" role="status" class="mb-3 text-sm">{{ t(result) }}</p>
        <p v-if="!count && !unavailable" class="text-sm text-muted-foreground">{{ t('common.operationPanel.empty') }}</p>
        <ul v-else class="space-y-3" :aria-label="t('common.operationPanel.items')">
          <li v-for="row in rows" :key="row.key" class="min-w-0 space-y-3 rounded-lg border bg-card p-3">
            <div class="flex flex-wrap items-center justify-between gap-2 text-sm"><strong>{{ t('common.operationPanel.areas.' + area(row.resource)) }}</strong><span>{{ t('common.operationPanel.states.' + row.state) }}</span></div>
            <details class="text-xs"><summary class="cursor-pointer rounded focus-visible:outline-2 focus-visible:outline-ring">{{ t('common.operationPanel.reference') }}</summary><dl class="mt-2 space-y-1 break-all text-muted-foreground"><dt>{{ t('common.operationPanel.key') }}</dt><dd>{{ row.key }}</dd><dt>{{ t('common.operationPanel.operation') }}</dt><dd>{{ row.operation }}</dd><dt>{{ t('common.operationPanel.target') }}</dt><dd>{{ row.resource }}</dd></dl></details>
            <Button v-if="panel.queryable(row)" size="sm" variant="outline" :disabled="!!querying || !['UNKNOWN', 'PENDING'].includes(row.state)" @click="panel.check(row.key)">{{ t(querying === row.key ? 'common.operationPanel.checking' : 'common.operationPanel.check') }}</Button>
            <p v-else class="text-xs text-muted-foreground">{{ t('common.operationPanel.nativeHint') }}</p>
            <Button v-if="destination(row)" size="sm" variant="ghost" @click="recover(row.key)">{{ t('common.operationPanel.openBusiness') }}</Button>
            <Button v-else-if="row.resource.startsWith('workspace.shelf.')" size="sm" variant="ghost" @click="recoverShelf(row.key)">{{ t('common.shelf.title') }}</Button>
          </li>
        </ul>
      </div>
    </SheetContent>
  </Sheet>
</template>
