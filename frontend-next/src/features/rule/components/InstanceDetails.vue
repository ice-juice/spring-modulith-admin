<script setup lang="ts">
import PermissionButton from '@/components/business/PermissionButton.vue'

import { ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import ErrorNotice from "@/components/business/ErrorNotice.vue";
import JsonField from "./JsonField.vue";
import UpgradePanel from "./UpgradePanel.vue";
import ReleasePanel from "./ReleasePanel.vue";
import ExecutionView from "./ExecutionView.vue";
import RuleOperationStatus from './RuleOperationStatus.vue';
import { useRuleTask } from "../useRuleTask";
import type { Asset, Instance, Execution } from "../types";
const props = defineProps<{ asset: Asset<Instance> }>(),
  emit = defineEmits<{ changed: [] }>(),
  { t } = useI18n(),
  task = useRuleTask(),
  { busy, error } = task,
  input = ref<unknown>({}),
  result = ref<Execution>(),
  trialReady = ref(false),
  form = ref<HTMLFormElement>();
watch(() => props.asset.id, id => task.bind('rule.assets.' + id), { immediate: true })
async function run(trial: boolean) {
  if (!form.value?.reportValidity()) return
  await task.command<Execution>({ operation: trial ? 'rule.trial' : 'rule.execute', resource: 'rule.assets.' + props.asset.id,
    path: '/instances/' + props.asset.id + (trial ? '/trial' : '/execute'), body: trial ? { expectedRevision: props.asset.revision, input: input.value } : { input: input.value }, completed: () => emit('changed') }, value => {
    result.value = value; if (trial) trialReady.value = ['SUCCESS', 'NOT_MATCHED'].includes(value.status)
  })
}
</script>
<template>
  <section
    class="mt-4 grid gap-4 rounded-2xl border bg-card p-4 lg:grid-cols-2"
  >
    <div class="space-y-4">
      <h2 class="text-base font-semibold">{{ asset.name }}</h2>
      <dl class="grid grid-cols-2 gap-2 text-xs">
        <dt>{{ t("rule.revision") }}</dt>
        <dd>{{ asset.revision }}</dd>
        <dt>{{ t("rule.releaseRevision") }}</dt>
        <dd>{{ asset.releaseRevision }}</dd>
        <dt>{{ t("rule.state") }}</dt>
        <dd>{{ t("rule.states." + asset.state) }}</dd>
      </dl>
      <ReleasePanel
        :key="asset.revision"
        :asset="asset"
        :trial-ready="trialReady"
        @changed="emit('changed')"
      /><UpgradePanel
        v-permission="'rule:instance:edit'"
        :asset="asset"
        @changed="emit('changed')"
      />
    </div>
    <form ref="form" class="space-y-3" @submit.prevent="run(true)">
      <JsonField :readonly="busy || task.uncertain.value" v-model="input" :label="t('rule.input')" />
      <div class="flex flex-wrap gap-2">
        <PermissionButton :permission="'rule:instance:edit'" :disabled="busy || task.uncertain.value">{{
          t("rule.trial")
        }}</PermissionButton
        ><PermissionButton
          :permission="'rule:execution:run'"
          type="button"
          variant="outline"
          :disabled="busy || task.uncertain.value"
          @click="run(false)"
          >{{ t("rule.execute") }}</PermissionButton
        >
      </div>
      <ErrorNotice :message="error" /><RuleOperationStatus :task="task" @resolved="emit('changed')" /><ExecutionView
        v-if="result"
        :result="result"
      />
    </form>
  </section>
</template>
