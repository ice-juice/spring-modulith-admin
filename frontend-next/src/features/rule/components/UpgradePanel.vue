<script setup lang="ts">
import PermissionButton from '@/components/business/PermissionButton.vue'

import { computed, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import ErrorNotice from "@/components/business/ErrorNotice.vue";
import JsonField from "./JsonField.vue";
import { confirmAction } from "@/lib/feedback";
import { printExact } from "../exact";
import { ruleWrite } from "../api";
import { useRuleTask } from "../useRuleTask";
import type { Asset, Instance, Migration, Preview } from "../types";
const props = defineProps<{ asset: Asset<Instance> }>(),
  emit = defineEmits<{ changed: [] }>(),
  { t } = useI18n(),
  task = useRuleTask(),
  { busy, error, uncertain } = task;
const target = ref(""),
  params = ref<Record<string, unknown> | null>(null),
  samples = ref<Record<string, unknown>[]>([{}]),
  preview = ref<Preview>(),
  validated = ref(""),
  form = ref<HTMLFormElement>();
const command = computed<Migration>(() => ({
    expectedRevision: props.asset.revision,
    templateVersionId: target.value,
    params: params.value,
    sampleInputs: samples.value,
  })),
  canSave = computed(
    () =>
      preview.value?.canMigrate &&
      printExact(command.value) === validated.value,
  );
async function run(save = false) {
  if (!form.value?.reportValidity()) return;
  const data = command.value,
    signature = printExact(data);
  if (save) {
    if (!canSave.value) return
    await task.command({ operation: 'rule.upgrade-draft', resource: 'rule.assets.' + props.asset.id, path: '/instances/' + props.asset.id + '/upgrade-draft', body: data,
      confirm: async () => await confirmAction(t('rule.canMigrate'), t('common.confirm')) && signature === printExact(command.value),
      completed: () => { preview.value = undefined; emit('changed') },
    }, () => { preview.value = undefined; emit('changed') })
  } else
    await task.run(
      () =>
        ruleWrite<Preview>(
          "/instances/" + props.asset.id + "/upgrade-preview",
          data,
        ),
      (value) => {
        preview.value = value;
        validated.value = signature;
      },
    );
}
watch(() => props.asset.id, id => task.bind('rule.assets.' + id), { immediate: true })
</script>
<template>
  <details class="rounded-xl border bg-card p-4">
    <summary class="cursor-pointer font-medium">
      {{ t("rule.upgrade") }}
    </summary>
    <form ref="form" @submit.prevent="run()"><fieldset :disabled="busy || uncertain" class="mt-3 space-y-3">
      <label class="grid gap-1 text-sm"
        >{{ t("rule.toVersion")
        }}<input
          v-model="target"
          required
          pattern="[0-9]+"
          class="h-10 min-w-0 rounded-lg border bg-card px-3" /></label
      ><JsonField v-model="params" :label="t('rule.params')" /><JsonField
        v-model="samples"
        :label="t('rule.samples')"
      />
      <div class="flex flex-wrap gap-2">
        <PermissionButton :permission="'rule:instance:edit'" :disabled="busy">{{ t("rule.preview") }}</PermissionButton
        ><PermissionButton :permission="'rule:instance:edit'"
          type="button"
          variant="outline"
          :disabled="busy || uncertain || !canSave"
          @click="run(true)"
          >{{ t("rule.migrate") }}</PermissionButton
        >
      </div>
      <ErrorNotice :message="error" />
      
    </fieldset></form>
    <div v-if="preview" class="mt-3 space-y-2">
      <p v-if="preview.canMigrate" class="text-sm text-success">
        {{ t("rule.canMigrate") }}
      </p>
      <p
        v-for="item in preview.problems"
        :key="item.path + item.code"
        class="text-xs text-destructive"
      >
        {{ item.path }}{{ t("rule.separator") }}{{ item.code }}
      </p>
      <div
        v-for="item in preview.comparisons"
        :key="item.sample"
        class="rounded-lg border bg-muted/20 p-3 text-xs"
      >
        <strong>{{
          t(item.changed ? "rule.changed" : "rule.unchanged")
        }}</strong>
        <div class="mt-2 grid gap-2 break-all sm:grid-cols-2">
          <pre class="whitespace-pre-wrap"
            >{{ item.before.status }}{{ t("rule.separator")
            }}{{ printExact(item.before.output) }}</pre>
          <pre class="whitespace-pre-wrap"
            >{{ item.after.status }}{{ t("rule.separator")
            }}{{ printExact(item.after.output) }}</pre>
        </div>
      </div>
      <JsonField
        :model-value="preview.changes"
        :label="t('rule.diff')"
        readonly
      />
    </div>
  </details>
</template>
