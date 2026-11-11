<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { confirmAction, notify } from "@/lib/feedback";
import { Button } from "@/components/ui/button";
import EditorDialog from "@/components/business/EditorDialog.vue";
import ErrorNotice from "@/components/business/ErrorNotice.vue";
import RuleOperationStatus from './RuleOperationStatus.vue'
import ParameterForm from "./ParameterForm.vue";
import JsonField from "./JsonField.vue";
import { cloneExact, printExact } from "../exact";
import { valuesFor, validInstant } from "../model";
import { ruleGet } from "../api";
import { useRuleTask } from "../useRuleTask";
import type { Asset, Instance, RuleDocument, Version } from "../types";
const props = defineProps<{
    asset?: Asset<Instance>;
    version: Version<RuleDocument>;
  }>(),
  open = defineModel<boolean>({ required: true }),
  emit = defineEmits<{ saved: [asset?: Asset<Instance>] }>(),
  { t } = useI18n();
const asset = ref(props.asset),
  draft = ref<Instance>(
    props.asset
      ? cloneExact(props.asset.draft)
      : {
          code: "",
          name: "",
          templateVersionId: props.version.id,
          params: valuesFor(props.version.document.parameterSchema),
          objectType: "",
          objectId: "",
          environment: "default",
          startAt: null,
          endAt: null,
        },
  );
const initial = printExact(draft.value),
  dirty = computed(() => printExact(draft.value) !== initial),
  form = ref<HTMLFormElement>(),
  task = useRuleTask(),
  { busy, error, uncertain } = task,
  snapshot = ref<unknown>();
async function save() {
  if (!form.value?.reportValidity()) return;
  if (
    !validInstant(draft.value.startAt || "") ||
    !validInstant(draft.value.endAt || "") ||
    (draft.value.startAt &&
      draft.value.endAt &&
      Date.parse(draft.value.startAt) >= Date.parse(draft.value.endAt))
  ) {
    error.value = t("rule.invalidJson");
    return;
  }
  draft.value.startAt ||= null;
  draft.value.endAt ||= null;
  const current = asset.value
  await task.command<Asset<Instance>>({ operation: 'rule.instance-save', resource: current ? 'rule.assets.' + current.id : 'rule.instances.new',
    path: current ? '/instances/' + current.id + '/draft' : '/instances', body: current ? { expectedRevision: current.revision, instance: draft.value } : draft.value,
    method: current ? 'PUT' : 'POST', completed: () => recovered(),
  }, value => { notify(t('rule.saved')); emit('saved', value); open.value = false })
}
watch(() => props.asset?.id, id => task.bind(id ? 'rule.assets.' + id : 'rule.instances.new'), { immediate: true })
async function recovered(state = 'SUCCEEDED') {
  if (state !== 'SUCCEEDED') return
  await task.run<Asset<Instance> | Asset<Instance>[]>(() => asset.value ? ruleGet<Asset<Instance>>('/instances/' + asset.value.id) : ruleGet<Asset<Instance>[]>('/instances', { page: 1, size: 20 }), value => { snapshot.value = value; emit('saved', Array.isArray(value) ? undefined : value); open.value = false })
}
async function close() {
  if (
    dirty.value &&
    !(await confirmAction(t("common.unsavedConfirm"), t("common.confirm")))
  )
    return;
  open.value = false;
}
async function reconcile() { const state = await task.check(); if (state) await recovered(state) }
</script>
<template>
  <EditorDialog
    v-model="open"
    :title="t('rule.newInstance')"
    :description="t('rule.bindingHint')"
    :busy="busy"
    :submit-disabled="uncertain"
    :dirty="dirty"
    wide
    @save="save"
    ><ErrorNotice :message="error" /><RuleOperationStatus :task="task" @resolved="recovered" />
    <div v-if="uncertain" class="mb-4 space-y-2">
      <p role="alert" class="text-sm text-destructive">
        {{ t("rule.uncertain") }}
      </p>
      <Button variant="outline" :disabled="busy" @click="reconcile">{{
        t("rule.reconcile")
      }}</Button>
    </div>
    <JsonField
      v-if="snapshot"
      v-model="snapshot"
      :label="t('rule.details')"
      readonly
    />
    <form ref="form" @submit.prevent="save"><fieldset :disabled="busy || uncertain" class="space-y-5">
      <p class="break-all text-xs text-muted-foreground">
        {{ t("rule.version") }}{{ t("rule.separator") }}{{ version.id }}
      </p>
      <div class="grid gap-4 sm:grid-cols-2">
        <label
          v-for="key in [
            'code',
            'name',
            'objectType',
            'objectId',
            'environment',
          ] as const"
          :key="key"
          class="grid gap-2 text-sm"
          >{{ t("rule." + key)
          }}<input
            v-model="draft[key]"
            required
            :pattern="key === 'code' ? '[a-z][a-z0-9_-]{0,63}' : undefined"
            :readonly="!!asset && key === 'code'"
            :maxlength="key === 'code' || key === 'objectType' ? 64 : 128"
            class="h-10 min-w-0 rounded-lg border bg-card px-3" /></label
        ><label
          v-for="key in ['startAt', 'endAt'] as const"
          :key="key"
          class="grid gap-2 text-sm"
          >{{ t("rule." + key)
          }}<input
            v-model="draft[key]"
            class="h-10 min-w-0 rounded-lg border bg-card px-3"
        /></label>
      </div>
      <h3 class="text-sm font-semibold">{{ t("rule.parameters") }}</h3>
      <ParameterForm :readonly="busy || uncertain" v-model="draft.params" :document="version.document" />
    </fieldset></form>
    <template #footer
      ><Button variant="outline" :disabled="busy" @click="close">{{
        t("common.cancel")
      }}</Button
      ><Button :disabled="busy || uncertain" @click="save">{{
        t("rule.saveDraft")
      }}</Button></template
    ></EditorDialog
  >
</template>
