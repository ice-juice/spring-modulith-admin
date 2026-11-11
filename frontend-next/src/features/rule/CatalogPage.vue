<script setup lang="ts">
import PermissionButton from '@/components/business/PermissionButton.vue'

import { ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { Button } from "@/components/ui/button";
import PageHeader from "@/components/business/PageHeader.vue";
import ErrorNotice from "@/components/business/ErrorNotice.vue";
import EmptyState from "@/components/business/EmptyState.vue";
import InstanceEditor from "./components/InstanceEditor.vue";
import { ruleGet } from "./api";
import { useRuleTask } from "./useRuleTask";
import type { Asset, Scene, Version, RuleDocument } from "./types";
const { t } = useI18n(),
  task = useRuleTask(),
  { busy, error } = task,
  scenes = ref<Asset<Scene>[]>([]),
  sceneId = ref(""),
  page = ref(1),
  versions = ref<(Version<RuleDocument> & { name: string; code: string })[]>(
    [],
  ),
  selected = ref<Version<RuleDocument>>(),
  open = ref(false);
async function loadScenes() {
  await task.run(
    () =>
      ruleGet<Asset<Scene>[]>("/catalog/scenes", {
        page: page.value,
        size: 100,
      }),
    (value) => scenes.value.push(...value),
  );
}
async function load() {
  versions.value = [];
  if (sceneId.value)
    await task.run(
      () =>
        ruleGet<
          { name: string; code: string; version: Version<RuleDocument> }[]
        >("/catalog/entries", { sceneId: sceneId.value }),
      (value) =>
        (versions.value = value.map((entry) => ({
          ...entry.version,
          name: entry.name,
          code: entry.code,
        }))),
    );
}
watch(sceneId, load);
void loadScenes();
</script>
<template>
  <PageHeader :title="t('rule.catalog')" :description="t('rule.hint')"
    ><Button variant="outline" :disabled="busy" @click="load">{{
      t("rule.refresh")
    }}</Button></PageHeader
  ><ErrorNotice :message="error" />
  <div
    class="mb-5 flex flex-wrap items-end gap-3 rounded-xl border bg-card p-4"
  >
    <label class="grid min-w-48 gap-2 text-sm"
      >{{ t("rule.scene")
      }}<select
        v-model="sceneId"
        :aria-label="t('rule.scene')"
        :disabled="busy"
        class="h-10 rounded-lg border bg-card px-3"
      >
        <option value="">{{ t("rule.choose") }}</option>
        <option v-for="scene in scenes" :key="scene.id" :value="scene.id">
          {{ scene.name }}
        </option>
      </select></label
    ><Button
      variant="ghost"
      :disabled="busy"
      @click="
        page++;
        loadScenes();
      "
      >{{ t("rule.loadMore") }}</Button
    >
  </div>
  <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
    <article
      v-for="version in versions"
      :key="version.id"
      class="flex flex-col gap-4 rounded-2xl border bg-card p-5 shadow-[var(--shadow-surface)]"
    >
      <div class="flex items-center justify-between">
        <span
          class="rounded-md bg-accent px-2 py-1 text-xs text-accent-foreground"
          >{{ t("rule.kinds." + version.document.definition.kind) }}</span
        ><span class="text-xs text-muted-foreground"
          >{{ t("rule.version") }}{{ t("rule.separator")
          }}{{ version.revision }}</span
        >
      </div>
      <h2 class="text-base font-semibold">{{ version.name }}</h2>
      <p class="break-all font-mono text-xs text-muted-foreground">
        {{ version.id }}
      </p>
      <dl class="space-y-2 text-sm">
        <div v-for="(ui, key) in version.document.parameterUi" :key="key">
          <dt class="font-medium">{{ ui.label }}</dt>
          <dd class="text-xs text-muted-foreground">{{ ui.help }}</dd>
        </div>
      </dl>
      <PermissionButton
        :permission="'rule:instance:edit'"
        class="mt-auto"
        @click="
          selected = version;
          open = true;
        "
        >{{ t("rule.selectTemplate") }}</PermissionButton
      >
    </article>
  </div>
  <EmptyState
    v-if="!versions.length && !busy"
    :description="t('rule.noTemplate')"
  /><InstanceEditor
    v-if="open && selected"
    :key="selected.id"
    v-model="open"
    :version="selected"
  />
</template>
