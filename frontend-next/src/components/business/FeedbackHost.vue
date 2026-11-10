<script setup lang="ts">
import { CheckCircle2, AlertCircle, X } from '@lucide/vue'
import { ToastProvider, ToastRoot, ToastTitle, ToastClose, ToastViewport } from 'reka-ui'
import { useI18n } from 'vue-i18n'
import { AlertDialog, AlertDialogContent, AlertDialogHeader, AlertDialogTitle, AlertDialogDescription, AlertDialogFooter, AlertDialogCancel, AlertDialogAction } from '@/components/ui/alert-dialog'
import { notices, dismissNotice, confirmation, settleConfirmation } from '@/lib/feedback'
const { t } = useI18n()
</script>
<template>
  <ToastProvider :duration="6000"><ToastRoot v-for="notice in notices" :key="notice.id" :open="true" class="pointer-events-auto flex items-center gap-3 rounded-xl border bg-popover p-4 text-popover-foreground shadow-lg" @update:open="!$event && dismissNotice(notice.id)"><AlertCircle v-if="notice.tone === 'error'" class="size-5 shrink-0 text-destructive" /><CheckCircle2 v-else class="size-5 shrink-0 text-primary" /><ToastTitle class="min-w-0 flex-1 text-sm">{{ notice.message }}</ToastTitle><ToastClose :aria-label="t('common.cancel')"><X class="size-4" /></ToastClose></ToastRoot><ToastViewport class="pointer-events-none fixed right-4 bottom-4 z-[100] flex w-[calc(100%-2rem)] max-w-sm flex-col gap-2 outline-none" /></ToastProvider>
  <AlertDialog :open="!!confirmation" @update:open="!$event && settleConfirmation(false)"><AlertDialogContent><AlertDialogHeader><AlertDialogTitle>{{ confirmation?.title }}</AlertDialogTitle><AlertDialogDescription>{{ confirmation?.description }}</AlertDialogDescription></AlertDialogHeader><AlertDialogFooter><AlertDialogCancel @click="settleConfirmation(false)">{{ t('common.cancel') }}</AlertDialogCancel><AlertDialogAction :class="confirmation?.destructive ? 'bg-destructive text-destructive-foreground hover:bg-destructive/90' : ''" @click="settleConfirmation(true)">{{ t('common.confirm') }}</AlertDialogAction></AlertDialogFooter></AlertDialogContent></AlertDialog>
</template>
