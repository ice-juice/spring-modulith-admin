<script setup lang="ts">
import PermissionButton from '@/components/business/PermissionButton.vue'

import { ref, watch } from "vue";
import { useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { Button } from "@/components/ui/button";
import ErrorNotice from "@/components/business/ErrorNotice.vue";
import JsonField from "./JsonField.vue";
import { confirmAction } from "@/lib/feedback";
import { ruleGet } from "../api";
import { useRuleTask } from "../useRuleTask";
import type { Asset, RuleDocument, Version } from "../types";
const props = defineProps<{ asset: Asset<RuleDocument>; dirty: boolean }>(),
  emit = defineEmits<{ changed: [] }>(),
  { t } = useI18n(),
  router = useRouter(),
  task = useRuleTask(),
  { busy, error, uncertain } = task;
const code = ref(""),
  name = ref(""),
  versions = ref<Version[]>([]),
  from = ref(""),
  to = ref(""),
  copyVersion = ref(""),
  result = ref<unknown>(),
  truncated = ref(false);
watch(() => props.asset.id, id => task.bind('rule.assets.' + id), { immediate: true })
async function copy() {
  await task.command<Asset<RuleDocument>>({ operation: 'rule.template-copy', resource: 'rule.assets.' + props.asset.id, path: '/templates/' + props.asset.id + '/copy', body: { code: code.value, name: name.value, versionId: copyVersion.value || null }, completed: async () => { await router.push('/rule/templates') } }, value => { void router.push('/rule/templates/' + value.id) })
}
async function list() {
  await task.run(
    () => ruleGet<Version[]>("/assets/" + props.asset.id + "/versions"),
    (value) => (versions.value = value),
  );
}
async function diff() {
  if (!from.value || !to.value) return;
  await task.run(
    () =>
      ruleGet("/assets/" + props.asset.id + "/diff", {
        fromVersionId: from.value,
        toVersionId: to.value,
      }),
    (value) => (result.value = value),
  );
}
async function impact() {
  await task.run(
    () =>
      ruleGet<{ truncated: boolean; assets: unknown[] }>(
        "/templates/" + props.asset.id + "/dependency-impact",
      ),
    (value) => {
      result.value = value.assets;
      truncated.value = value.truncated;
    },
  );
}
async function lifecycle(action: 'deprecate' | 'revoke') {
  if (props.dirty || busy.value || uncertain.value) return
  await task.command({ operation: 'rule.' + action, resource: 'rule.assets.' + props.asset.id, path: '/templates/' + props.asset.id + '/' + action, body: { expectedRevision: props.asset.revision }, operationKey: true,
    confirm: () => confirmAction(t('rule.lifecycleConfirm'), t('common.confirm'), true), completed: () => emit('changed') }, () => emit('changed'))
}
</script>
<template>
  <details class="space-y-3 rounded-xl border bg-card p-4">
    <summary class="cursor-pointer font-medium">
      {{ t("rule.operations") }}
    </summary>
    <ErrorNotice :message="error" />
    
    <div class="flex flex-wrap gap-2">
      <Button variant="outline" size="sm" :disabled="busy" @click="list">{{
        t("rule.versions")
      }}</Button
      ><Button variant="outline" size="sm" :disabled="busy" @click="impact">{{
        t("rule.impact")
      }}</Button
      ><PermissionButton
        :permission="'rule:template:lifecycle'"
        variant="outline"
        size="sm"
        :disabled="
          busy ||
          dirty ||
          uncertain ||
          !asset.activeVersionId ||
          asset.state === 'REVOKED'
        "
        @click="lifecycle('deprecate')"
        >{{ t("rule.deprecate") }}</PermissionButton
      ><PermissionButton
        :permission="'rule:template:lifecycle'"
        variant="outline"
        size="sm"
        :disabled="
          busy ||
          dirty ||
          uncertain ||
          !asset.activeVersionId ||
          asset.state === 'REVOKED'
        "
        @click="lifecycle('revoke')"
        >{{ t("rule.revoke") }}</PermissionButton
      >
    </div>
    <div v-if="versions.length" class="flex flex-wrap gap-2">
      <select
        v-model="from"
        :aria-label="t('rule.fromVersion')"
        class="h-9 rounded-md border bg-card px-2"
      >
        <option value="">{{ t("rule.fromVersion") }}</option>
        <option v-for="item in versions" :key="item.id" :value="item.id">
          {{ item.id }}
        </option></select
      ><select
        v-model="to"
        :aria-label="t('rule.toVersion')"
        class="h-9 rounded-md border bg-card px-2"
      >
        <option value="">{{ t("rule.toVersion") }}</option>
        <option v-for="item in versions" :key="item.id" :value="item.id">
          {{ item.id }}
        </option></select
      ><Button size="sm" :disabled="busy || !from || !to" @click="diff">{{
        t("rule.diff")
      }}</Button>
    </div>
    <fieldset :disabled="busy || uncertain"><form
      v-permission="'rule:template:edit'"
      class="grid gap-3 rounded-lg border bg-muted/20 p-3 sm:grid-cols-2"
      @submit.prevent="copy"
    >
      <label class="grid gap-1 text-xs"
        >{{ t("rule.code")
        }}<input
          v-model="code"
          required
          pattern="[a-z][a-z0-9_-]{0,63}"
          class="h-9 min-w-0 rounded-md border bg-card px-2" /></label
      ><label class="grid gap-1 text-xs"
        >{{ t("rule.name")
        }}<input
          v-model="name"
          required
          maxlength="128"
          class="h-9 min-w-0 rounded-md border bg-card px-2" /></label
      ><select
        v-model="copyVersion"
        :aria-label="t('rule.version')"
        class="h-9 rounded-md border bg-card px-2"
      >
        <option value="">{{ t("rule.saveDraft") }}</option>
        <option v-for="item in versions" :key="item.id" :value="item.id">
          {{ item.id }}
        </option></select
      ><PermissionButton :permission="'rule:template:edit'" size="sm" :disabled="busy || uncertain || dirty">{{
        t("rule.copy")
      }}</PermissionButton>
    </form></fieldset>
    <p v-if="truncated" class="text-sm text-destructive">
      {{ t("rule.truncated") }}
    </p>
    <JsonField
      v-if="result"
      v-model="result"
      :label="t('rule.result')"
      readonly
    />
  </details>
</template>
