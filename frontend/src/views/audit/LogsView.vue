<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { get, type PageResult } from '@/api/http'
import { usePreferences } from '@/stores/preferences'
import { formatInstant, localDateTimeToInstant } from '@/lib/time'
const route = useRoute(),
  router = useRouter()
const { t, locale } = useI18n()
const preferences = usePreferences()
const kind = computed(() => String(route.meta.logKind))
interface Log {
  id: string
  username: string
  status?: string
  message?: string
  action?: string
  clientPath?: string
  resultCode?: number
  ip: string
  country?: string
  province?: string
  city?: string
  browser?: string
  operatingSystem?: string
  ipChanged?: boolean
  unusualLocation?: boolean
  localAddress?: boolean
  durationMs?: string
  occurredAt: string
}
const rows = ref<Log[]>([]),
  username = ref(''),
  page = ref(1),
  total = ref(0),
  busy = ref(false),
  error = ref('')
const dates = ref<[string, string]>()
const status = ref(''),
  success = ref<boolean>()
const statuses = ['SUCCESS', 'PASSWORD_FAILURE', 'CAPTCHA_FAILURE']
async function load() {
  busy.value = true
  error.value = ''
  try {
    const result = await get<PageResult<Log>>('/audit/' + kind.value, {
      page: page.value,
      size: 20,
      username: username.value,
      status: status.value || undefined,
      success: success.value,
      from: dates.value?.[0]
        ? localDateTimeToInstant(dates.value[0], preferences.timeZone)
        : undefined,
      to: dates.value?.[1]
        ? localDateTimeToInstant(dates.value[1], preferences.timeZone)
        : undefined,
    })
    rows.value = result.items
    total.value = Number(result.total)
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}
function search() {
  page.value = 1
  void load()
}
watch([kind, locale], search, { immediate: true })
</script>
<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-input
        v-model="username"
        :placeholder="t('audit.username')"
        clearable
        style="width: 180px"
        @keyup.enter="search"
      />
      <el-select
        v-if="kind === 'logins'"
        v-model="status"
        clearable
        :placeholder="t('common.status')"
        style="width: 190px"
        ><el-option
          v-for="value in statuses"
          :key="value"
          :label="t('audit.statuses.' + value)"
          :value="value"
      /></el-select>
      <el-select
        v-else
        v-model="success"
        clearable
        :placeholder="t('common.status')"
        style="width: 140px"
        ><el-option :label="t('audit.success')" :value="true" /><el-option
          :label="t('audit.failure')"
          :value="false"
      /></el-select>
      <el-date-picker
        v-model="dates"
        type="datetimerange"
        value-format="YYYY-MM-DD HH:mm:ss"
        :start-placeholder="t('audit.from')"
        :end-placeholder="t('audit.to')"
      /><small>{{ preferences.timeZone }}</small>
      <el-button @click="search">{{ t('common.search') }}</el-button
      ><el-button @click="load">{{ t('common.refresh') }}</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table v-loading="busy" :data="rows" :empty-text="t('common.empty')" stripe>
      <el-table-column prop="username" :label="t('audit.username')" min-width="130" />
      <el-table-column
        v-if="kind === 'operations'"
        prop="message"
        :label="t('audit.operation')"
        min-width="250"
        show-overflow-tooltip
      />
      <el-table-column :label="t('common.status')" min-width="150"
        ><template #default="{ row }"
          ><el-tag
            :type="
              kind === 'logins'
                ? row.status === 'SUCCESS'
                  ? 'success'
                  : 'danger'
                : row.resultCode === 0
                  ? 'success'
                  : 'danger'
            "
            >{{
              kind === 'logins'
                ? t('audit.statuses.' + row.status)
                : t(row.resultCode === 0 ? 'audit.success' : 'audit.failure')
            }}</el-tag
          ></template
        ></el-table-column
      >
      <el-table-column prop="ip" :label="t('audit.ip')" min-width="130" />
      <el-table-column v-if="kind === 'logins'" :label="t('audit.location')" min-width="220"
        ><template #default="{ row }"
          >{{
            row.localAddress
              ? t('audit.localAddress')
              : [row.country, row.province, row.city].filter(Boolean).join(' / ') ||
                t('audit.unknown')
          }}<el-tag v-if="row.unusualLocation" type="warning" size="small">{{
            t('audit.unusual')
          }}</el-tag></template
        ></el-table-column
      >
      <el-table-column v-if="kind === 'logins'" :label="t('audit.device')" min-width="170"
        ><template #default="{ row }">{{
          [row.browser, row.operatingSystem].join(' / ')
        }}</template></el-table-column
      >
      <el-table-column v-if="kind === 'operations'" :label="t('audit.duration')" width="120"
        ><template #default="{ row }">{{ row.durationMs }}</template></el-table-column
      >
      <el-table-column :label="t('audit.occurredAt')" min-width="190"
        ><template #default="{ row }">{{
          formatInstant(row.occurredAt, preferences.timeZone, locale)
        }}</template></el-table-column
      >
      <el-table-column v-if="kind === 'operations'" :label="t('common.actions')" width="140"
        ><template #default="{ row }"
          ><el-button
            v-if="row.clientPath"
            link
            type="primary"
            @click="router.push(row.clientPath)"
            >{{ t('audit.viewObject') }}</el-button
          ></template
        ></el-table-column
      >
    </el-table>
    <el-pagination
      v-model:current-page="page"
      :page-size="20"
      :total="total"
      layout="total, prev, pager, next"
      style="margin-top: 20px"
      @current-change="load"
    />
  </el-card>
</template>
