<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { post } from '@/api/http'
import ManagementView from '@/views/iam/ManagementView.vue'
import EffectiveConfigView from './EffectiveConfigView.vue'
const { t } = useI18n()
const tab = ref('effective'),
  revision = ref(0)
async function refresh() {
  await post('/config/parameters/refresh')
  ElMessage.success(t('common.saved'))
}
async function reset() {
  try {
    await ElMessageBox.confirm(t('config.resetConfirm'), t('common.confirm'), { type: 'warning' })
  } catch {
    return
  }
  await post('/config/parameters/reset-builtins')
  revision.value++
  ElMessage.success(t('common.saved'))
}
</script>
<template>
  <div class="toolbar">
    <el-button v-permission="'config:parameter:update'" @click="refresh">{{
      t('config.refreshCache')
    }}</el-button
    ><el-button v-permission="'config:parameter:update'" type="warning" plain @click="reset">{{
      t('config.resetDefaults')
    }}</el-button>
  </div>
  <el-tabs v-model="tab"
    ><el-tab-pane :label="t('config.effective.tab')" name="effective"
      ><EffectiveConfigView :key="revision" kind="parameters" /></el-tab-pane
    ><el-tab-pane :label="t('config.effective.privateTab')" name="parameters"
      ><ManagementView :key="revision" resource-id="parameters" /></el-tab-pane
    ><el-tab-pane :label="t('config.parameterGroups')" name="groups"
      ><ManagementView resource-id="parameterGroups" /></el-tab-pane
  ></el-tabs>
</template>
