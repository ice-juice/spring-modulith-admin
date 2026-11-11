<script setup lang="ts">
import { ApiError } from '@/api/http'
import PermissionButton from '@/components/business/PermissionButton.vue'

import { ref, computed, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useAuth } from "@/stores/auth";
import ErrorNotice from "@/components/business/ErrorNotice.vue";
import JsonField from "./JsonField.vue";
import { confirmAction } from "@/lib/feedback";
import { ruleGet } from "../api";
import { useRuleTask } from "../useRuleTask";
import type { Asset, Version, Release } from "../types";
const props = defineProps<{
    asset: Asset;
    dirty?: boolean;
    trialReady?: boolean;
  }>(),
  emit = defineEmits<{ changed: [] }>(),
  { t } = useI18n(),
  auth = useAuth(),
  task = useRuleTask(),
  { busy, error, uncertain } = task,
  versions = ref<Version[]>([]),
  target = ref(""),
  details = ref<unknown>(),
  result = ref<unknown>();
const instance = computed(() => props.asset.kind === "INSTANCE"),
  publishPermission = computed(() =>
    instance.value ? "rule:instance:publish" : "rule:template:publish",
  );
async function history() {
  await task.run(
    () => ruleGet<Version[]>("/assets/" + props.asset.id + "/versions"),
    (value) => (versions.value = value),
  );
}
watch(() => props.asset.id, id => task.bind('rule.assets.' + id), { immediate: true })
async function action(kind: 'publish' | 'disable' | 'rollback') {
  if (props.dirty || busy.value || uncertain.value) return
  const id = props.asset.id, expectedRevision = props.asset.revision, versionId = target.value, isInstance = instance.value
  const path = kind === 'rollback' ? '/assets/' + id + '/rollback' : '/' + (isInstance ? 'instances' : 'templates') + '/' + id + '/' + kind
  await task.command<Release>({ operation: !isInstance && kind === 'publish' ? 'rule.template-publish' : 'rule.' + kind,
    resource: 'rule.assets.' + id, path, body: { expectedRevision, ...(kind === 'rollback' ? { versionId } : {}) }, operationKey: true,
    protocol: !isInstance && kind === 'publish' ? 'NATIVE' : 'LOCAL',
    confirm: () => confirmAction(t('rule.' + (kind === 'publish' ? 'publishConfirm' : kind === 'disable' ? 'disableConfirm' : 'rollbackConfirm')), t('common.confirm'), kind !== 'publish'),
    completed: () => emit('changed'),
    native: { success: value => value.assetId === id && value.action === 'PUBLISH' ? 'completed' : 'unknown', failure: cause => cause instanceof ApiError && [400, 401, 403, 404, 409, 413, 415, 429].includes(cause.code) ? 'rejected' : 'unknown' },
  }, value => { result.value = value; emit('changed') })
}
</script>
<template>
  <section class="space-y-3 rounded-xl border bg-card p-4">
    <div class="flex flex-wrap gap-2">
      <PermissionButton :permission="publishPermission"
        v-if="auth.hasPermission(publishPermission)"
        :disabled="busy || uncertain || dirty || (instance && !trialReady)"
        @click="action('publish')"
        >{{ t("rule.publish") }}</PermissionButton
      ><PermissionButton :permission="'rule:instance:disable'"
        v-if="instance && auth.hasPermission('rule:instance:disable')"
        variant="outline"
        :disabled="busy || uncertain || dirty"
        @click="action('disable')"
        >{{ t("rule.disable") }}</PermissionButton
      ><PermissionButton :permission="'rule:template:view'"
        v-if="auth.hasPermission('rule:template:view')"
        variant="ghost"
        :disabled="busy"
        @click="history"
        >{{ t("rule.versions") }}</PermissionButton
      >
    </div>
    <p class="text-xs text-muted-foreground">{{ t("rule.noAutoPublish") }}</p>
    
    <ErrorNotice :message="error" />
    <div v-if="versions.length" class="space-y-3">
      <label class="grid gap-2 text-sm"
        >{{ t("rule.version")
        }}<select
          v-model="target"
          :disabled="busy || uncertain"
          :aria-label="t('rule.version')"
          class="h-10 max-w-full rounded-lg border bg-card px-3"
          @change="details = versions.find((row) => row.id === target)"
        >
          <option value="">{{ t("rule.choose") }}</option>
          <option
            v-for="version in versions"
            :key="version.id"
            :value="version.id"
          >
            {{ version.revision }}{{ t("rule.separator") }}{{ version.id }}
          </option>
        </select></label
      ><PermissionButton
        :permission="'rule:release:rollback'"
        variant="outline"
        :disabled="!target || busy || uncertain || dirty"
        @click="action('rollback')"
        >{{ t("rule.rollback") }}</PermissionButton
      ><JsonField
        v-if="details"
        v-model="details"
        :label="t('rule.details')"
        readonly
      />
    </div>
    <JsonField
      v-if="result"
      v-model="result"
      :label="t('rule.result')"
      readonly
    />
  </section>
</template>
