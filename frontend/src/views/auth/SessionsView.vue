<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessageBox } from 'element-plus'
import { get, post, type PageResult } from '@/api/http'
import { usePreferences } from '@/stores/preferences'
import { formatInstant } from '@/lib/time'
interface Session {
  id: string
  username: string
  name: string
  ip: string
  userAgent: string
  lastSeenAt: string
  expiresAt: string
  locked: boolean
  current: boolean
}
const { t, locale } = useI18n()
const preferences = usePreferences()
const rows = ref<Session[]>([])
const keyword = ref('')
const page = ref(1)
const total = ref(0)
const busy = ref(false)
const error = ref('')
async function load() {
  busy.value = true
  error.value = ''
  try {
    const result = await get<PageResult<Session>>('/auth/sessions', {
      page: page.value,
      size: 20,
      keyword: keyword.value,
    })
    rows.value = result.items
    total.value = Number(result.total)
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}
async function revoke(row: Session) {
  try {
    await ElMessageBox.confirm(t('auth.forceLogoutConfirm'), t('common.confirm'), {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await post('/auth/sessions/' + row.id + '/revoke')
    await load()
  } catch (e) {
    error.value = (e as Error).message
  }
}
watch(locale, load, { immediate: true })
function search() {
  page.value = 1
  void load()
}
</script>
<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-input
        v-model="keyword"
        :placeholder="t('auth.username')"
        style="width: 260px"
        clearable
        @keyup.enter="search"
      /><el-button @click="search">{{ t('common.search') }}</el-button
      ><el-button @click="load">{{ t('common.refresh') }}</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table v-loading="busy" :data="rows" :empty-text="t('common.empty')" stripe>
      <el-table-column prop="username" :label="t('auth.username')" min-width="150" />
      <el-table-column prop="name" :label="t('common.name')" min-width="120" />
      <el-table-column prop="ip" :label="t('auth.ip')" min-width="130" />
      <el-table-column
        prop="userAgent"
        :label="t('auth.device')"
        min-width="200"
        show-overflow-tooltip
      />
      <el-table-column :label="t('auth.lastSeen')" min-width="190"
        ><template #default="{ row }">{{
          formatInstant(row.lastSeenAt, preferences.timeZone, locale)
        }}</template></el-table-column
      >
      <el-table-column :label="t('common.status')" min-width="120"
        ><template #default="{ row }"
          ><el-tag :type="row.locked ? 'warning' : 'success'">{{
            t(row.locked ? 'auth.locked' : 'auth.online')
          }}</el-tag></template
        ></el-table-column
      >
      <el-table-column :label="t('common.actions')" width="150"
        ><template #default="{ row }"
          ><el-tag v-if="row.current" type="info">{{ t('auth.currentSession') }}</el-tag
          ><el-button
            v-else
            v-permission="'auth:session:revoke'"
            link
            type="danger"
            @click="revoke(row)"
            >{{ t('auth.forceLogout') }}</el-button
          ></template
        ></el-table-column
      >
    </el-table>
    <el-pagination
      v-model:current-page="page"
      :total="total"
      :page-size="20"
      layout="total, prev, pager, next"
      style="margin-top: 20px"
      @current-change="load"
    />
  </el-card>
</template>
