<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { post } from '@/api/http'
import { notify, confirmAction } from '@/lib/feedback'
import { Button } from '@/components/ui/button'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import PageHeader from '@/components/business/PageHeader.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import ResourcePage from '@/features/iam/ResourcePage.vue'
const { t } = useI18n(), tab = ref('parameters'), revision = ref(0), busy = ref(false), error = ref('')
async function refresh() { if (busy.value) return; busy.value = true; error.value = ''; try { await post('/config/parameters/refresh'); notify(t('common.saved')) } catch (cause) { error.value = (cause as Error).message } finally { busy.value = false } }
async function reset() { if (busy.value || !await confirmAction(t('config.resetConfirm'), t('common.confirm'), true)) return; busy.value = true; error.value = ''; try { await post('/config/parameters/reset-builtins'); revision.value++; notify(t('common.saved')) } catch (cause) { error.value = (cause as Error).message } finally { busy.value = false } }
</script>
<template><PageHeader :title="t('config.parameters')"><Button v-permission="'config:parameter:update'" variant="outline" :disabled="busy" @click="refresh">{{ t('config.refreshCache') }}</Button><Button v-permission="'config:parameter:update'" variant="outline" class="text-destructive" :disabled="busy" @click="reset">{{ t('config.resetDefaults') }}</Button></PageHeader><ErrorNotice :message="error" /><Tabs v-model="tab"><TabsList class="mb-5"><TabsTrigger value="parameters">{{ t('config.parameters') }}</TabsTrigger><TabsTrigger value="groups">{{ t('config.parameterGroups') }}</TabsTrigger></TabsList><TabsContent value="parameters"><ResourcePage :key="revision" resource-id="parameters" embedded /></TabsContent><TabsContent value="groups"><ResourcePage resource-id="parameterGroups" embedded /></TabsContent></Tabs></template>
