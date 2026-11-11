<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps<{ template?: string; name: string; title?: string; description?: string; logo?: string; background?: string; blur?: boolean; accent?: string }>()
const style = computed(() => ({ '--tenant-accent': props.accent && /^#[a-fA-F0-9]{6}$/.test(props.accent) ? props.accent : 'var(--el-color-primary)' }))
const backdrop = computed(() => ({ backgroundImage: props.background ? 'url(' + JSON.stringify(props.background) + ')' : undefined, filter: props.blur ? 'blur(8px)' : undefined }))
</script>
<template>
  <div class="tenant-entry" :class="template || 'login1'" :data-login-template="template || 'login1'" :style="style">
    <div v-if="background" class="tenant-backdrop" :style="backdrop" />
    <div class="tenant-tools"><slot name="tools" /></div>
    <header class="tenant-heading"><img v-if="logo" :src="logo" :alt="name" /><span>{{ name }}</span><h1>{{ title || name }}</h1><p v-if="description">{{ description }}</p></header>
    <main class="tenant-form"><slot /><div class="tenant-footer"><slot name="footer" /></div></main>
  </div>
</template>
<style scoped>
.tenant-entry{position:relative;isolation:isolate;min-height:100dvh;display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);align-items:center;gap:3rem;padding:6rem max(1.25rem,7vw) 3rem;background:var(--el-bg-color-page);color:var(--el-text-color-primary);overflow:clip}
.tenant-backdrop{position:absolute;inset:-12px;background-size:cover;background-position:center;opacity:.12;z-index:-1;pointer-events:none}
.tenant-tools{position:absolute;top:1rem;right:1rem;z-index:2;border-radius:8px;background:var(--el-bg-color);padding:4px}
.tenant-heading{min-width:0;max-width:34rem;justify-self:center;overflow-wrap:anywhere}
.tenant-heading img{display:block;max-width:12rem;height:3rem;object-fit:contain;margin-bottom:1rem}
.tenant-heading span{font-weight:600}.tenant-heading h1{font-size:clamp(1.8rem,3vw,3rem);line-height:1.3;white-space:pre-line}.tenant-heading p{line-height:1.8;white-space:pre-line}
.tenant-form{min-width:0;width:100%;max-width:29rem;justify-self:center;padding:2rem;background:var(--el-bg-color);border:1px solid var(--el-border-color);border-top:4px solid var(--tenant-accent);border-radius:16px}
.tenant-footer{margin-top:2rem;overflow-wrap:anywhere}.tenant-form :deep(.login-card){width:100%;padding:0;border:0}.tenant-form :deep(.el-card__body){padding:0}
.login2{grid-template-columns:minmax(0,1fr);gap:1.5rem;align-content:center}.login2 .tenant-heading{text-align:center}.login2 .tenant-heading img{margin:0 auto 1rem}.login2 .tenant-heading h1{font-size:1.8rem;margin:.75rem 0}
.login3{background:#142536}.login3 .tenant-backdrop{opacity:.4}.login3 .tenant-heading{color:#fff}.login3 .tenant-form{box-shadow:0 20px 60px #0004}
@media(max-width:760px){.tenant-entry{grid-template-columns:minmax(0,1fr);gap:1.5rem;padding-top:5rem}.tenant-heading{width:100%}.tenant-heading h1{font-size:1.8rem;margin:.75rem 0}.tenant-form{padding:1.25rem}}
</style>
