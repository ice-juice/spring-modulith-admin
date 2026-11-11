<script setup lang="ts">
import PermissionButton from '@/components/business/PermissionButton.vue'

import { computed, ref } from "vue";
import { useI18n } from "vue-i18n";
import { useAuth } from "@/stores/auth";
import { usePreferences } from "@/stores/preferences";
import { formatInstant } from "@/lib/time";
import { Button } from "@/components/ui/button";
import PageHeader from "@/components/business/PageHeader.vue";
import DataTable from "@/components/business/DataTable.vue";
import FilterBar from "@/components/business/FilterBar.vue";
import ErrorNotice from "@/components/business/ErrorNotice.vue";
import RuleOperationStatus from './components/RuleOperationStatus.vue'
import BatchPanel from "./components/BatchPanel.vue";
import JsonField from "./components/JsonField.vue";
import { confirmAction } from "@/lib/feedback";
import { ruleGet, ruleWrite } from "./api";
import { useRuleTask } from "./useRuleTask";
import { validInstant } from "./model";
interface Summary {
  id: string;
  instanceId: string;
  templateVersionId: string | null;
  releaseRevision: string | null;
  status: string;
  durationMicros: string;
  createdAt: string;
}
const { t, locale } = useI18n(),
  auth = useAuth(),
  preferences = usePreferences(),
  task = useRuleTask(),
  { busy, error, uncertain } = task,
  tab = ref(
    auth.hasPermission("rule:execution:view")
      ? "executions"
      : auth.hasPermission(["rule:batch:view", "rule:batch:migrate"])
        ? "batch"
        : "resources",
  );
const query = ref({
    instanceId: "",
    templateId: "",
    templateVersionId: "",
    status: "",
    from: new Date(Date.now() - 86400000).toISOString(),
    until: new Date().toISOString(),
  }),
  rows = ref<Summary[]>([]),
  page = ref(1),
  detail = ref<unknown>(),
  statistics = ref<unknown>(),
  resources = ref<Record<string, number>>(),
  result = ref<unknown>(),
  days = ref(30),
  limit = ref(100);
const keys = [
  "instanceId",
  "templateId",
  "templateVersionId",
  "from",
  "until",
] as const;
const columns = computed(() => [
  { key: "status", title: t("rule.status") },
  { key: "instanceId", title: t("rule.instanceId") },
  { key: "templateVersionId", title: t("rule.templateVersionId") },
  { key: "releaseRevision", title: t("rule.releaseRevision") },
  { key: "createdAt", title: t("rule.createdAt") },
  { key: "durationMicros", title: t("rule.duration") },
  { key: "actions", title: t("rule.actions") },
]);
async function search(index = 1) {
  if (
    !validInstant(query.value.from) ||
    !validInstant(query.value.until) ||
    !query.value.from ||
    !query.value.until
  ) {
    error.value = t("rule.invalidJson");
    return;
  }
  const value = query.value;
  await task.run(
    () =>
      ruleWrite<Summary[]>("/executions/search", {
        instanceId: value.instanceId || null,
        templateId: value.templateId || null,
        templateVersionId: value.templateVersionId || null,
        status: value.status || null,
        from: value.from,
        until: value.until,
        page: index,
        size: 20,
      }),
    (data) => {
      rows.value = data;
      page.value = index;
      detail.value = undefined;
      statistics.value = undefined;
    },
  );
}
async function inspect(row: Summary) {
  await task.run(
    () => ruleGet("/instances/" + row.instanceId + "/executions/" + row.id),
    (value) => (detail.value = value),
  );
}
async function stats() {
  if (!query.value.instanceId) return;
  await task.run(
    () =>
      ruleGet(
        "/instances/" + query.value.instanceId + "/execution-statistics",
        { from: query.value.from, until: query.value.until },
      ),
    (value) => (statistics.value = value),
  );
}
async function loadResources() {
  await task.run(
    () => ruleGet<Record<string, number>>("/resources"),
    (value) => (resources.value = value),
  );
}
task.bind('rule.maintenance.*')
async function clean(cache: boolean) {
  await task.command({ operation: cache ? 'rule.resources-clear' : 'rule.retention-cleanup', resource: cache ? 'rule.maintenance.runtime' : 'rule.maintenance.retention',
    path: cache ? '/resources/clear' : '/retention/cleanup', body: cache ? {} : { days: days.value, limit: limit.value }, protocol: cache ? 'NATIVE' : 'LOCAL',
    confirm: () => confirmAction(t(cache ? 'rule.clearResources' : 'rule.cleanupConfirm'), t('common.confirm'), true),
    native: { success: () => 'completed' },
  }, value => { result.value = value })
  if (cache && !uncertain.value && auth.hasPermission('rule:maintenance:view')) await loadResources()
}
</script>
<template>
  <PageHeader :title="t('rule.operations')" :description="t('rule.hint')" />
  <div class="mb-4 flex flex-wrap gap-2">
    <PermissionButton :permission="'rule:execution:view'"
      v-if="auth.hasPermission('rule:execution:view')"
      :variant="tab === 'executions' ? 'secondary' : 'ghost'"
      @click="tab = 'executions'"
      >{{ t("rule.executions") }}</PermissionButton
    ><PermissionButton :permission="['rule:batch:view', 'rule:batch:migrate']"
      v-if="auth.hasPermission(['rule:batch:view', 'rule:batch:migrate'])"
      :variant="tab === 'batch' ? 'secondary' : 'ghost'"
      @click="tab = 'batch'"
      >{{ t("rule.batch") }}</PermissionButton
    ><PermissionButton :permission="['rule:maintenance:view', 'rule:maintenance:clean']"
      v-if="
        auth.hasPermission(['rule:maintenance:view', 'rule:maintenance:clean'])
      "
      :variant="tab === 'resources' ? 'secondary' : 'ghost'"
      @click="tab = 'resources'"
      >{{ t("rule.resources") }}</PermissionButton
    >
  </div>
  <ErrorNotice :message="error" /><RuleOperationStatus :task="task" />
  <p v-if="uncertain" role="alert" class="text-sm text-destructive">
    {{ t("rule.uncertain") }}
  </p>
  <section v-if="tab === 'executions'" class="space-y-4">
    <FilterBar :busy="busy" @search="search()"
      ><div class="grid w-full gap-3 sm:grid-cols-2 xl:grid-cols-3">
        <label v-for="key in keys" :key="key" class="grid gap-1 text-xs"
          >{{ t("rule." + key)
          }}<input
            v-model="query[key]"
            :required="key === 'from' || key === 'until'"
            :pattern="key.endsWith('Id') ? '[0-9]+' : undefined"
            class="h-9 min-w-0 rounded-md border bg-card px-2" /></label
        ><label class="grid gap-1 text-xs"
          >{{ t("rule.status")
          }}<select
            v-model="query.status"
            class="h-9 rounded-md border bg-card px-2"
          >
            <option value="">{{ t("rule.choose") }}</option>
            <option
              v-for="status in [
                'SUCCESS',
                'NOT_MATCHED',
                'INVALID_INPUT',
                'NOT_CONFIGURED',
                'DISABLED',
                'NOT_EFFECTIVE',
                'TIMEOUT',
                'ERROR',
              ]"
              :key="status"
            >
              {{ status }}
            </option>
          </select></label
        >
      </div>
      <template #actions
        ><Button
          type="button"
          variant="outline"
          size="sm"
          :disabled="busy || !query.instanceId"
          @click="stats"
          >{{ t("rule.statistics") }}</Button
        ></template
      ></FilterBar
    ><DataTable :rows="rows" :columns="columns" :loading="busy"
      ><template #createdAt="{ row }">{{
        formatInstant(row.createdAt, preferences.timeZone, locale)
      }}</template
      ><template #actions="{ row }"
        ><Button variant="ghost" size="sm" @click="inspect(row)">{{
          t("rule.details")
        }}</Button></template
      ></DataTable
    >
    <div class="flex justify-end gap-2">
      <Button
        variant="outline"
        size="sm"
        :disabled="busy || page <= 1"
        @click="search(page - 1)"
        >{{ t("rule.previous") }}</Button
      ><Button
        variant="outline"
        size="sm"
        :disabled="busy || rows.length < 20"
        @click="search(page + 1)"
        >{{ t("rule.nextPage") }}</Button
      >
    </div>
    <JsonField
      v-if="detail"
      v-model="detail"
      :label="t('rule.details')"
      readonly
      :rows="12"
    /><JsonField
      v-if="statistics"
      v-model="statistics"
      :label="t('rule.statistics')"
      readonly
    />
  </section>
  <BatchPanel v-if="tab === 'batch'" />
  <section
    v-if="tab === 'resources'"
    class="space-y-4 rounded-xl border bg-card p-4"
  >
    <p class="text-sm text-muted-foreground">{{ t("rule.allScopeHint") }}</p>
    <div class="flex flex-wrap gap-2">
      <PermissionButton
        :permission="'rule:maintenance:view'"
        variant="outline"
        :disabled="busy"
        @click="loadResources"
        >{{ t("rule.refresh") }}</PermissionButton
      ><PermissionButton
        :permission="'rule:maintenance:clean'"
        variant="outline"
        :disabled="busy || uncertain"
        @click="clean(true)"
        >{{ t("rule.clearResources") }}</PermissionButton
      >
    </div>
    <dl v-if="resources" class="grid gap-2 text-sm sm:grid-cols-2">
      <div
        v-for="(value, key) in resources"
        :key="key"
        class="rounded-lg bg-muted/30 p-3"
      >
        <dt>{{ key }}</dt>
        <dd class="text-base font-semibold tabular-nums">{{ value }}</dd>
      </div>
    </dl>
    <form
      v-permission="'rule:maintenance:clean'"
      class="flex flex-wrap items-end gap-3"
      @submit.prevent="clean(false)"
    >
      <label class="grid gap-1 text-sm"
        >{{ t("rule.days")
        }}<input
          v-model.number="days"
          type="number"
          required
          min="7"
          max="3650"
          class="h-9 rounded-md border bg-card px-2" /></label
      ><label class="grid gap-1 text-sm"
        >{{ t("rule.limit")
        }}<input
          v-model.number="limit"
          type="number"
          required
          min="1"
          max="500"
          class="h-9 rounded-md border bg-card px-2" /></label
      ><PermissionButton :permission="'rule:maintenance:clean'" variant="destructive" :disabled="busy || uncertain">{{
        t("rule.cleanup")
      }}</PermissionButton>
    </form>
    <JsonField
      v-if="result"
      v-model="result"
      :label="t('rule.result')"
      readonly
    />
  </section>
</template>
