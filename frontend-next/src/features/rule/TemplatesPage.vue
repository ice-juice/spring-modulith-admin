<script setup lang="ts">
import PermissionButton from '@/components/business/PermissionButton.vue'

import TableWorkspace from "@/components/business/TableWorkspace.vue";
import { computed, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { Button } from "@/components/ui/button";
import PageHeader from "@/components/business/PageHeader.vue";
import DataTable from "@/components/business/DataTable.vue";
import EditorDialog from "@/components/business/EditorDialog.vue";
import ErrorNotice from "@/components/business/ErrorNotice.vue";
import { ruleGet } from "./api";
import { useRuleTask } from "./useRuleTask";
import { matchingSamples } from "./samples";
import type { Asset, Scene, RuleDocument } from "./types";
const { t } = useI18n(),
  router = useRouter(),
  task = useRuleTask(),
  { busy, error, uncertain } = task;
const rows = ref<Asset<RuleDocument>[]>([]),
  scenes = ref<Asset<Scene>[]>([]),
  page = ref(1),
  scenePage = ref(1),
  open = ref(false),
  code = ref(""),
  name = ref(""),
  sceneId = ref(""),
  form = ref<HTMLFormElement>();
const sample = ref("");
const samples = computed(() =>
  matchingSamples(
    scenes.value.find((scene) => scene.id === sceneId.value)?.draft,
  ),
);
watch(sceneId, () => {
  sample.value = "";
});
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
      ruleGet<Asset<RuleDocument>[]>("/templates", {
        page: page.value,
        size: 20,
      }),
    (value) => {
      rows.value = value;
      uncertain.value = false;
    },
  );
}
async function newTemplate() {
  sample.value = "";
  code.value = "";
  name.value = "";
  sceneId.value = "";
  scenes.value = [];
  scenePage.value = 1;
  await moreScenes();
  open.value = true;
}
async function moreScenes() {
  await task.run(
    () =>
      ruleGet<Asset<Scene>[]>("/scenes", { page: scenePage.value, size: 100 }),
    (value) => {
      scenes.value.push(...value);
      scenePage.value++;
    },
  );
}
async function save() {
  if (!form.value?.reportValidity()) return;
  const scene = scenes.value.find((row) => row.id === sceneId.value);
  if (!scene) return;
  open.value = false;
  void router.push({
    path: "/rule/templates/new/" + scene.id,
    state: {
      ruleTemplateCode: code.value,
      ruleTemplateName: name.value,
      ruleTemplateSample: sample.value,
    },
  });
}
watch(page, load);
void load();
</script>
<template>
  <TableWorkspace
    ><template #tools
      ><PageHeader :title="t('rule.templates')" :description="t('rule.hint')"
        ><Button variant="outline" :disabled="busy" @click="load">{{
          uncertain ? t("rule.reconcile") : t("rule.refresh")
        }}</Button
        ><PermissionButton
          :permission="'rule:template:edit'"
          :disabled="busy || uncertain"
          @click="newTemplate"
          >{{ t("rule.newTemplate") }}</PermissionButton
        ></PageHeader
      ><ErrorNotice :message="error" />
      <p v-if="uncertain" role="alert" class="mb-3 text-sm text-destructive">
        {{ t("rule.uncertain") }}
      </p></template
    ><DataTable :rows="rows" :columns="columns" :loading="busy" :page="page"
      ><template #state="{ row }"
        ><span
          class="rounded-md bg-accent px-2 py-1 text-xs text-accent-foreground"
          >{{ t("rule.states." + row.state) }}</span
        ></template
      ><template #actions="{ row }"
        ><Button
          variant="ghost"
          size="sm"
          @click="router.push('/rule/templates/' + row.id)"
          >{{ t("rule.open") }}</Button
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
  ><EditorDialog
    v-model="open"
    :title="t('rule.newTemplate')"
    :busy="busy"
    :dirty="!!code || !!name"
    @save="save"
    ><ErrorNotice :message="error" />
    <form ref="form" class="grid gap-4" @submit.prevent="save">
      <label class="grid gap-2 text-sm"
        >{{ t("rule.scene")
        }}<select
          v-model="sceneId"
          required
          class="h-10 rounded-lg border bg-card px-3"
        >
          <option value="">{{ t("rule.choose") }}</option>
          <option v-for="scene in scenes" :key="scene.id" :value="scene.id">
            {{ scene.name }}
          </option>
        </select></label
      ><Button
        type="button"
        variant="ghost"
        :disabled="busy"
        @click="moreScenes"
        >{{ t("rule.loadMore") }}</Button
      ><label class="grid gap-2 text-sm"
        >{{ t("rule.sampleTemplate")
        }}<select
          v-model="sample"
          :aria-label="t('rule.sampleTemplate')"
          class="h-10 min-w-0 rounded-lg border bg-card px-3"
        >
          <option value="">{{ t("rule.blankTemplate") }}</option>
          <option v-for="id in samples" :key="id" :value="id">
            {{ t("rule.sampleTitles." + id) }}
          </option>
        </select></label
      >
      <p class="text-xs text-muted-foreground">
        {{
          sample
            ? t("rule.sampleDescriptions." + sample)
            : t("rule.sampleCompatibility")
        }}
      </p>
      <label class="grid gap-2 text-sm"
        >{{ t("rule.code")
        }}<input
          v-model="code"
          required
          pattern="[a-z][a-z0-9_-]{0,63}"
          class="h-10 rounded-lg border bg-card px-3" /></label
      ><label class="grid gap-2 text-sm"
        >{{ t("rule.name")
        }}<input
          v-model="name"
          required
          maxlength="128"
          class="h-10 rounded-lg border bg-card px-3"
      /></label></form
  ></EditorDialog>
</template>
