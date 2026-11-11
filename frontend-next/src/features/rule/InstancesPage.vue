<script setup lang="ts">
import PermissionButton from '@/components/business/PermissionButton.vue'

import TableWorkspace from "@/components/business/TableWorkspace.vue";
import { computed, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { Button } from "@/components/ui/button";
import PageHeader from "@/components/business/PageHeader.vue";
import ErrorNotice from "@/components/business/ErrorNotice.vue";
import DataTable from "@/components/business/DataTable.vue";
import InstanceDetails from "./components/InstanceDetails.vue";
import InstanceEditor from "./components/InstanceEditor.vue";
import { ruleGet } from "./api";
import { useRuleTask } from "./useRuleTask";
import type { Asset, Instance, Version, RuleDocument } from "./types";
const { t } = useI18n(),
  task = useRuleTask(),
  { busy, error } = task,
  rows = ref<Asset<Instance>[]>([]),
  page = ref(1),
  selected = ref<Asset<Instance>>(),
  version = ref<Version<RuleDocument>>(),
  open = ref(false),
  inspected = ref<Asset<Instance>>();
const columns = computed(() => [
  { key: "name", title: t("rule.name") },
  { key: "code", title: t("rule.code") },
  { key: "state", title: t("rule.state") },
  { key: "activeVersionId", title: t("rule.activeVersion") },
  { key: "actions", title: t("rule.actions") },
]);
async function load() {
  await task.run(
    () =>
      ruleGet<Asset<Instance>[]>("/instances", { page: page.value, size: 20 }),
    (value) => (rows.value = value),
  );
}
async function edit(row: Asset<Instance>) {
  await task.run(
    () => ruleGet<Version<RuleDocument>>("/instances/" + row.id + "/template"),
    (value) => {
      selected.value = row;
      version.value = value;
      open.value = true;
    },
  );
}
async function refreshDetails() {
  if (!inspected.value) return;
  await task.run(
    () => ruleGet<Asset<Instance>>("/instances/" + inspected.value!.id),
    (value) => {
      inspected.value = value;
    },
  );
  await load();
}
watch(page, load);
void load();
</script>
<template>
  <TableWorkspace
    ><template #tools
      ><PageHeader
        :title="t('rule.instances')"
        :description="t('rule.noAutoPublish')"
        ><Button variant="outline" :disabled="busy" @click="load">{{
          t("rule.refresh")
        }}</Button></PageHeader
      ><ErrorNotice :message="error" /></template
    ><DataTable :rows="rows" :columns="columns" :loading="busy" :page="page"
      ><template #state="{ row }">{{ t("rule.states." + row.state) }}</template
      ><template #actions="{ row }"
        ><Button size="sm" variant="ghost" @click="inspected = row">{{
          t("rule.details")
        }}</Button
        ><PermissionButton
          :permission="'rule:instance:edit'"
          size="sm"
          variant="ghost"
          @click="edit(row)"
          >{{ t("rule.edit") }}</PermissionButton
        ></template
      ></DataTable
    ><template #footer
      ><div class="mt-3 flex justify-end gap-2">
        <Button
          variant="outline"
          :disabled="page <= 1 || busy"
          @click="page--"
          >{{ t("rule.previous") }}</Button
        ><span class="p-2 text-sm">{{ t("rule.page", { page }) }}</span
        ><Button
          variant="outline"
          :disabled="rows.length < 20 || busy"
          @click="page++"
          >{{ t("rule.nextPage") }}</Button
        >
      </div></template
    ></TableWorkspace
  ><InstanceDetails
    v-if="inspected"
    :key="inspected.id + inspected.revision"
    :asset="inspected"
    @changed="refreshDetails"
  /><InstanceEditor
    v-if="open && selected && version"
    :key="selected.id"
    v-model="open"
    :asset="selected"
    :version="version"
    @saved="load"
  />
</template>
