<script setup lang="ts">
import PermissionButton from '@/components/business/PermissionButton.vue'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import DataTable from '@/components/business/DataTable.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import { get, post } from '@/api/http'
import { useLatestRequest } from '@/composables/useLatestRequest'
import { useSubmission } from '@/composables/useSubmission'
import OperationStatus from '@/components/business/OperationStatus.vue'
import { confirmAction } from '@/lib/feedback'
interface Work { id: string; state: string; releaseRevision: string; bundleId: string; result: string | null; failure: string | null }
interface Status { enabled: boolean; lastSync: string; lastSuccess: string | null; versions: {pluginId:string;version:string;sha256:string;inFlight:number;revoked:boolean}[] }
const {t}=useI18n(),runtimeRead=useLatestRequest(),workRead=useLatestRequest(),submission=useSubmission(),workId=ref(''),work=ref<Work>(),status=ref<Status>(),error=ref('')
submission.bind('extension.work.*')
const busy=computed(()=>runtimeRead.loading.value || workRead.loading.value || submission.busy.value || submission.querying.value)
const versions=computed(()=>status.value?.versions.map(v=>({...v,id:v.sha256}))||[])
const result=computed(()=>{if(!work.value?.result)return '';try{return new TextDecoder().decode(Uint8Array.from(atob(work.value.result),c=>c.charCodeAt(0)))}catch{return t('extension.unreadableResult')}})
async function refresh(){ await runtimeRead.run(signal=>get<Status>('/extension/runtime',undefined,{signal}),value=>{status.value=value},'runtime');error.value=runtimeRead.error.value }
async function lookup(){ const id=workId.value;if(!/^[0-9]+$/.test(id)){error.value=t('common.required');return};work.value=undefined;await workRead.run(signal=>get<Work>('/extension/work/'+id,undefined,{signal}),value=>{work.value=value},id);error.value=workRead.error.value }
function evidence(value: Work,id:string){if(!value || value.id!==id)throw new Error(t('extension.unknownResult'));return {id,state:value.state==='SUCCEEDED'?'SUCCEEDED' as const:value.state==='CANCELLED'?'FAILED_FINAL' as const:value.state==='RUNNING'?'PENDING' as const:'UNKNOWN' as const}}
async function reconcile(){
  if(!work.value || busy.value)return
  const id=work.value.id
  const result=await submission.run<Work>({operation:'extension.work-reconcile',resource:'extension.work.'+id,protocol:'NATIVE',nativeId:id},async()=>{
    if(!await confirmAction(t('extension.reconcileWorkHint'),t('extension.reconcileWork')))return
    return options=>post<Work>('/extension/work/'+id+'/reconcile',undefined,options)
  },{success:value=>{const state=evidence(value,id).state;return state==='SUCCEEDED'?'completed':state==='FAILED_FINAL'?'failed':state==='PENDING'?'pending':'unknown'}})
  if(result.kind==='success' || result.kind==='pending' && result.value){workRead.cancel();work.value=result.value!}
  error.value=submission.error.value
}
async function check(){const id=submission.record.value?.operationId;if(!id)return;const result=await submission.checkNative(()=>get<Work>('/extension/work/'+id),value=>evidence(value,id));if(result){workId.value=id;workRead.cancel();work.value=result.value};error.value=submission.error.value}
</script>
<template><section class="space-y-4"><ErrorNotice :message="error" /><OperationStatus :slow="submission.slow.value" :unresolved="submission.unresolved.value" :querying="submission.querying.value" queryable @check="check" /><div class="flex flex-wrap items-center gap-3"><PermissionButton :permission="'extension:runtime:read'" variant="outline" :disabled="busy" @click="refresh">{{ t('extension.refreshRuntime') }}</PermissionButton><span v-if="status" class="text-sm">{{ status.lastSync }}</span></div><DataTable v-if="status" :rows="versions" :columns="['pluginId','version','sha256','inFlight','revoked'].map(key=>({key,title:t('extension.fields.'+key)}))" />
<form class="space-y-3 rounded-2xl border bg-card p-5" @submit.prevent="lookup"><Label for="extension-work-id">{{ t('extension.workId') }}</Label><div class="flex gap-2"><Input id="extension-work-id" v-model="workId" inputmode="numeric" :disabled="busy" /><Button type="submit" :disabled="busy">{{ t('common.search') }}</Button></div><p class="text-xs leading-6 text-muted-foreground">{{ t('extension.workHint') }}</p>
<div v-if="work" class="space-y-3 border-t pt-3"><p>{{ work.state }} {{ work.bundleId }}</p><p v-if="work.failure" class="text-sm text-muted-foreground">{{ work.failure }}</p><pre v-if="result" class="max-h-80 overflow-auto whitespace-pre-wrap break-all rounded-lg bg-muted p-3 text-xs">{{ result }}</pre><PermissionButton :permission="'extension:work:reconcile'" v-if="['UNKNOWN','RUNNING'].includes(work.state)" type="button" variant="outline" :disabled="busy || submission.unresolved.value" @click="reconcile">{{ t('extension.reconcileWork') }}</PermissionButton></div></form></section></template>
