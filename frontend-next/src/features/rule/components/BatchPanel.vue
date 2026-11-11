<script setup lang="ts">
import RuleOperationStatus from './RuleOperationStatus.vue'
import PermissionButton from '@/components/business/PermissionButton.vue'
import { ref, computed, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useAuth } from "@/stores/auth";
import { Button } from "@/components/ui/button";
import ErrorNotice from "@/components/business/ErrorNotice.vue";
import JsonField from "./JsonField.vue";
import { confirmAction } from "@/lib/feedback";
import { ruleGet } from "../api";
import { useRuleTask } from "../useRuleTask";
import type { BatchReport, Migration } from "../types";
const { t } = useI18n(),
  auth = useAuth(),
  task = useRuleTask(),
  { busy, error, uncertain } = task,
  items = ref<{ instanceId: string; migration: Migration }[]>([]),
  receipt = ref(""),
  report = ref<BatchReport>(),
  form = ref<HTMLFormElement>(),
  key = computed(() => task.submission.record.value?.key || '');
task.bind('rule.batch.*')
watch(task.recovered, recovered)
function add() {
  if (!busy.value && !uncertain.value && items.value.length < 50)
    items.value.push({
      instanceId: "",
      migration: {
        expectedRevision: "",
        templateVersionId: "",
        params: null,
        sampleInputs: [{}],
      },
    });
}
async function run() {
  if (busy.value || uncertain.value || !items.value.length || !form.value?.reportValidity()) return
  const ids = [...new Set(items.value.map(item => item.instanceId))].sort()
  await task.command<BatchReport>({ operation: 'rule.batch', resource: 'rule.batch.' + ids[0], resources: ids.map(id => 'rule.assets.' + id), path: '/migration-batches', body: { items: items.value }, operationKey: true, protocol: 'NATIVE',
    confirm: () => confirmAction(t('rule.batchHint'), t('common.confirm')),
    native: { success: value => value.state === 'COMPLETED' ? 'completed' : value.state === 'COMPLETED_WITH_ERRORS' ? 'failed' : value.state === 'RUNNING' ? 'pending' : 'unknown' },
  }, value => { report.value = value; receipt.value = value.id })
}
function recovered() { const value = task.recovered.value as { report?: BatchReport } | undefined; if (value?.report) { report.value = value.report; receipt.value = value.report.id } }
async function lookup() {
  if (task.submission.unresolved.value) { await task.check(); recovered(); return }
  if (!/^\d+$/.test(receipt.value)) return;
  await task.run(
    () => ruleGet<BatchReport>("/migration-batches/" + receipt.value),
    (value) => {
      report.value = value;
      if (value.state !== "RUNNING") uncertain.value = false;
    },
  );
}
</script>
<template>
  <section class="space-y-4 rounded-2xl border bg-card p-4">
    <h2 class="text-sm font-semibold">{{ t("rule.batch") }}</h2>
    <p class="text-xs text-muted-foreground">{{ t("rule.batchHint") }}</p>
    <ErrorNotice :message="error" /><RuleOperationStatus :task="task" @resolved="recovered" />
    <p v-if="uncertain" role="alert" class="text-sm text-destructive">
      {{ t("rule.uncertain") }}{{ t("rule.separator") }}{{ key }}
    </p>
    <form
      v-if="auth.hasPermission('rule:batch:migrate')"
      ref="form"
      class="space-y-3"
      @submit.prevent="run"
    >
      <fieldset
        v-for="(item, index) in items"
        :disabled="busy || uncertain"
        :key="index"
        class="grid gap-3 rounded-lg border bg-muted/20 p-3 sm:grid-cols-2"
      >
        <label class="grid gap-1 text-xs"
          >{{ t("rule.instanceId")
          }}<input
            v-model="item.instanceId"
            required
            pattern="[0-9]+"
            class="h-9 rounded-md border bg-card px-2" /></label
        ><label class="grid gap-1 text-xs"
          >{{ t("rule.revision")
          }}<input
            v-model="item.migration.expectedRevision"
            required
            pattern="[0-9]+"
            class="h-9 rounded-md border bg-card px-2" /></label
        ><label class="grid gap-1 text-xs"
          >{{ t("rule.templateVersionId")
          }}<input
            v-model="item.migration.templateVersionId"
            required
            pattern="[0-9]+"
            class="h-9 rounded-md border bg-card px-2" /></label
        ><JsonField
          v-model="item.migration.params"
          :label="t('rule.params')"
        /><JsonField
          v-model="item.migration.sampleInputs"
          :label="t('rule.samples')"
        /><Button
          type="button"
          variant="ghost"
          size="sm"
          @click="items.splice(index, 1)"
          >{{ t("rule.remove") }}</Button
        >
      </fieldset>
      <div class="flex flex-wrap gap-2">
        <Button
          type="button"
          variant="outline"
          size="sm"
          :disabled="busy || uncertain || items.length >= 50"
          @click="add"
          >{{ t("rule.batchItems") }}</Button
        ><PermissionButton :permission="'rule:batch:migrate'" size="sm" :disabled="busy || uncertain || !items.length">{{
          t("rule.migrate")
        }}</PermissionButton>
      </div>
    </form>
    <form
      v-if="auth.hasPermission('rule:batch:view')"
      class="flex flex-wrap items-end gap-2"
      @submit.prevent="lookup"
    >
      <label class="grid flex-1 gap-1 text-xs"
        >{{ t("rule.receiptId")
        }}<input
          v-model="receipt"
          required
          pattern="[0-9]+"
          class="h-9 rounded-md border bg-card px-2" /></label
      ><PermissionButton :permission="'rule:batch:view'" variant="outline" size="sm" :disabled="busy">{{
        t("rule.search")
      }}</PermissionButton>
    </form>
    <section v-if="report" class="space-y-3 rounded-xl border p-3">
      <h3 class="text-sm font-semibold">
        {{ t("rule.receipt") }}{{ t("rule.separator") }}{{ report.state }}
      </h3>
      <ul class="space-y-1 text-xs">
        <li v-for="item in report.results" :key="item.instanceId">
          {{ item.instanceId }}{{ t("rule.separator")
          }}{{ t(item.passed ? "rule.passed" : "rule.failed")
          }}{{ t("rule.separator") }}{{ item.newRevision || item.error || "—" }}
        </li>
      </ul>
      <p
        v-if="report.pendingInstanceIds.length"
        class="break-all text-xs text-warning"
      >
        {{ t("rule.pending") }}{{ t("rule.separator")
        }}{{ report.pendingInstanceIds.join(", ") }}
      </p>
    </section>
  </section>
</template>
