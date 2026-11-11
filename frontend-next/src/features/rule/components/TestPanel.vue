<script setup lang="ts">
import PermissionButton from '@/components/business/PermissionButton.vue'

import { ref, computed, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useAuth } from "@/stores/auth";
import { Button } from "@/components/ui/button";
import EditorDialog from "@/components/business/EditorDialog.vue";
import ErrorNotice from "@/components/business/ErrorNotice.vue";
import RuleOperationStatus from './RuleOperationStatus.vue'
import JsonField from "./JsonField.vue";
import { ruleGet } from "../api";
import { cloneExact, printExact } from "../exact";
import { useRuleTask } from "../useRuleTask";
import type { CaseView, TestCaseInput, TestRun, Version } from "../types";
const props = defineProps<{ id: string }>(),
  { t } = useI18n(),
  auth = useAuth(),
  task = useRuleTask(),
  { busy, error, uncertain } = task;
const cases = ref<CaseView[]>([]),
  runs = ref<TestRun[]>([]),
  versions = ref<Version[]>([]),
  version = ref(""),
  compare = ref(""),
  report = ref<TestRun>(),
  loaded = ref(false),
  open = ref(false),
  selected = ref<CaseView>(),
  initial = ref(""),
  form = ref<HTMLFormElement>();
const blank = (): TestCaseInput => ({
    name: "",
    input: {},
    params: {},
    expectedStatus: "SUCCESS",
    expectedOutput: null,
    expectedProblemCode: null,
    enabled: true,
    evaluatedAt: "2026-01-01T00:00:00Z",
  }),
  draft = ref(blank()),
  base = computed(() => "/templates/" + props.id);
async function load() {
  await task.run(
    () =>
      Promise.all([
        ruleGet<CaseView[]>(base.value + "/testcases"),
        ruleGet<TestRun[]>(base.value + "/test-runs"),
        ruleGet<Version[]>("/assets/" + props.id + "/versions"),
      ]),
    ([a, b, c]) => {
      cases.value = a;
      runs.value = b;
      versions.value = c;
      loaded.value = true;
      uncertain.value = false;
    },
  );
}
function edit(row?: CaseView) {
  selected.value = row;
  draft.value = row ? cloneExact(row.testcase) : blank();
  initial.value = printExact(draft.value);
  open.value = true;
}
watch(() => props.id, id => task.bind('rule.assets.' + id), { immediate: true })
async function recovered(state = 'SUCCEEDED') { if (state === 'SUCCEEDED') { open.value = false; await load() } }
async function save() {
  if (!form.value?.reportValidity()) return
  const current = selected.value
  if (await task.command({ operation: 'rule.testcase-save', resource: 'rule.assets.' + props.id,
    path: base.value + '/testcases' + (current ? '/' + current.id : ''), method: current ? 'PUT' : 'POST',
    body: { expectedRevision: current?.revision || '0', testcase: { ...draft.value, expectedProblemCode: draft.value.expectedProblemCode || null } }, completed: () => recovered(),
  }, () => { open.value = false })) await load()
}
async function run() {
  if (!version.value) return
  if (await task.command<TestRun>({ operation: 'rule.test-run', resource: 'rule.assets.' + props.id, path: base.value + '/test-runs',
    body: { versionId: version.value, compareVersionId: compare.value || null }, completed: () => load(),
  }, value => { report.value = value })) await load()
}
</script>
<template>
  <details class="space-y-3 rounded-xl border bg-card p-4">
    <summary class="cursor-pointer font-medium" @click="!loaded && load()">
      {{ t("rule.casesTitle") }}
    </summary>
    <div class="flex flex-wrap gap-2">
      <Button variant="outline" size="sm" :disabled="busy" @click="load">{{
        t("rule.refresh")
      }}</Button
      ><PermissionButton :permission="'rule:test:edit'"
        v-if="auth.hasPermission('rule:test:edit')"
        size="sm"
        :disabled="busy || uncertain"
        @click="edit()"
        >{{ t("rule.newCase") }}</PermissionButton
      >
    </div>
    <ErrorNotice :message="error" />
    
    <ul class="space-y-1">
      <li
        v-for="row in cases"
        :key="row.id"
        class="flex items-center justify-between gap-2 rounded-lg bg-muted/25 px-3 py-2 text-sm"
      >
        <span
          >{{ row.testcase.name }}{{ t("rule.separator")
          }}{{
            t(row.testcase.enabled ? "rule.enabled" : "rule.disable")
          }}</span
        ><PermissionButton :permission="'rule:test:edit'"
          v-if="auth.hasPermission('rule:test:edit')"
          variant="ghost"
          size="sm"
          :disabled="busy || uncertain"
          @click="edit(row)"
          >{{ t("rule.edit") }}</PermissionButton
        >
      </li>
    </ul>
    <div class="grid gap-2 sm:grid-cols-2">
      <select
        v-model="version"
        :aria-label="t('rule.version')"
        class="h-9 min-w-0 rounded-md border bg-card px-2"
      >
        <option value="">{{ t("rule.version") }}</option>
        <option v-for="item in versions" :key="item.id" :value="item.id">
          {{ item.id }}
        </option></select
      ><select
        v-model="compare"
        :aria-label="t('rule.compareVersion')"
        class="h-9 min-w-0 rounded-md border bg-card px-2"
      >
        <option value="">{{ t("rule.compareVersion") }}</option>
        <option v-for="item in versions" :key="item.id" :value="item.id">
          {{ item.id }}
        </option></select
      ><PermissionButton :permission="'rule:test:run'"
        v-if="auth.hasPermission('rule:test:run')"
        size="sm"
        :disabled="busy || uncertain || !version"
        @click="run"
        >{{ t("rule.runTests") }}</PermissionButton
      >
    </div>
    <label v-if="runs.length" class="grid gap-1 text-sm"
      >{{ t("rule.testRuns")
      }}<select
        :aria-label="t('rule.testRuns')"
        class="h-9 rounded-md border bg-card px-2"
        @change="
          report = runs.find(
            (item) => item.id === ($event.target as HTMLSelectElement).value,
          )
        "
      >
        <option value="">{{ t("rule.choose") }}</option>
        <option v-for="item in runs" :key="item.id" :value="item.id">
          {{ item.id }}{{ t("rule.separator")
          }}{{ t(item.passed ? "rule.passed" : "rule.failed") }}
        </option>
      </select></label
    >
    <section v-if="report" class="space-y-2 rounded-lg border bg-muted/20 p-3">
      <h3 class="text-sm font-semibold">
        {{ t(report.passed ? "rule.passed" : "rule.failed")
        }}{{ t("rule.separator") }}{{ report.total }}
      </h3>
      <ul class="space-y-1 text-xs">
        <li v-for="item in report.results" :key="item.caseId">
          {{ item.name }}{{ t("rule.separator") }}{{ item.actualStatus
          }}{{ t("rule.separator")
          }}{{ t(item.passed ? "rule.passed" : "rule.failed")
          }}<span v-if="item.changed !== null"
            >{{ t("rule.separator")
            }}{{ t(item.changed ? "rule.changed" : "rule.unchanged") }}</span
          >
        </li>
      </ul>
      <JsonField :model-value="report" :label="t('rule.details')" readonly />
    </section>
    <EditorDialog
      v-model="open"
      :title="t(selected ? 'rule.edit' : 'rule.newCase')"
      :busy="busy" :submit-disabled="uncertain"
      :dirty="initial !== printExact(draft)"
      wide
      @save="save"
      ><ErrorNotice :message="error" /><RuleOperationStatus :task="task" @resolved="recovered" />
      <fieldset :disabled="busy || uncertain"><form ref="form" class="grid gap-3 sm:grid-cols-2" @submit.prevent="save">
        <label class="grid gap-1 text-sm"
          >{{ t("rule.caseName")
          }}<input
            v-model="draft.name"
            required
            maxlength="128"
            class="h-9 rounded-md border bg-card px-2" /></label
        ><label class="grid gap-1 text-sm"
          >{{ t("rule.expectedStatus")
          }}<select
            v-model="draft.expectedStatus"
            class="h-9 rounded-md border bg-card px-2"
            @change="draft.expectedOutput = null"
          >
            <option
              v-for="status in [
                'SUCCESS',
                'NOT_MATCHED',
                'INVALID_INPUT',
                'ERROR',
                'TIMEOUT',
              ]"
              :key="status"
            >
              {{ status }}
            </option>
          </select></label
        ><JsonField v-model="draft.input" :label="t('rule.input')" /><JsonField
          v-model="draft.params"
          :label="t('rule.params')"
        /><JsonField
          v-if="['SUCCESS', 'NOT_MATCHED'].includes(draft.expectedStatus)"
          v-model="draft.expectedOutput"
          :label="t('rule.expectedOutput')"
        /><label class="grid gap-1 text-sm"
          >{{ t("rule.expectedProblemCode")
          }}<input
            v-model="draft.expectedProblemCode"
            class="h-9 rounded-md border bg-card px-2" /></label
        ><label class="grid gap-1 text-sm"
          >{{ t("rule.evaluatedAt")
          }}<input
            v-model="draft.evaluatedAt"
            required
            class="h-9 rounded-md border bg-card px-2" /></label
        ><label class="flex items-center gap-2 text-sm"
          ><input v-model="draft.enabled" type="checkbox" />{{
            t("rule.enabled")
          }}</label
        >
      </form></fieldset></EditorDialog
    >
  </details>
</template>
