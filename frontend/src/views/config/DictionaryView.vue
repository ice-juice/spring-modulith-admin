<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { get, post } from '@/api/http'
import ManagementView from '@/views/iam/ManagementView.vue'
import EffectiveConfigView from './EffectiveConfigView.vue'
import type { Row } from '@/features/iam/resources'
const { t, locale } = useI18n()
const route = useRoute(),
  router = useRouter()
const types = ref<Row[]>([])
const selected = ref('')
const tab = ref('effective')
const current = computed(() => types.value.find((type) => type.id === selected.value))
const filters = computed(() => ({ typeId: selected.value }))
const preset = computed(() => ({ typeId: selected.value }))
async function loadTypes() {
  types.value = await get<Row[]>('/config/dictionaries/types')
  const requested = typeof route.query.typeId === 'string' ? route.query.typeId : selected.value
  selected.value = types.value.some((type) => type.id === requested)
    ? requested
    : types.value[0]?.id || ''
  if (typeof route.query.typeId === 'string' && selected.value) tab.value = 'items'
}
async function initialize() {
  await post('/config/dictionaries/initialize')
  await loadTypes()
  ElMessage.success(t('common.saved'))
}
function selectType(row: Row) {
  selected.value = row.id
  tab.value = 'items'
  void router.replace({ query: { typeId: row.id } })
}
watch(locale, loadTypes, { immediate: true })
</script>
<template>
  <div class="toolbar">
    <el-select
      v-model="selected"
      :placeholder="t('config.selectType')"
      style="width: 300px"
      filterable
      ><el-option v-for="type in types" :key="type.id" :label="String(type.name)" :value="type.id"
    /></el-select>
    <el-button v-permission="'config:dictionary:update'" @click="initialize">{{
      t('config.initialize')
    }}</el-button>
  </div>
  <el-tabs v-model="tab">
    <el-tab-pane :label="t('config.effective.tab')" name="effective"
      ><EffectiveConfigView kind="dictionaries"
    /></el-tab-pane>
    <el-tab-pane :label="t('config.dictionaryItems')" name="items"
      ><ManagementView
        v-if="selected"
        :key="selected"
        resource-id="dictionaryItems"
        :filters="filters"
        :preset="preset"
        :hierarchical="current?.tree === true" /><el-empty
        v-else
        :description="t('config.selectType')"
    /></el-tab-pane>
    <el-tab-pane :label="t('config.dictionaryTypes')" name="types"
      ><ManagementView resource-id="dictionaryTypes" @changed="loadTypes" @select="selectType"
    /></el-tab-pane>
  </el-tabs>
</template>
